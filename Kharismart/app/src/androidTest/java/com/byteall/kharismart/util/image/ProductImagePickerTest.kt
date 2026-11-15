package com.byteall.kharismart.util.image

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.byteall.kharismart.ui.theme.KharismartTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductImagePickerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testPlaceholderAndSelectButton_displayedWhenNoPhoto() {
        composeTestRule.setContent {
            KharismartTheme {
                ProductImagePicker(
                    photoFilename = null,
                    onPhotoChanged = {}
                )
            }
        }

        // Placeholder icon container and select button should be visible
        composeTestRule.onNodeWithTag("product_image_placeholder").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_pick_photo").assertIsDisplayed()

        // Delete button must NOT exist when no photo is attached
        composeTestRule.onNodeWithTag("btn_delete_photo").assertDoesNotExist()
    }

    @Test
    fun testChangeAndDeleteButtons_displayedWhenPhotoAttached() {
        composeTestRule.setContent {
            KharismartTheme {
                ProductImagePicker(
                    photoFilename = "sample_product.jpg",
                    onPhotoChanged = {}
                )
            }
        }

        // Both pick/change and delete buttons must be visible
        composeTestRule.onNodeWithTag("btn_pick_photo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_delete_photo").assertIsDisplayed()
    }

    @Test
    fun testClickingDeletePhoto_triggersCallbackWithNull() {
        var updatedFilename: String? = "existing_photo.jpg"

        composeTestRule.setContent {
            KharismartTheme {
                ProductImagePicker(
                    photoFilename = "existing_photo.jpg",
                    onPhotoChanged = { updatedFilename = it }
                )
            }
        }

        // Click delete photo button
        composeTestRule.onNodeWithTag("btn_delete_photo").performClick()

        // Verify callback was invoked with null
        composeTestRule.waitForIdle()
        assertNull("Callback should receive null when photo is deleted", updatedFilename)
    }
}
