package com.byteall.kharismart.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.byteall.kharismart.data.local.DemoSeed
import com.byteall.kharismart.data.local.KharismartDatabase
import com.byteall.kharismart.data.local.Role
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserRepositoryTest {

    private lateinit var db: KharismartDatabase
    private lateinit var userRepository: UserRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = KharismartDatabase.buildInMemory(context)
        db.openHelper.writableDatabase
        userRepository = UserRepository(db.userDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testPinValidation() {
        runBlocking {
            // Short PIN (3 digits)
            val resultShort = userRepository.addUser("Budi", "budi1", "123", Role.CASHIER)
            assertTrue(resultShort.isFailure)

            // Long PIN (7 digits)
            val resultLong = userRepository.addUser("Budi", "budi2", "1234567", Role.CASHIER)
            assertTrue(resultLong.isFailure)

            // Non-digits in PIN
            val resultLetters = userRepository.addUser("Budi", "budi3", "12ab", Role.CASHIER)
            assertTrue(resultLetters.isFailure)

            // Valid 4 and 6 digit PINs succeed
            val resultValid4 = userRepository.addUser("Budi", "budi4", "1234", Role.CASHIER)
            assertTrue(resultValid4.isSuccess)

            val resultValid6 = userRepository.addUser("Budi", "budi6", "123456", Role.CASHIER)
            assertTrue(resultValid6.isSuccess)
        }
    }

    @Test
    fun testDuplicateUsernameCaseInsensitive() {
        runBlocking {
            val duplicate = userRepository.addUser("Admin Baru", "SUMARTI", "5555", Role.ADMIN)
            assertTrue(duplicate.isFailure)
        }
    }

    @Test
    fun testAdminDeactivationProtection() {
        runBlocking {
            val admin = db.userDao().getByUsername(DemoSeed.ADMIN_USERNAME)!!
            val cashier = db.userDao().getByUsername(DemoSeed.CASHIER_USERNAME)!!

            // Deactivating the cashier succeeds
            val cashierResult = userRepository.setActive(cashier.userId, false)
            assertTrue(cashierResult.isSuccess)
            assertEquals(0, db.userDao().getById(cashier.userId)?.status)

            // Deactivating the last active admin must fail
            val adminResult = userRepository.setActive(admin.userId, false)
            assertTrue(adminResult.isFailure)
            assertEquals(1, db.userDao().getById(admin.userId)?.status)

            // After adding a second admin, the original admin can be deactivated
            val addSecondAdmin = userRepository.addUser("Admin Dua", "admin2", "1122", Role.ADMIN)
            assertTrue(addSecondAdmin.isSuccess)

            val deactiveFirstAdmin = userRepository.setActive(admin.userId, false)
            assertTrue(deactiveFirstAdmin.isSuccess)
            assertEquals(0, db.userDao().getById(admin.userId)?.status)
        }
    }

    @Test
    fun testAdminResetPinWithRecoveryCode() {
        runBlocking {
            val admin = db.userDao().getByUsername(DemoSeed.ADMIN_USERNAME)!!

            // Reset with valid demo recovery code
            val resetResult = userRepository.resetAdminPinWithRecoveryCode(
                userId = admin.userId,
                recoveryCode = DemoSeed.ADMIN_RECOVERY_CODE,
                newPin = "654321"
            )
            assertTrue(resetResult.isSuccess)

            val newRecoveryCode = resetResult.getOrNull()
            assertNotNull(newRecoveryCode)
            assertNotEquals(DemoSeed.ADMIN_RECOVERY_CODE, newRecoveryCode)

            // New PIN verifies
            assertTrue(userRepository.verifyPin(admin.userId, "654321"))
            assertFalse(userRepository.verifyPin(admin.userId, DemoSeed.ADMIN_PIN))

            // Old recovery code must now fail
            val retryOldCode = userRepository.resetAdminPinWithRecoveryCode(
                userId = admin.userId,
                recoveryCode = DemoSeed.ADMIN_RECOVERY_CODE,
                newPin = "111111"
            )
            assertTrue(retryOldCode.isFailure)

            // New recovery code succeeds
            val retryNewCode = userRepository.resetAdminPinWithRecoveryCode(
                userId = admin.userId,
                recoveryCode = newRecoveryCode!!,
                newPin = "111111"
            )
            assertTrue(retryNewCode.isSuccess)
        }
    }
}