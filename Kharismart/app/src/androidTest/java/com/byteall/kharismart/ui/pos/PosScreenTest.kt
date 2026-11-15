package com.byteall.kharismart.ui.pos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.byteall.kharismart.data.local.KharismartDatabase
import com.byteall.kharismart.data.repository.InventoryRepository
import com.byteall.kharismart.ui.theme.KharismartTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PosScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: KharismartDatabase
    private lateinit var inventoryRepository: InventoryRepository
    private lateinit var viewModel: PosViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = KharismartDatabase.buildInMemory(context)
        database.openHelper.writableDatabase

        inventoryRepository = InventoryRepository(
            database = database,
            productDao = database.productDao(),
            categoryDao = database.categoryDao(),
            settingsDao = database.settingsDao()
        )
        viewModel = PosViewModel(inventoryRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testPosScreen_initialEmptyStateAndControlsDisplayed() {
        composeTestRule.setContent {
            KharismartTheme {
                PosScreen(viewModel = viewModel)
            }
        }

        // Barcode input and search field should be visible
        composeTestRule.onNodeWithTag("input_barcode_scanner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_pos_search").assertIsDisplayed()

        // Cart should show empty placeholder
        composeTestRule.onNodeWithTag("pos_empty_cart").assertIsDisplayed()

        // Checkout button should be disabled when cart is empty
        composeTestRule.onNodeWithTag("btn_pos_checkout").assertIsNotEnabled()
    }
}
