package com.byteall.kharismart.data.repository

import com.byteall.kharismart.data.local.KharismartDatabase
import com.byteall.kharismart.data.local.dao.DebtDao
import com.byteall.kharismart.data.local.dao.ProductDao
import com.byteall.kharismart.data.local.dao.SaleDao
import com.byteall.kharismart.data.local.dao.SettingsDao
import com.byteall.kharismart.data.local.entity.DebtEntity
import com.byteall.kharismart.data.local.entity.SettingsEntity
import com.byteall.kharismart.data.local.entity.TransactionEntity
import com.byteall.kharismart.data.local.model.TransactionItemWithName
import com.byteall.kharismart.data.local.model.TransactionWithCashier
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
}