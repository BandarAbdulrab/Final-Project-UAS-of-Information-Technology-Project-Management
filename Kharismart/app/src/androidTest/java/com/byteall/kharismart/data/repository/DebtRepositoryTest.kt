package com.byteall.kharismart.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.byteall.kharismart.data.local.DebtStatus
import com.byteall.kharismart.data.local.DemoSeed
import com.byteall.kharismart.data.local.KharismartDatabase
import com.byteall.kharismart.data.local.PaymentMethod
import com.byteall.kharismart.data.local.entity.DebtEntity
import com.byteall.kharismart.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebtRepositoryTest {

    private lateinit var db: KharismartDatabase
    private lateinit var debtRepository: DebtRepository
    private lateinit var saleRepository: SaleRepository
    private var adminUserId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = KharismartDatabase.buildInMemory(context)
        debtRepository = DebtRepository(db, db.debtDao())
        saleRepository = SaleRepository(
            database = db,
            saleDao = db.saleDao(),
            productDao = db.productDao(),
            debtDao = db.debtDao(),
            settingsDao = db.settingsDao()
        )
        adminUserId = db.userDao().getByUsername(DemoSeed.ADMIN_USERNAME)!!.userId
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testPartialAndFullSettlementUpdatesStatusAndPayments() = runBlocking {
        val debtId = debtRepository.addManualDebt(
            userId = adminUserId,
            customerName = "Bu Hirah",
            amount = 49_000L,
            note = "Jatuh tempo Jumat"
        ).getOrThrow()

        assertEquals(49_000L, debtRepository.observeOutstandingTotal().first())

        // Reject overpayment
        val overpay = debtRepository.recordPayment(
            userId = adminUserId,
            debtId = debtId,
            amount = 50_000L,
            method = PaymentMethod.CASH,
            note = null
        )
        assertTrue(overpay.isFailure)

        // Partial payment Rp10.000
        debtRepository.recordPayment(
            userId = adminUserId,
            debtId = debtId,
            amount = 10_000L,
            method = PaymentMethod.CASH,
            note = "Cicilan 1"
        ).getOrThrow()

        val afterPartial = debtRepository.getDebt(debtId)!!
        assertEquals(10_000L, afterPartial.paidAmount)
        assertEquals(DebtStatus.UNPAID, afterPartial.status)
        assertEquals(39_000L, debtRepository.observeOutstandingTotal().first())

        // Remaining payment Rp39.000 via QRIS -> marks PAID
        debtRepository.recordPayment(
            userId = adminUserId,
            debtId = debtId,
            amount = 39_000L,
            method = PaymentMethod.QRIS,
            note = "Pelunasan"
        ).getOrThrow()

        val afterFull = debtRepository.getDebt(debtId)!!
        assertEquals(49_000L, afterFull.paidAmount)
        assertEquals(DebtStatus.PAID, afterFull.status)
        assertEquals(0L, debtRepository.observeOutstandingTotal().first())

        val payments = debtRepository.observePayments(debtId).first()
        assertEquals(2, payments.size)
    }

    @Test
    fun testDeleteRestrictionsForPosAndPaidDebts() = runBlocking {
        // 1. Manual debt with 0 payments can be deleted
        val manualId = debtRepository.addManualDebt(
            userId = adminUserId,
            customerName = "Pak Joko",
            amount = 15_000L,
            note = null
        ).getOrThrow()
        assertTrue(debtRepository.deleteManualDebt(manualId).isSuccess)
        assertNull(debtRepository.getDebt(manualId))

        // 2. Manual debt with a payment cannot be deleted
        val paidManualId = debtRepository.addManualDebt(
            userId = adminUserId,
            customerName = "Bu Sari",
            amount = 20_000L,
            note = null
        ).getOrThrow()
        debtRepository.recordPayment(
            userId = adminUserId,
            debtId = paidManualId,
            amount = 5_000L,
            method = PaymentMethod.CASH,
            note = null
        ).getOrThrow()
        assertTrue(debtRepository.deleteManualDebt(paidManualId).isFailure)

        // 3. POS-linked debt can never be deleted and resolves its receipt
        val txId = db.saleDao().insertTransaction(
            TransactionEntity(
                userId = adminUserId,
                receiptNo = "TRX-20261114-001",
                date = "2026-11-14 09:00:00",
                method = PaymentMethod.CREDIT,
                total = 30_000L,
                paid = 0L,
                changeDue = 0L,
                totalCost = 22_000L,
                grossProfit = 8_000L,
                note = null
            )
        )
        val posDebtId = db.debtDao().insertDebt(
            DebtEntity(
                transactionId = txId,
                userId = adminUserId,
                customerName = "Bu Hirah",
                date = "2026-11-14 09:00:00",
                totalAmount = 30_000L,
                paidAmount = 0L,
                status = DebtStatus.UNPAID,
                note = null
            )
        )
        assertTrue(debtRepository.deleteManualDebt(posDebtId).isFailure)

        val receipt = saleRepository.getReceiptData(txId)
        assertNotNull(receipt)
        assertEquals("TRX-20261114-001", receipt!!.transaction.receiptNo)
    }
}