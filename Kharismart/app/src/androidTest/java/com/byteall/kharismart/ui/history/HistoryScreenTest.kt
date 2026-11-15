package com.byteall.kharismart.ui.history

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.byteall.kharismart.data.local.DemoSeed
import com.byteall.kharismart.data.local.KharismartDatabase
import com.byteall.kharismart.data.local.PaymentMethod
import com.byteall.kharismart.data.local.entity.ProductEntity
import com.byteall.kharismart.data.local.entity.TransactionEntity
import com.byteall.kharismart.data.local.entity.TransactionItemEntity
import com.byteall.kharismart.data.repository.SaleRepository
import com.byteall.kharismart.session.AppSession
import com.byteall.kharismart.session.SessionUser
import com.byteall.kharismart.session.UserRole
import com.byteall.kharismart.ui.theme.KharismartTheme
import com.byteall.kharismart.util.time.WibClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: KharismartDatabase
    private lateinit var saleRepository: SaleRepository
    private var adminUserId: Long = 0L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = KharismartDatabase.buildInMemory(context)
        database.openHelper.writableDatabase

        saleRepository = SaleRepository(
            database = database,
            saleDao = database.saleDao(),
            productDao = database.productDao(),
            debtDao = database.debtDao(),
            settingsDao = database.settingsDao()
        )

        runBlocking {
            val admin = database.userDao().getByUsername(DemoSeed.ADMIN_USERNAME)!!
            adminUserId = admin.userId

            AppSession.signIn(
                SessionUser(
                    userId = admin.userId,
                    username = admin.username,
                    name = admin.name,
                    role = UserRole.ADMIN
                )
            )

            val prodId = database.productDao().insert(
                ProductEntity(
                    name = "Beras Rojolele 5kg",
                    categoryId = 1L,
                    price = 68000L,
                    costPrice = 58000L,
                    stock = 20,
                    status = 1
                )
            )

            val trxId = database.saleDao().insertTransaction(
                TransactionEntity(
                    userId = adminUserId,
                    receiptNo = "TRX-20261115-099",
                    date = WibClock.nowString(),
                    method = PaymentMethod.CASH,
                    total = 68000L,
                    paid = 70000L,
                    changeDue = 2000L,
                    totalCost = 58000L,
                    grossProfit = 10000L
                )
            )

            database.saleDao().insertItems(
                listOf(
                    TransactionItemEntity(
                        transactionId = trxId,
                        productId = prodId,
                        quantity = 1,
                        price = 68000L,
                        costPrice = 58000L,
                        subtotal = 68000L
                    )
                )
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testHistoryScreen_displaysTransactionAndExpandsDetails() {
        val viewModel = HistoryViewModel(saleRepository)

        composeTestRule.setContent {
            KharismartTheme {
                HistoryScreen(viewModel = viewModel)
            }
        }

        // 1. Transaction card and Rp68.000 total are displayed
        composeTestRule.onNodeWithTag("history_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("TRX-20261115-099").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Rp68.000").onFirst().assertIsDisplayed()

        // 2. Scroll to transaction card and click to expand
        composeTestRule.onNodeWithTag("history_card_TRX-20261115-099")
            .performScrollTo()
            .performClick()

        // 3. Wait for item node to enter semantics tree, then scroll and verify displayed
        composeTestRule.waitUntil(5000) {
            composeTestRule
                .onAllNodesWithText("1 x Beras Rojolele 5kg", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("1 x Beras Rojolele 5kg", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("history_btn_reprint_TRX-20261115-099", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()

        // 4. Click reprint button to show ReceiptDialog
        composeTestRule.onNodeWithTag("history_btn_reprint_TRX-20261115-099", useUnmergedTree = true)
            .performClick()

        // 5. Receipt Dialog is displayed with reprint badge
        composeTestRule.onNodeWithTag("receipt_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("SALINAN / REPRINT").assertIsDisplayed()
        composeTestRule.onNodeWithTag("receipt_btn_done").assertIsDisplayed()
    }
}