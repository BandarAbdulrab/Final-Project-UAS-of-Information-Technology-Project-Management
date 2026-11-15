package com.byteall.kharismart.data.repository

import androidx.room.withTransaction
import com.byteall.kharismart.data.local.DebtStatus
import com.byteall.kharismart.data.local.KharismartDatabase
import com.byteall.kharismart.data.local.MovementType
import com.byteall.kharismart.data.local.PaymentMethod
import com.byteall.kharismart.data.local.dao.DebtDao
import com.byteall.kharismart.data.local.dao.ProductDao
import com.byteall.kharismart.data.local.dao.SaleDao
import com.byteall.kharismart.data.local.dao.SettingsDao
import com.byteall.kharismart.data.local.entity.DebtEntity
import com.byteall.kharismart.data.local.entity.ProductEntity
import com.byteall.kharismart.data.local.entity.SettingsEntity
import com.byteall.kharismart.data.local.entity.StockMovementEntity
import com.byteall.kharismart.data.local.entity.TransactionEntity
import com.byteall.kharismart.data.local.entity.TransactionItemEntity
import com.byteall.kharismart.data.local.model.TransactionItemWithName
import com.byteall.kharismart.data.local.model.TransactionWithCashier
import com.byteall.kharismart.util.DomainException
import com.byteall.kharismart.util.time.WibClock
import com.byteall.kharismart.util.time.WibClock.DateRange
import kotlinx.coroutines.flow.Flow

data class ReceiptData(
    val transaction: TransactionEntity,
    val cashierName: String,
    val items: List<TransactionItemWithName>,
    val debt: DebtEntity?,
    val settings: SettingsEntity
)

data class CartLine(
    val productId: Long,
    val quantity: Int
)

