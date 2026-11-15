package com.byteall.kharismart.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.byteall.kharismart.data.local.DebtStatus
import com.byteall.kharismart.data.local.DemoSeed
import com.byteall.kharismart.data.local.KharismartDatabase
import com.byteall.kharismart.data.local.MovementType
import com.byteall.kharismart.data.local.PaymentMethod
import com.byteall.kharismart.data.local.entity.ProductEntity
import com.byteall.kharismart.data.local.entity.TransactionEntity
import com.byteall.kharismart.util.DomainException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaleRepositoryTest {

    private lateinit var db: KharismartDatabase
    private lateinit var saleRepository: SaleRepository
    private var adminUserId: Long = 0L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = KharismartDatabase.buildInMemory(context)
        db.openHelper.writableDatabase

        saleRepository = SaleRepository(
            database = db,
            saleDao = db.saleDao(),
            productDao = db.productDao(),
            debtDao = db.debtDao(),
            settingsDao = db.settingsDao()
        )

        runBlocking {
            adminUserId = db.userDao().getByUsername(DemoSeed.ADMIN_USERNAME)!!.userId
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testReceiptSequenceFormatting() {
        runBlocking {
            val datePart = "20260301"

            // 1. Initial receipt on empty database
            assertEquals("TRX-20260301-001", saleRepository.nextReceiptNo(datePart))

            // 2. Insert up to 9 receipts
            for (i in 1..9) {
                db.saleDao().insertTransaction(
                    TransactionEntity(
                        userId = adminUserId,
                        receiptNo = "TRX-$datePart-${String.format("%03d", i)}",
                        date = "2026-03-01 10:00:00",
                        method = "cash",
                        total = 10000,
                        paid = 10000,
                        changeDue = 0,
                        totalCost = 5000,
                        grossProfit = 5000
                    )
                )
            }
            assertEquals("TRX-20260301-010", saleRepository.nextReceiptNo(datePart))

            // 3. Past 999 receipts, expands naturally without string sorting breakage
            db.saleDao().insertTransaction(
                TransactionEntity(
                    userId = adminUserId,
                    receiptNo = "TRX-$datePart-999",
                    date = "2026-03-01 22:00:00",
                    method = "cash",
                    total = 10000,
                    paid = 10000,
                    changeDue = 0,
                    totalCost = 5000,
                    grossProfit = 5000
                )
            )
            assertEquals("TRX-20260301-1000", saleRepository.nextReceiptNo(datePart))
        }
    }

    @Test
    fun testCompleteCheckout_cashWithChange() = runBlocking {
        val prodId = db.productDao().insert(
            ProductEntity(
                name = "Gula Pasir 1kg",
                categoryId = 1L,
                price = 15000L,
                costPrice = 12000L,
                stock = 10,
                status = 1
            )
        )

        val result = saleRepository.completeCheckout(
            lines = listOf(CartLine(productId = prodId, quantity = 2)),
            method = PaymentMethod.CASH,
            paidAmount = 50000L,
            customerName = null,
            note = "Kembalian Rp20.000",
            userId = adminUserId
        )

        assertTrue("Checkout cash harus berhasil", result.isSuccess)
        val trx = result.getOrThrow()
        assertEquals(30000L, trx.total)
        assertEquals(50000L, trx.paid)
        assertEquals(20000L, trx.changeDue)
        assertEquals(24000L, trx.totalCost)
        assertEquals(6000L, trx.grossProfit)
        assertTrue(trx.receiptNo.startsWith("TRX-"))

        // Stock must be decremented: 10 - 2 = 8
        val updatedProduct = db.productDao().getById(prodId)!!
        assertEquals(8, updatedProduct.stock)

        // Item row exists
        val items = db.saleDao().getItemsWithNames(trx.transactionId)
        assertEquals(1, items.size)
        assertEquals(2, items[0].item.quantity)
        assertEquals(15000L, items[0].item.price)
        assertEquals(30000L, items[0].item.subtotal)

        // Stock movement recorded
        val movements = db.productDao().observeMovementsForProduct(prodId).first()
        assertEquals(1, movements.size)
        assertEquals(MovementType.SALE, movements[0].type)
        assertEquals(-2, movements[0].quantityChange)
        assertEquals(8, movements[0].stockAfter)
    }

    @Test
    fun testCompleteCheckout_cashInsufficientAmount_failsAndRollsBack() = runBlocking {
        val prodId = db.productDao().insert(
            ProductEntity(
                name = "Beras 5kg",
                categoryId = 1L,
                price = 70000L,
                costPrice = 60000L,
                stock = 5,
                status = 1
            )
        )

        val result = saleRepository.completeCheckout(
            lines = listOf(CartLine(productId = prodId, quantity = 1)),
            method = PaymentMethod.CASH,
            paidAmount = 50000L, // Kurang 20rb
            customerName = null,
            note = null,
            userId = adminUserId
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DomainException)

        // Stock must be intact (5)
        val productAfter = db.productDao().getById(prodId)!!
        assertEquals(5, productAfter.stock)

        // Zero movements
        val movements = db.productDao().observeMovementsForProduct(prodId).first()
        assertTrue(movements.isEmpty())
    }

    @Test
    fun testCompleteCheckout_qrisSuccess() = runBlocking {
        val prodId = db.productDao().insert(
            ProductEntity(
                name = "Minyak Goreng 2L",
                categoryId = 1L,
                price = 34000L,
                costPrice = 29000L,
                stock = 8,
                status = 1
            )
        )

        val result = saleRepository.completeCheckout(
            lines = listOf(CartLine(productId = prodId, quantity = 1)),
            method = PaymentMethod.QRIS,
            paidAmount = 34000L,
            customerName = null,
            note = "QRIS BCA",
            userId = adminUserId
        )

        assertTrue(result.isSuccess)
        val trx = result.getOrThrow()
        assertEquals(PaymentMethod.QRIS, trx.method)
        assertEquals(34000L, trx.total)
        assertEquals(34000L, trx.paid)
        assertEquals(0L, trx.changeDue)

        // Stock decremented
        assertEquals(7, db.productDao().getById(prodId)!!.stock)
    }

    @Test
    fun testCompleteCheckout_creditCreatesLinkedDebtRow() = runBlocking {
        val prodId = db.productDao().insert(
            ProductEntity(
                name = "Kopi Bubuk",
                categoryId = 1L,
                price = 10000L,
                costPrice = 8000L,
                stock = 15,
                status = 1
            )
        )

        val result = saleRepository.completeCheckout(
            lines = listOf(CartLine(productId = prodId, quantity = 2)),
            method = PaymentMethod.CREDIT,
            paidAmount = 0L,
            customerName = "Pak RT Bambang",
            note = "Jatuh tempo akhir bulan",
            userId = adminUserId
        )

        assertTrue(result.isSuccess)
        val trx = result.getOrThrow()
        assertEquals(PaymentMethod.CREDIT, trx.method)
        assertEquals(20000L, trx.total)
        assertEquals(0L, trx.paid)
        assertEquals(0L, trx.changeDue)

        // Verify linked debt row
        val debt = db.debtDao().getDebtByTransactionId(trx.transactionId)
        assertNotNull("Debt record must be created for credit transaction", debt)
        assertEquals("Pak RT Bambang", debt!!.customerName)
        assertEquals(20000L, debt.totalAmount)
        assertEquals(0L, debt.paidAmount)
        assertEquals(DebtStatus.UNPAID, debt.status)
        assertEquals(trx.transactionId, debt.transactionId)

        // Stock decremented: 15 - 2 = 13
        assertEquals(13, db.productDao().getById(prodId)!!.stock)
    }

    @Test
    fun testCompleteCheckout_creditBlankCustomer_failsAndRollsBack() = runBlocking {
        val prodId = db.productDao().insert(
            ProductEntity(
                name = "Sabun Mandi",
                categoryId = 1L,
                price = 4000L,
                costPrice = 3000L,
                stock = 10,
                status = 1
            )
        )

        val result = saleRepository.completeCheckout(
            lines = listOf(CartLine(productId = prodId, quantity = 2)),
            method = PaymentMethod.CREDIT,
            paidAmount = 0L,
            customerName = "   ", // Blank name
            note = null,
            userId = adminUserId
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DomainException)

        // Stock intact
        assertEquals(10, db.productDao().getById(prodId)!!.stock)
    }

    @Test
    fun testCompleteCheckout_insufficientStock_rollsBackTransaction() = runBlocking {
        val prodA = db.productDao().insert(
            ProductEntity(
                name = "Teh Celup",
                categoryId = 1L,
                price = 6000L,
                costPrice = 4500L,
                stock = 2, // only 2 in stock
                status = 1
            )
        )

        val prodB = db.productDao().insert(
            ProductEntity(
                name = "Biskuit Roma",
                categoryId = 1L,
                price = 8000L,
                costPrice = 6500L,
                stock = 20,
                status = 1
            )
        )

        // Request 5 of Teh Celup (available: 2)
        val result = saleRepository.completeCheckout(
            lines = listOf(
                CartLine(productId = prodB, quantity = 2),
                CartLine(productId = prodA, quantity = 5)
            ),
            method = PaymentMethod.CASH,
            paidAmount = 100000L,
            customerName = null,
            note = null,
            userId = adminUserId
        )

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is DomainException)
        assertTrue(ex!!.message!!.contains("Teh Celup"))

        // Verify full rollback: both products keep their original stock!
        assertEquals(2, db.productDao().getById(prodA)!!.stock)
        assertEquals(20, db.productDao().getById(prodB)!!.stock)

        // Zero movements
        assertTrue(db.productDao().observeMovementsForProduct(prodA).first().isEmpty())
        assertTrue(db.productDao().observeMovementsForProduct(prodB).first().isEmpty())
    }
}
