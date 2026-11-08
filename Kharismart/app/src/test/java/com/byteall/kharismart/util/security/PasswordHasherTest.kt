package com.byteall.kharismart.util.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun `hashPin generates valid salted hash and verifyPin succeeds with correct pin`() {
        val pin = "123456"
        val hash = PasswordHasher.hashPin(pin)

        val parts = hash.split(":")
        assertEquals(2, parts.size)
        assertEquals(32, parts[0].length) // 16 bytes = 32 hex chars
        assertEquals(64, parts[1].length) // 256 bits = 32 bytes = 64 hex chars

        assertTrue(PasswordHasher.verifyPin(pin, hash))
        assertFalse(PasswordHasher.verifyPin("654321", hash))
        assertFalse(PasswordHasher.verifyPin("12345", hash))
    }

    @Test
    fun `two hashes of same pin produce different salts and hashes`() {
        val pin = "4321"
        val hash1 = PasswordHasher.hashPin(pin)
        val hash2 = PasswordHasher.hashPin(pin)

        assertNotEquals(hash1, hash2)
        assertTrue(PasswordHasher.verifyPin(pin, hash1))
        assertTrue(PasswordHasher.verifyPin(pin, hash2))
    }

    @Test
    fun `verifyPin returns false without throwing on malformed hashes`() {
        assertFalse(PasswordHasher.verifyPin("1234", ""))
        assertFalse(PasswordHasher.verifyPin("1234", "not-a-valid-hash"))
        assertFalse(PasswordHasher.verifyPin("1234", "invalid_salt:invalid_hash"))
        assertFalse(PasswordHasher.verifyPin("1234", ":"))
        assertFalse(PasswordHasher.verifyPin("1234", "abc:"))
        assertFalse(PasswordHasher.verifyPin("1234", ":abc"))
        assertFalse(PasswordHasher.verifyPin("1234", "1234:5678:9012"))
    }

    @Test
    fun `recovery code verification is case-insensitive and trims whitespace`() {
        val code = "KHARISMA-DEMO-2026"
        val hash = PasswordHasher.hashRecoveryCode(code)

        assertTrue(PasswordHasher.verifyRecoveryCode("kharisma-demo-2026", hash))
        assertTrue(PasswordHasher.verifyRecoveryCode("  KHARISMA-DEMO-2026  ", hash))
        assertTrue(PasswordHasher.verifyRecoveryCode("Kharisma-Demo-2026", hash))
        assertFalse(PasswordHasher.verifyRecoveryCode("WRONG-CODE-2026", hash))
    }

    @Test
    fun `generateRecoveryCode produces 14 char formatted string with unambiguous chars`() {
        val forbiddenChars = setOf('0', 'O', '1', 'I')
        repeat(20) {
            val code = PasswordHasher.generateRecoveryCode()
            val parts = code.split("-")
            assertEquals(3, parts.size)
            assertEquals(4, parts[0].length)
            assertEquals(4, parts[1].length)
            assertEquals(4, parts[2].length)

            for (ch in code) {
                if (ch != '-') {
                    assertFalse("Code contains forbidden char $ch: $code", forbiddenChars.contains(ch))
                }
            }
        }
    }
}
