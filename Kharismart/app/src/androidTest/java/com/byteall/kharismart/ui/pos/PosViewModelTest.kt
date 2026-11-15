package com.byteall.kharismart.ui.pos

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.byteall.kharismart.data.local.KharismartDatabase
import com.byteall.kharismart.data.local.entity.CategoryEntity
import com.byteall.kharismart.data.local.entity.ProductEntity
import com.byteall.kharismart.data.repository.InventoryRepository
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
class PosViewModelTest {

    private lateinit var database: KharismartDatabase
    private lateinit var inventoryRepository: InventoryRepository
    private lateinit var viewModel: PosViewModel

    private val sampleProductA = ProductEntity(
        productId = 1L,
        barcode = "8991234000001",
        name = "Indomie Goreng",
        categoryId = 1L,
        price = 3500L,
        costPrice = 2800L,
        stock = 3, // Low stock limit for testing
        minStock = 5,
        photo = null,
        status = 1
    )

    private val sampleProductOutOfStock = ProductEntity(
        productId = 2L,
        barcode = "8991234000002",
        name = "Kopi Sachet",
        categoryId = 1L,
        price = 2000L,
        costPrice = 1500L,
        stock = 0,
        minStock = 5,
        photo = null,
        status = 1
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = KharismartDatabase.buildInMemory(context)
        database.openHelper.writableDatabase

        inventoryRepository = InventoryRepository(
            database = database,
            productDao = database.productDao(),
            categoryDao = database.categoryDao(),
            settingsDao = database.settingsDao()
        )

        // Seed products into database
        runBlocking {
            database.productDao().insert(sampleProductA)
            database.productDao().insert(sampleProductOutOfStock)
        }

        viewModel = PosViewModel(inventoryRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testAddToCart_calculatesSubtotalAndTotalCorrectly() {
        viewModel.addToCart(sampleProductA)

        val cart = viewModel.cartItems.value
        assertEquals(1, cart.size)
        assertEquals(sampleProductA.productId, cart[0].product.productId)
        assertEquals(1, cart[0].quantity)
        assertEquals(3500L, cart[0].unitPrice)
        assertEquals(3500L, cart[0].subtotal)
        assertEquals(3500L, viewModel.totalAmount.value)
    }

    @Test
    fun testAddToCart_incrementsQuantityWhenAddedAgain() {
        viewModel.addToCart(sampleProductA)
        viewModel.addToCart(sampleProductA)

        val cart = viewModel.cartItems.value
        assertEquals(1, cart.size)
        assertEquals(2, cart[0].quantity)
        assertEquals(7000L, cart[0].subtotal)
        assertEquals(7000L, viewModel.totalAmount.value)
    }

    @Test
    fun testAddToCart_enforcesAvailableStockLimit_BR01() {
        // sampleProductA has stock = 3
        viewModel.addToCart(sampleProductA) // qty = 1
        viewModel.addToCart(sampleProductA) // qty = 2
        viewModel.addToCart(sampleProductA) // qty = 3
        assertEquals(3, viewModel.cartItems.value[0].quantity)
        assertNull(viewModel.uiState.value.errorMessage)

        // 4th addition must be rejected
        viewModel.addToCart(sampleProductA)
        assertEquals(3, viewModel.cartItems.value[0].quantity)
        val error = viewModel.uiState.value.errorMessage
        assertNotNull("Should emit out-of-stock warning", error)
        assertTrue(error!!.contains("tidak mencukupi"))
    }

    @Test
    fun testAddToCart_rejectsOutOfStockProduct() {
        viewModel.addToCart(sampleProductOutOfStock)

        assertTrue("Cart must remain empty", viewModel.cartItems.value.isEmpty())
        val error = viewModel.uiState.value.errorMessage
        assertNotNull(error)
        assertTrue(error!!.contains("habis"))
    }

    @Test
    fun testUpdateQuantity_decrementsAndRemovesItemAtZero() {
        viewModel.addToCart(sampleProductA)
        viewModel.addToCart(sampleProductA)
        assertEquals(2, viewModel.cartItems.value[0].quantity)

        // Decrement by 1 -> qty 1
        viewModel.updateQuantity(sampleProductA.productId, -1)
        assertEquals(1, viewModel.cartItems.value[0].quantity)
        assertEquals(3500L, viewModel.totalAmount.value)

        // Decrement by 1 again -> qty 0 -> removed
        viewModel.updateQuantity(sampleProductA.productId, -1)
        assertTrue(viewModel.cartItems.value.isEmpty())
        assertEquals(0L, viewModel.totalAmount.value)
    }

    @Test
    fun testRemoveItem_removesFromCartImmediately() {
        viewModel.addToCart(sampleProductA)
        assertEquals(1, viewModel.cartItems.value.size)

        viewModel.removeItem(sampleProductA.productId)
        assertTrue(viewModel.cartItems.value.isEmpty())
    }

    @Test
    fun testClearCart_emptiesCartAndResetsTotal() {
        viewModel.addToCart(sampleProductA)
        viewModel.clearCart()

        assertTrue(viewModel.cartItems.value.isEmpty())
        assertEquals(0L, viewModel.totalAmount.value)
    }

    @Test
    fun testOnBarcodeScanned_findsProductAndAddsToCart() = runBlocking {
        viewModel.onBarcodeScanned("8991234000001")

        // Wait for coroutine to add item
        kotlinx.coroutines.delay(100)

        val cart = viewModel.cartItems.value
        assertEquals(1, cart.size)
        assertEquals("Indomie Goreng", cart[0].product.name)
    }

    @Test
    fun testOnBarcodeScanned_emitsErrorForUnknownBarcode() = runBlocking {
        viewModel.onBarcodeScanned("UNKNOWN_BARCODE_999")

        kotlinx.coroutines.delay(100)

        assertTrue(viewModel.cartItems.value.isEmpty())
        val error = viewModel.uiState.value.errorMessage
        assertNotNull(error)
        assertTrue(error!!.contains("tidak ditemukan"))
    }
}
