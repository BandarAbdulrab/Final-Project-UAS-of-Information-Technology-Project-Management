package com.byteall.kharismart.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.byteall.kharismart.data.local.DebtStatus
import com.byteall.kharismart.data.local.DemoSeed
import com.byteall.kharismart.data.local.FinanceType
import com.byteall.kharismart.data.local.KharismartDatabase
import com.byteall.kharismart.data.local.PaymentMethod
import com.byteall.kharismart.data.local.entity.DebtEntity
import com.byteall.kharismart.data.local.entity.ProductEntity
import com.byteall.kharismart.data.local.entity.TransactionEntity
import com.byteall.kharismart.util.time.WibClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class AnalyticsRepositoryTest {

    private lateinit var db: KharismartDatabase
    private lateinit var analyticsRepository: AnalyticsRepository
    private lateinit var debtRepository: DebtRepository
    private lateinit var financeRepository: FinanceRepository
    private lateinit var inventoryRepository: InventoryRepository
    private var adminUserId: Long = 0L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = KharismartDatabase.buildInMemory(context)
        db.openHelper.writableDatabase

        // Fix clock to 2026-03-01 12:00:00 WIB
        WibClock.clock = Clock.fixed(Instant.parse("2026-03-01T05:00:00Z"), WibClock.zone)

        analyticsRepository = AnalyticsRepository(db.analyticsDao())
        debtRepository = DebtRepository(db, db.debtDao())
        financeRepository = FinanceRepository(db.financeDao())
        inventoryRepository = InventoryRepository(db, db.productDao(), db.categoryDao(), db.settingsDao())

        runBlocking {
            adminUserId = db.userDao().getByUsername(DemoSeed.ADMIN_USERNAME)!!.userId
        }
    }

    @After
    fun tearDown() {
        WibClock.clock = Clock.system(WibClock.zone)
        db.close()
    }

    @Test
    fun testBR14AccountingFormulasAndAcceptanceScenarios() {
        runBlocking {
            val todayRange = WibClock.dayRange(WibClock.today())

            // 1. Insert a Credit Sale (Total Rp49.000, Total Cost Rp30.000)
            val trxId = db.saleDao().insertTransaction(
                TransactionEntity(
                    userId = adminUserId,
                    receiptNo = "TRX-20260301-001",
                    date = "2026-03-01 10:00:00",
                    method = PaymentMethod.CREDIT,
                    total = 49000,
                    paid = 0,
                    changeDue = 0,
                    totalCost = 30000,
                    grossProfit = 19000
                )
            )

            // Insert linked unpaid debt
            val debtId = db.debtDao().insertDebt(
                DebtEntity(
                    transactionId = trxId,
                    userId = adminUserId,
                    customerName = "Bu Hirah",
                    date = "2026-03-01 10:00:00",
                    totalAmount = 49000,
                    paidAmount = 0,
                    status = DebtStatus.UNPAID
                )
            )

            // Verification after Credit Sale
            val s1 = analyticsRepository.observeSummary(todayRange).first()
            assertEquals(49000L, s1.revenue)
            assertEquals(19000L, s1.grossProfit)
            assertEquals(49000L, s1.creditSales)
            assertEquals(0L, s1.cashReceived)
            assertEquals(0L, s1.cashBalance)
            assertEquals(49000L, s1.outstandingDebt)

            // 2. Bu Hirah makes partial cash payment of Rp10.000
            val payResult = debtRepository.recordPayment(
                userId = adminUserId,
                debtId = debtId,
                amount = 10000,
                method = PaymentMethod.CASH,
                note = "Cicilan 1"
            )
            assertTrue(payResult.isSuccess)

            val s2 = analyticsRepository.observeSummary(todayRange).first()
            assertEquals(49000L, s2.revenue) // Revenue unchanged
            assertEquals(10000L, s2.cashReceived)
            assertEquals(10000L, s2.cashBalance)
            assertEquals(39000L, s2.outstandingDebt)

            // 3. Operating expense Rp20.000, other income Rp5.000, restock purchase 100 units @ Rp500 (Rp50.000)
            val catId = db.categoryDao().observeActive().first().first().categoryId
            val productId = inventoryRepository.saveProduct(
                ProductEntity(name = "Kecap Manis", categoryId = catId, price = 2500, costPrice = 500, stock = 0),
                adminUserId
            ).getOrNull()!!

            financeRepository.add(adminUserId, "2026-03-01 13:00:00", FinanceType.EXPENSE, "Listrik", 20000, "Token PLN")
            financeRepository.add(adminUserId, "2026-03-01 14:00:00", FinanceType.INCOME, "Sewa", 5000, "Titip jualan")
            inventoryRepository.restock(productId, 100, 500, false, adminUserId)

            val s3 = analyticsRepository.observeSummary(todayRange).first()
            // Net Profit = Gross Profit (19000) + Other Income (5000) - Expense (20000) = 4000
            assertEquals(4000L, s3.netProfit)
            // Cash Balance = Cash Received (10000) + Other Income (5000) - Expense (20000) - Restock (50000) = -55000
            assertEquals(-55000L, s3.cashBalance)

            // 4. Target Progress percentages
            assertEquals(100, AnalyticsRepository.targetProgressPercent(60000, 50000))
            assertEquals(25, AnalyticsRepository.targetProgressPercent(25000, 100000))
            assertEquals(0, AnalyticsRepository.targetProgressPercent(1, null))
            assertEquals(0, AnalyticsRepository.targetProgressPercent(1, 0))
        }
    }
}