package com.byteall.kharismart.ui.pos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.byteall.kharismart.ui.theme.KharismartTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CheckoutDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testCheckoutDialog_rendersInitialCashTabAndDisabledUntilPaid() {
        composeTestRule.setContent {
            KharismartTheme {
                CheckoutDialog(
                    totalAmount = 50000L,
                    qrisBank = "BCA",
                    qrisOwnerName = "Toko Kharisma",
                    qrisImageFilename = null,
                    debtorSuggestions = emptyList(),
                    isSubmitting = false,
                    onDismissRequest = {},
                    onConfirmCheckout = { _, _, _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithTag("checkout_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("checkout_tab_cash").assertIsDisplayed()
        composeTestRule.onNodeWithTag("checkout_tab_qris").assertIsDisplayed()
        composeTestRule.onNodeWithTag("checkout_tab_credit").assertIsDisplayed()

        composeTestRule.onNodeWithTag("checkout_btn_submit").assertIsNotEnabled()

        composeTestRule.onNodeWithTag("checkout_input_cash_paid")
            .performScrollTo()
            .performTextInput("60000")

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("checkout_btn_submit").assertIsEnabled()
        composeTestRule.onNodeWithTag("checkout_change_amount", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun testCheckoutDialog_qrisTabRequiresCheckboxConfirmation() {
        composeTestRule.setContent {
            KharismartTheme {
                CheckoutDialog(
                    totalAmount = 35000L,
                    qrisBank = "Bank Mandiri",
                    qrisOwnerName = "Toko Kharisma",
                    qrisImageFilename = null,
                    debtorSuggestions = emptyList(),
                    isSubmitting = false,
                    onDismissRequest = {},
                    onConfirmCheckout = { _, _, _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithTag("checkout_tab_qris").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("checkout_qris_checkbox", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("checkout_btn_submit").assertIsNotEnabled()

        composeTestRule.onNodeWithTag("checkout_qris_checkbox", useUnmergedTree = true)
            .performClick()
        composeTestRule.onNodeWithTag("checkout_btn_submit").assertIsEnabled()
    }

    @Test
    fun testCheckoutDialog_creditTabRequiresCustomerName() {
        composeTestRule.setContent {
            KharismartTheme {
                CheckoutDialog(
                    totalAmount = 25000L,
                    qrisBank = null,
                    qrisOwnerName = null,
                    qrisImageFilename = null,
                    debtorSuggestions = listOf("Ibu Dewi", "Pak Joko"),
                    isSubmitting = false,
                    onDismissRequest = {},
                    onConfirmCheckout = { _, _, _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithTag("checkout_tab_credit").performClick()

        composeTestRule.onNodeWithTag("checkout_input_customer_name")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("checkout_btn_submit").assertIsNotEnabled()

        composeTestRule.onNodeWithTag("checkout_input_customer_name")
            .performTextInput("Ibu Siti")

        composeTestRule.onNodeWithTag("checkout_btn_submit").assertIsEnabled()
    }
}