package com.byteall.kharismart.util.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2WithHmacSHA256 password & recovery code hasher.
 * Generates salted hashes and performs constant-time verification.
 */
object PasswordHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 10_000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16

    // Unambiguous character set (no 0, O, 1, I)
    private const val RECOVERY_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    private val secureRandom = SecureRandom()

    /**
     * Hashes a user PIN using PBKDF2WithHmacSHA256 with 10,000 iterations and a random 16-byte salt.
     * Returns a string in the format "saltHex:hashHex".
     */
    fun hashPin(pin: String): String {
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)
        val hash = pbkdf2(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        return "${salt.toHex()}:${hash.toHex()}"
    }

    /**
     * Verifies a user PIN against a stored "saltHex:hashHex" string in constant time.
     * Returns false (never throws) for malformed stored hash strings.
     */
    fun verifyPin(pin: String, storedHash: String): Boolean {
        return try {
            val parts = storedHash.split(":")
            if (parts.size != 2) return false

            val salt = hexToBytes(parts[0])
            val expectedHash = hexToBytes(parts[1])
            if (salt.isEmpty() || expectedHash.isEmpty()) return false

            val computedHash = pbkdf2(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
            MessageDigest.isEqual(computedHash, expectedHash)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Hashes a recovery code after trimming and upper-casing.
     */
    fun hashRecoveryCode(code: String): String {
        val normalized = code.trim().uppercase()
        return hashPin(normalized)
    }

    /**
     * Verifies a recovery code against a stored hash after trimming and upper-casing.
     * Returns false (never throws) for malformed stored hash strings.
     */
    fun verifyRecoveryCode(code: String, storedHash: String): Boolean {
        val normalized = code.trim().uppercase()
        return verifyPin(normalized, storedHash)
    }

    /**
     * Generates a random recovery code in the format "XXXX-XXXX-XXXX" using unambiguous characters.
     */
    fun generateRecoveryCode(): String {
        fun randomBlock(length: Int): String {
            val sb = java.lang.StringBuilder(length)
            for (i in 0 until length) {
                val index = secureRandom.nextInt(RECOVERY_ALPHABET.length)
                sb.append(RECOVERY_ALPHABET[index])
            }
            return sb.toString()
        }

        return "${randomBlock(4)}-${randomBlock(4)}-${randomBlock(4)}"
    }

    private fun pbkdf2(chars: CharArray, salt: ByteArray, iterations: Int, keyLengthBits: Int): ByteArray {
        val spec = PBEKeySpec(chars, salt, iterations, keyLengthBits)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        return factory.generateSecret(spec).encoded
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        if (hex.length % 2 != 0) return ByteArray(0)
        val bytes = ByteArray(hex.length / 2)
        for (i in bytes.indices) {
            val index = i * 2
            bytes[i] = hex.substring(index, index + 2).toInt(16).toByte()
        }
        return bytes
    }
}
