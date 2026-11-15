package com.byteall.kharismart.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.byteall.kharismart.data.local.DemoSeed
import com.byteall.kharismart.data.local.KharismartDatabase
import com.byteall.kharismart.data.local.MovementType
import com.byteall.kharismart.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InventoryRepositoryTest {

    private lateinit var db: KharismartDatabase
    private lateinit var inventoryRepository: InventoryRepository
    private var adminUserId: Long = 0L
    private var categoryId: Long = 0L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = KharismartDatabase.buildInMemory(context)
        db.openHelper.writableDatabase

        inventoryRepository = InventoryRepository(
            database = db,
            productDao = db.productDao(),
            categoryDao = db.categoryDao(),
            settingsDao = db.settingsDao()
        )

        runBlocking {
            adminUserId = db.userDao().getByUsername(DemoSeed.ADMIN_USERNAME)!!.userId
            categoryId = db.categoryDao().observeActive().first().first().categoryId
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testSaveNewProductWritesInitialMovementWithoutTotalCost() {
        runBlocking {
            val product = ProductEntity(
                name = "Beras Rojolele 5kg",
                categoryId = categoryId,
                price = 65000,
                costPrice = 58000,
                stock = 10,
                minStock = 2
            )
            val saveResult = inventoryRepository.saveProduct(product, adminUserId)
            assertTrue(saveResult.isSuccess)

            val productId = saveResult.getOrNull()!!
            val movements = db.productDao().observeMovementsForProduct(productId).first()
            assertEquals(1, movements.size)

            val movement = movements.first()
            assertEquals(MovementType.INITIAL, movement.type)
            assertEquals(10, movement.quantityChange)
            assertEquals(10, movement.stockAfter)
            assertEquals(58000L, movement.unitCost)
            assertNull(movement.totalCost) // Initial stock is not considered a purchase
        }
    }

    @Test
    fun testEditProductDoesNotAlterStock() {
        runBlocking {
            val product = ProductEntity(
                name = "Gula Pasir 1kg",
                categoryId = categoryId,
                price = 15000,
                costPrice = 12000,
                stock = 25
            )
            val productId = inventoryRepository.saveProduct(product, adminUserId).getOrNull()!!

            // Edit product price, attempting to set stock = 999
            val edited = db.productDao().getById(productId)!!.copy(
                price = 16000,
                stock = 999
            )
            val updateResult = inventoryRepository.saveProduct(edited, adminUserId)
            assertTrue(updateResult.isSuccess)

            val retrieved = db.productDao().getById(productId)!!
            assertEquals(16000L, retrieved.price)
            assertEquals(25, retrieved.stock) // Stock remains unchanged
        }
    }

    @Test
    fun testRestockUpdatesStockAndCostPrice() {
        runBlocking {
            val product = ProductEntity(
                name = "Kopi Sachet",
                categoryId = categoryId,
                price = 2000,
                costPrice = 1500,
                stock = 10
            )
            val productId = inventoryRepository.saveProduct(product, adminUserId).getOrNull()!!

            // Restock 5 units at Rp700 with updateCostPrice = true
            val restockResult = inventoryRepository.restock(
                productId = productId,
                quantity = 5,
                unitCost = 700,
                updateCostPrice = true,
                userId = adminUserId
            )
            assertTrue(restockResult.isSuccess)

            val retrieved = db.productDao().getById(productId)!!
            assertEquals(15, retrieved.stock)
            assertEquals(700L, retrieved.costPrice)

            val movements = db.productDao().observeMovementsForProduct(productId).first()
            val latest = movements.first()
            assertEquals(MovementType.RESTOCK, latest.type)
            assertEquals(5, latest.quantityChange)
            assertEquals(15, latest.stockAfter)
            assertEquals(3500L, latest.totalCost) // 5 * 700
        }
    }

    @Test
    fun testAdjustStockOpnameNeverTouchesFinance() {
        runBlocking {
            val product = ProductEntity(
                name = "Minyak Goreng 1L",
                categoryId = categoryId,
                price = 18000,
                costPrice = 15000,
                stock = 15
            )
            val productId = inventoryRepository.saveProduct(product, adminUserId).getOrNull()!!

            // Blank reason must fail
            val failBlank = inventoryRepository.adjustStock(productId, 12, "   ", adminUserId)
            assertTrue(failBlank.isFailure)

            // Adjust counted to 12 (delta = -3)
            val adjustResult = inventoryRepository.adjustStock(productId, 12, "Rusak bocor", adminUserId)
            assertTrue(adjustResult.isSuccess)
            assertEquals(-3, adjustResult.getOrNull())

            val retrieved = db.productDao().getById(productId)!!
            assertEquals(12, retrieved.stock)

            // BR-12: Zero rows added to finance table
            val allFinance = db.financeDao().observeBetween("2000-01-01 00:00:00", "2099-01-01 00:00:00").first()
            assertEquals(0, allFinance.size)

            // Adjust to the exact same stock count writes nothing
            val countBefore = db.productDao().observeMovementsForProduct(productId).first().size
            val noDelta = inventoryRepository.adjustStock(productId, 12, "Perhitungan ulang", adminUserId)
            assertTrue(noDelta.isSuccess)
            assertEquals(0, noDelta.getOrNull())
            val countAfter = db.productDao().observeMovementsForProduct(productId).first().size
            assertEquals(countBefore, countAfter)
        }
    }

    @Test
    fun testStockStatusEvaluation() {
        val defaultMin = 5
        val pOut = ProductEntity(productId = 1, name = "A", categoryId = 1, price = 1000, stock = 0)
        assertEquals(StockStatus.OUT, pOut.stockStatus(defaultMin))

        val pLow = ProductEntity(productId = 2, name = "B", categoryId = 1, price = 1000, stock = 3, minStock = 4)
        assertEquals(StockStatus.LOW, pLow.stockStatus(defaultMin))

        val pSafe = ProductEntity(productId = 3, name = "C", categoryId = 1, price = 1000, stock = 10, minStock = 4)
        assertEquals(StockStatus.SAFE, pSafe.stockStatus(defaultMin))
    }
}