package com.byteall.kharismart.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.byteall.kharismart.data.local.entity.ProductEntity
import com.byteall.kharismart.data.local.entity.TransactionEntity
import com.byteall.kharismart.data.local.entity.UserEntity
import com.byteall.kharismart.util.security.PasswordHasher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseSeedTest {

    private lateinit var db: KharismartDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = KharismartDatabase.buildInMemory(context)
        // Ensure database is opened and seedCallback executed
        db.openHelper.writableDatabase
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun test1_seedCategoriesAndSettings() {
        runBlocking {
            // 1. Categories seed check
            val categories = db.categoryDao().observeAll().first()
            assertEquals(4, categories.size)
            val expectedNames = listOf("Kebutuhan Harian", "Jajanan", "Rokok", "Sembako")
            val actualNames = categories.map { it.name }.sorted()
            assertEquals(expectedNames.sorted(), actualNames)
            assertTrue(categories.all { it.status == 1 })

            // 2. Settings seed check
            val settings = db.settingsDao().getSettings()
            assertNotNull(settings)
            settings?.let {
                assertEquals(1L, it.settingsId)
                assertEquals("Toko Kharisma", it.storeName)
                assertEquals("Thank you", it.receiptFooter)
                assertEquals(5, it.defaultMinStock)
                assertEquals(5, it.autoLockMinutes)
                assertNull(it.qrisBank)
                assertNull(it.storeAddress)
                assertNull(it.targetDaily)
                assertNull(it.targetWeekly)
                assertNull(it.targetMonthly)
            }
        }
    }

    @Test
    fun test2_seedUsers() {
        runBlocking {
            val users = db.userDao().observeAllUsers().first()
            assertEquals(2, users.size)

            val admin = db.userDao().getByUsername(DemoSeed.ADMIN_USERNAME)
            assertNotNull(admin)
            admin?.let {
                assertEquals("sumarti", it.username)
                assertEquals(Role.ADMIN, it.role)
                assertEquals("Ibu Sumarti", it.name)
                assertEquals(1, it.status)
                assertTrue(PasswordHasher.verifyPin(DemoSeed.ADMIN_PIN, it.pinHash))
                assertFalse(PasswordHasher.verifyPin("999999", it.pinHash))
                assertNotNull(it.recoveryHash)
                assertTrue(PasswordHasher.verifyRecoveryCode(DemoSeed.ADMIN_RECOVERY_CODE, it.recoveryHash!!))
            }

            val cashier = db.userDao().getByUsername(DemoSeed.CASHIER_USERNAME)
            assertNotNull(cashier)
            cashier?.let {
                assertEquals("rizki", it.username)
                assertEquals(Role.CASHIER, it.role)
                assertEquals("Rizki", it.name)
                assertEquals(1, it.status)
                assertTrue(PasswordHasher.verifyPin(DemoSeed.CASHIER_PIN, it.pinHash))
                assertFalse(PasswordHasher.verifyPin("9999", it.pinHash))
                assertNull(it.recoveryHash)
            }
        }
    }

    @Test
    fun test3_stockTriggerEnforcement() {
        runBlocking {
            val categories = db.categoryDao().observeAll().first()
            val catId = categories.first().categoryId

            // 1. Inserting product with stock -1 must fail
            try {
                db.productDao().insert(
                    ProductEntity(
                        name = "Invalid Stock Item",
                        categoryId = catId,
                        price = 5000,
                        stock = -1
                    )
                )
                fail("Expected exception when inserting product with stock < 0")
            } catch (e: SQLiteException) {
                assertTrue(e.message?.contains("stock must be >= 0") == true)
            }

            // 2. Inserting product with stock 10 succeeds
            val pId = db.productDao().insert(
                ProductEntity(
                    name = "Valid Stock Item",
                    categoryId = catId,
                    price = 5000,
                    stock = 10
                )
            )
            assertTrue(pId > 0)

            // 3. Updating product to stock -1 must fail
            try {
                db.productDao().updateStock(pId, -1)
                fail("Expected exception when updating product stock < 0")
            } catch (e: SQLiteException) {
                assertTrue(e.message?.contains("stock must be >= 0") == true)
            }

            // 4. Updating product stock to 0 succeeds
            db.productDao().updateStock(pId, 0)
            val updated = db.productDao().getById(pId)
            assertEquals(0, updated?.stock)
        }
    }

    @Test
    fun test4_decrementStockIfAvailable() {
        runBlocking {
            val categories = db.categoryDao().observeAll().first()
            val catId = categories.first().categoryId

            val pId = db.productDao().insert(
                ProductEntity(
                    name = "Stock Decrement Test",
                    categoryId = catId,
                    price = 10000,
                    stock = 5
                )
            )

            // Decrement 3 when 5 available -> returns 1, stock becomes 2
            val rows1 = db.productDao().decrementStockIfAvailable(pId, 3)
            assertEquals(1, rows1)
            assertEquals(2, db.productDao().getById(pId)?.stock)

            // Decrement 3 when only 2 available -> returns 0, stock remains 2
            val rows2 = db.productDao().decrementStockIfAvailable(pId, 3)
            assertEquals(0, rows2)
            assertEquals(2, db.productDao().getById(pId)?.stock)
        }
    }

    @Test
    fun test5_settingsTriggerSingleRow() {
        val sdb = db.openHelper.writableDatabase

        // Inserting settings with settings_id 2 must throw
        try {
            sdb.execSQL("INSERT INTO settings (settings_id, store_name) VALUES (2, 'Second Store')")
            fail("Expected trigger failure when inserting settings_id 2")
        } catch (e: SQLiteException) {
            assertTrue(e.message?.contains("settings is a single-row table") == true)
        }

        // Inserting settings with settings_id 1 when row already exists must throw
        try {
            sdb.execSQL("INSERT INTO settings (settings_id, store_name) VALUES (1, 'Second Store')")
            fail("Expected trigger failure when inserting duplicate settings_id 1")
        } catch (e: SQLiteException) {
            assertTrue(
                e.message?.contains("settings is a single-row table") == true ||
                    e is SQLiteConstraintException
            )
        }
    }

    @Test
    fun test6_tableConstraints() {
        runBlocking {
            val categories = db.categoryDao().observeAll().first()
            val catId = categories.first().categoryId

            // 1. Duplicate username throws
            try {
                db.userDao().insert(
                    UserEntity(
                        username = DemoSeed.ADMIN_USERNAME,
                        pinHash = "dummy-hash",
                        name = "Duplicate Admin",
                        role = Role.ADMIN
                    )
                )
                fail("Expected constraint violation on duplicate username")
            } catch (_: SQLiteConstraintException) {
                // Expected
            }

            // 2. Two products with NULL barcode are allowed
            val p1 = db.productDao().insert(
                ProductEntity(
                    barcode = null,
                    name = "Null Barcode 1",
                    categoryId = catId,
                    price = 1000
                )
            )
            val p2 = db.productDao().insert(
                ProductEntity(
                    barcode = null,
                    name = "Null Barcode 2",
                    categoryId = catId,
                    price = 2000
                )
            )
            assertTrue(p1 > 0 && p2 > 0)

            // 3. Two products with the same barcode throw
            val uniqueBarcode = "8992753123456"
            val p3 = db.productDao().insert(
                ProductEntity(
                    barcode = uniqueBarcode,
                    name = "Barcode Item 1",
                    categoryId = catId,
                    price = 3000
                )
            )
            assertTrue(p3 > 0)

            try {
                db.productDao().insert(
                    ProductEntity(
                        barcode = uniqueBarcode,
                        name = "Barcode Item 2",
                        categoryId = catId,
                        price = 4000
                    )
                )
                fail("Expected constraint violation on duplicate barcode")
            } catch (_: SQLiteConstraintException) {
                // Expected
            }

            // 4. Deleting a category that still has a product throws (RESTRICT)
            val sdb = db.openHelper.writableDatabase
            try {
                sdb.execSQL("DELETE FROM categories WHERE category_id = $catId")
                fail("Expected foreign key constraint violation when deleting referenced category")
            } catch (_: SQLiteConstraintException) {
                // Expected
            }
        }
    }

    @Test
    fun test7_getMaxSequenceForPrefix() {
        runBlocking {
            val prefix = "TRX-20260301-"

            // Empty database for prefix returns null
            val initialMax = db.saleDao().getMaxSequenceForPrefix(prefix)
            assertNull(initialMax)

            val admin = db.userDao().getByUsername(DemoSeed.ADMIN_USERNAME)!!

            // Insert receipts ending in 999 and 1000
            db.saleDao().insertTransaction(
                TransactionEntity(
                    userId = admin.userId,
                    receiptNo = "${prefix}999",
                    date = "2026-03-01 10:00:00",
                    method = "cash",
                    total = 10000,
                    paid = 10000,
                    changeDue = 0,
                    totalCost = 8000,
                    grossProfit = 2000
                )
            )

            db.saleDao().insertTransaction(
                TransactionEntity(
                    userId = admin.userId,
                    receiptNo = "${prefix}1000",
                    date = "2026-03-01 11:00:00",
                    method = "cash",
                    total = 20000,
                    paid = 20000,
                    changeDue = 0,
                    totalCost = 15000,
                    grossProfit = 5000
                )
            )

            // Must return 1000, proving numerical sorting rather than alphabetical
            val maxSeq = db.saleDao().getMaxSequenceForPrefix(prefix)
            assertEquals(1000, maxSeq)
        }
    }
}
