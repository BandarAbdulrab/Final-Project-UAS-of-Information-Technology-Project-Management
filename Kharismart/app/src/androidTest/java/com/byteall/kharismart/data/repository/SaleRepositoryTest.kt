package com.byteall.kharismart.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.byteall.kharismart.data.local.DemoSeed
import com.byteall.kharismart.data.local.KharismartDatabase
import com.byteall.kharismart.data.local.entity.TransactionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
}