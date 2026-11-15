package com.byteall.kharismart.ui.pos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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

        // Submit button must be disabled because tendered cash is 0
        composeTestRule.onNodeWithTag("checkout_btn_submit").assertIsNotEnabled()

        // Input tendered amount of 60000 (enough for 50000)
        composeTestRule.onNodeWithTag("checkout_input_cash_paid").performTextInput("60000")

        // Submit button becomes enabled and change display is shown
        composeTestRule.onNodeWithTag("checkout_btn_submit").assertIsEnabled()
        composeTestRule.onNodeWithTag("checkout_change_amount").assertIsDisplayed()
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

        // Switch to QRIS tab
        composeTestRule.onNodeWithTag("checkout_tab_qris").performClick()

        // Checkbox must be visible and submit disabled until checked
        composeTestRule.onNodeWithTag("checkout_qris_checkbox").assertIsDisplayed()
        composeTestRule.onNodeWithTag("checkout_btn_submit").assertIsNotEnabled()

        // Check the confirmation box
        composeTestRule.onNodeWithTag("checkout_qris_checkbox").performClick()
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

        // Switch to Credit tab
        composeTestRule.onNodeWithTag("checkout_tab_credit").performClick()

        // Customer name input is displayed and submit is disabled
        composeTestRule.onNodeWithTag("checkout_input_customer_name").assertIsDisplayed()
        composeTestRule.onNodeWithTag("checkout_btn_submit").assertIsNotEnabled()

        // Type customer name
        composeTestRule.onNodeWithTag("checkout_input_customer_name").performTextInput("Ibu Siti")

        // Now submit is enabled
        composeTestRule.onNodeWithTag("checkout_btn_submit").assertIsEnabled()
    }
}