class SaleRepository(
    private val database: KharismartDatabase,
    private val saleDao: SaleDao,
    private val productDao: ProductDao,
    private val debtDao: DebtDao,
    private val settingsDao: SettingsDao
) {

    fun observeTransactions(range: DateRange): Flow<List<TransactionWithCashier>> {
        return saleDao.observeTransactionsBetween(range.start, range.endExclusive)
    }

    suspend fun getReceiptData(transactionId: Long): ReceiptData? {
        val trxWithCashier = saleDao.getTransactionWithCashier(transactionId) ?: return null
        val items = saleDao.getItemsWithNames(transactionId)
        val debt = debtDao.getDebtByTransactionId(transactionId)
        val settings = settingsDao.getSettings() ?: SettingsEntity(
            settingsId = 1,
            storeName = "Toko Kharisma",
            receiptFooter = "Thank you"
        )

        return ReceiptData(
            transaction = trxWithCashier.transaction,
            cashierName = trxWithCashier.cashierName,
            items = items,
            debt = debt,
            settings = settings
        )
    }

    suspend fun getReceiptDataByReceiptNo(receiptNo: String): ReceiptData? {
        val trxWithCashier = saleDao.getByReceiptNo(receiptNo) ?: return null
        val items = saleDao.getItemsWithNames(trxWithCashier.transaction.transactionId)
        val debt = debtDao.getDebtByTransactionId(trxWithCashier.transaction.transactionId)
        val settings = settingsDao.getSettings() ?: SettingsEntity(
            settingsId = 1,
            storeName = "Toko Kharisma",
            receiptFooter = "Thank you"
        )

        return ReceiptData(
            transaction = trxWithCashier.transaction,
            cashierName = trxWithCashier.cashierName,
            items = items,
            debt = debt,
            settings = settings
        )
    }

    suspend fun nextReceiptNo(date: String = WibClock.receiptDatePart()): String {
        val prefix = "TRX-$date-"
        val maxSeq = saleDao.getMaxSequenceForPrefix(prefix) ?: 0
        val nextSeq = maxSeq + 1
        return if (nextSeq < 1000) {
            String.format("%s%03d", prefix, nextSeq)
        } else {
            "$prefix$nextSeq"
        }
    }

    /**
     * Executes the atomic 3-way checkout transaction (SRS-F07, F08, BR-03, BR-04, BR-04b, BR-05, BR-08, BR-10, BR-14).
     *
     * In one database transaction:
     * 1. Re-verifies inventory stock for all cart items from database records (never trusts UI prices).
     * 2. Atomically decrements stock via [ProductDao.decrementStockIfAvailable] (fails if stock < quantity).
     * 3. Calculates total, total cost, and gross profit.
     * 4. Validates payment amount for Cash, QRIS, and Credit.
     * 5. Generates the sequential daily receipt number TRX-yyyyMMdd-XXX.
     * 6. Writes row to `transactions`.
     * 7. Writes line items to `transaction_items`.
     * 8. Records inventory decrement audit rows in `stock_movements`.
     * 9. If Credit: writes linked record in `debts` table with status 'unpaid'.
     *
     * If any step fails or violates a business rule, the entire transaction is rolled back.
     */
    suspend fun completeCheckout(
        lines: List<CartLine>,
        method: String,
        paidAmount: Long,
        customerName: String?,
        note: String?,
        userId: Long
    ): Result<TransactionEntity> {
        if (lines.isEmpty()) {
            return Result.failure(DomainException("Keranjang belanja kosong"))
        }

        if (method != PaymentMethod.CASH && method != PaymentMethod.QRIS && method != PaymentMethod.CREDIT) {
            return Result.failure(DomainException("Metode pembayaran '$method' tidak valid"))
        }

        val trimmedCustomerName = customerName?.trim()
        if (method == PaymentMethod.CREDIT && trimmedCustomerName.isNullOrBlank()) {
            return Result.failure(DomainException("Nama pelanggan wajib diisi untuk transaksi utang/kredit"))
        }

        return try {
            val transaction = database.withTransaction {
                // Consolidate lines in case the same productId appears multiple times in cart
                val consolidatedLines = lines
                    .groupBy { it.productId }
                    .map { (id, group) -> CartLine(productId = id, quantity = group.sumOf { it.quantity }) }

                // 1. Re-verify inventory stock for all cart items and decrement atomically (BR-01, BR-08)
                val linesWithProduct = mutableListOf<Pair<CartLine, ProductEntity>>()
                for (line in consolidatedLines) {
                    if (line.quantity <= 0) {
                        throw DomainException("Jumlah barang harus lebih dari 0")
                    }

                    val product = productDao.getById(line.productId)
                        ?: throw DomainException("Produk dengan ID ${line.productId} tidak ditemukan")

                    val rowsUpdated = productDao.decrementStockIfAvailable(line.productId, line.quantity)
                    if (rowsUpdated == 0) {
                        throw DomainException("Stok '${product.name}' tidak mencukupi untuk checkout (tersedia: ${product.stock}, diminta: ${line.quantity})")
                    }

                    linesWithProduct.add(line to product)
                }

                // 2. Compute totals based on official database product records (BR-14)
                val total = linesWithProduct.sumOf { (line, product) ->
                    product.price * line.quantity.toLong()
                }
                val totalCost = linesWithProduct.sumOf { (line, product) ->
                    product.costPrice * line.quantity.toLong()
                }
                val grossProfit = total - totalCost

                // 3. Validate payment amounts according to method (BR-03, BR-04, BR-05)
                val actualPaid: Long
                val changeDue: Long
                when (method) {
                    PaymentMethod.CASH -> {
                        if (paidAmount < total) {
                            throw DomainException("Nominal pembayaran tunai kurang dari total belanja")
                        }
                        actualPaid = paidAmount
                        changeDue = paidAmount - total
                    }
                    PaymentMethod.QRIS -> {
                        actualPaid = total
                        changeDue = 0L
                    }
                    PaymentMethod.CREDIT -> {
                        actualPaid = 0L
                        changeDue = 0L
                    }
                    else -> throw DomainException("Metode pembayaran tidak valid")
                }

                // 4. Generate sequential receipt number inside the same transaction
                val receiptNo = nextReceiptNo()
                val now = WibClock.nowString()

                // 5. Insert Transaction row
                val trxEntity = TransactionEntity(
                    userId = userId,
                    receiptNo = receiptNo,
                    date = now,
                    method = method,
                    total = total,
                    paid = actualPaid,
                    changeDue = changeDue,
                    totalCost = totalCost,
                    grossProfit = grossProfit,
                    note = note?.trim()?.ifBlank { null }
                )
                val transactionId = saleDao.insertTransaction(trxEntity)

                // 6. Insert Transaction items
                val itemEntities = linesWithProduct.map { (line, product) ->
                    TransactionItemEntity(
                        transactionId = transactionId,
                        productId = line.productId,
                        quantity = line.quantity,
                        price = product.price,
                        costPrice = product.costPrice,
                        subtotal = product.price * line.quantity.toLong()
                    )
                }
                saleDao.insertItems(itemEntities)

                // 7. Insert Stock Movement rows (BR-08)
                for ((line, product) in linesWithProduct) {
                    val movement = StockMovementEntity(
                        productId = line.productId,
                        userId = userId,
                        transactionId = transactionId,
                        date = now,
                        type = MovementType.SALE,
                        quantityChange = -line.quantity,
                        stockAfter = product.stock - line.quantity,
                        unitCost = product.costPrice,
                        totalCost = product.costPrice * line.quantity.toLong(),
                        note = "Penjualan: $receiptNo"
                    )
                    productDao.insertStockMovement(movement)
                }

                // 8. If Credit: insert linked Debt row (BR-05, BR-13)
                if (method == PaymentMethod.CREDIT) {
                    val debt = DebtEntity(
                        transactionId = transactionId,
                        userId = userId,
                        customerName = trimmedCustomerName!!,
                        date = now,
                        totalAmount = total,
                        paidAmount = 0L,
                        status = DebtStatus.UNPAID,
                        note = note?.trim()?.ifBlank { null }
                    )
                    debtDao.insertDebt(debt)
                }

                trxEntity.copy(transactionId = transactionId)
            }
            Result.success(transaction)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
