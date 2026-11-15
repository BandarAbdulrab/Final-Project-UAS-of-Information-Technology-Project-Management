package com.byteall.kharismart.ui.inventory

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.byteall.kharismart.data.local.entity.CategoryEntity
import com.byteall.kharismart.data.local.entity.ProductEntity
import com.byteall.kharismart.ui.theme.KharismartTheme
import org.junit.Assert.assertEquals
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

        composeTestRule.onNodeWithTag("input_product_name").performScrollTo().performTextInput("Beras 5kg")
        composeTestRule.onNodeWithTag("input_selling_price").performScrollTo().performTextInput("45000")
        composeTestRule.onNodeWithTag("input_cost_price").performScrollTo().performTextInput("50000")

        Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("price_below_cost_warning", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Peringatan: Harga jual (Rp45.000) lebih rendah dari harga modal (Rp50.000)!",
            useUnmergedTree = true
        ).assertIsDisplayed()
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

        composeTestRule.onNodeWithTag("input_restock_qty").performScrollTo().performTextInput("10")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Rp120.000", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("25 unit", useUnmergedTree = true).assertIsDisplayed()

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

        composeTestRule.onNodeWithTag("input_counted_stock").performScrollTo().performTextInput("12")
        composeTestRule.onNodeWithTag("input_opname_reason").performScrollTo().performTextInput("Bocor saat bongkar muat")

        composeTestRule.onNodeWithTag("btn_confirm_opname").assertIsEnabled()
        composeTestRule.onNodeWithTag("btn_confirm_opname").performClick()

        assertEquals(12, confirmedCount)
        assertEquals("Bocor saat bongkar muat", confirmedReason)
    }
}