package com.byteall.kharismart.ui.inventory

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.byteall.kharismart.data.local.entity.CategoryEntity
import com.byteall.kharismart.data.local.entity.ProductEntity
import com.byteall.kharismart.ui.theme.KharismartTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleCategories = listOf(
        CategoryEntity(categoryId = 1L, name = "Sembako", status = 1),
        CategoryEntity(categoryId = 2L, name = "Jajanan", status = 1)
    )

    private val sampleProduct = ProductEntity(
        productId = 10L,
        barcode = "8991234567890",
        name = "Minyak Goreng 1L",
        categoryId = 1L,
        price = 14000L,
        costPrice = 12000L,
        stock = 15,
        minStock = 5,
        photo = null,
        status = 1
    )

    @Test
    fun testAddEditProductDialog_showsWarningWhenSellingPriceBelowCost() {
        composeTestRule.setContent {
            KharismartTheme {
                AddEditProductDialog(
                    initialProduct = null,
                    categories = sampleCategories,
                    defaultMinStock = 5,
                    isAdmin = true,
                    onDismiss = {},
                    onSaveProduct = {},
                    onAddNewCategory = {}
                )
            }
        }

        // Initially no warning
        composeTestRule.onNodeWithTag("input_product_name").performTextInput("Beras 5kg")
        composeTestRule.onNodeWithTag("input_selling_price").performTextInput("45000")
        composeTestRule.onNodeWithTag("input_cost_price").performTextInput("50000")

        // Price (45000) < Cost (50000), warning banner should be displayed
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Peringatan: Harga jual (Rp45.000) lebih rendah dari harga modal (Rp50.000)!")
            .assertIsDisplayed()
    }

    @Test
    fun testRestockDialog_calculatesTotalCostAndUpdatesProjection() {
        var confirmedQty = 0
        var confirmedCost = 0L

        composeTestRule.setContent {
            KharismartTheme {
                RestockDialog(
                    product = sampleProduct,
                    onDismiss = {},
                    onConfirmRestock = { qty, cost, _ ->
                        confirmedQty = qty
                        confirmedCost = cost
                    }
                )
            }
        }

        // Enter restock quantity: 10 units at Rp11.000
        composeTestRule.onNodeWithTag("input_restock_qty").performTextInput("10")

        // Verify total purchase cost: 10 * 12.000 (prefilled cost) = Rp120.000
        composeTestRule.onNodeWithText("Rp120.000").assertIsDisplayed()
        // Verify projected stock: 15 + 10 = 25 unit
        composeTestRule.onNodeWithText("25 unit").assertIsDisplayed()

        // Confirm restock
        composeTestRule.onNodeWithTag("btn_confirm_restock").performClick()
        assertEquals(10, confirmedQty)
        assertEquals(12000L, confirmedCost)
    }

    @Test
    fun testStockOpnameDialog_calculatesNegativeDeltaAndRequiresReason() {
        var confirmedCount = 0
        var confirmedReason = ""

        composeTestRule.setContent {
            KharismartTheme {
                StockOpnameDialog(
                    product = sampleProduct,
                    onDismiss = {},
                    onConfirmAdjustment = { count, reason ->
                        confirmedCount = count
                        confirmedReason = reason
                    }
                )
            }
        }

        // Counted stock is empty/prefilled with 15. Set to 12 (loss of 3 units)
        composeTestRule.onNodeWithTag("input_counted_stock").performTextInput("12")
        // Reason must be entered
        composeTestRule.onNodeWithTag("input_opname_reason").performTextInput("Bocor saat bongkar muat")

        // Verify button enabled and click
        composeTestRule.onNodeWithTag("btn_confirm_opname").assertIsEnabled()
        composeTestRule.onNodeWithTag("btn_confirm_opname").performClick()

        assertEquals(12, confirmedCount)
        assertEquals("Bocor saat bongkar muat", confirmedReason)
    }
}
