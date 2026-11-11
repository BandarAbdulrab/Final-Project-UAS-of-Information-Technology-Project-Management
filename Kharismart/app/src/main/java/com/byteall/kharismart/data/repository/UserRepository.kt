package com.byteall.kharismart.data.repository

import com.byteall.kharismart.data.local.Role
import com.byteall.kharismart.data.local.dao.UserDao
import com.byteall.kharismart.data.local.entity.UserEntity
import com.byteall.kharismart.util.DomainException
import com.byteall.kharismart.util.security.PasswordHasher
import kotlinx.coroutines.flow.Flow

class UserRepository(private val userDao: UserDao) {

    fun observeActiveUsers(): Flow<List<UserEntity>> = userDao.observeActiveUsers()

    fun observeAllUsers(): Flow<List<UserEntity>> = userDao.observeAllUsers()

    suspend fun getUser(userId: Long): UserEntity? = userDao.getById(userId)

    suspend fun verifyPin(userId: Long, pin: String): Boolean {
        val user = userDao.getById(userId) ?: return false
        if (user.status != 1) return false
        return PasswordHasher.verifyPin(pin, user.pinHash)
    }

    suspend fun addUser(name: String, username: String, pin: String, role: String): Result<Long> {
        val cleanName = name.trim()
        val cleanUsername = username.trim()

        if (cleanName.isBlank()) {
            return Result.failure(DomainException("Nama pengguna tidak boleh kosong"))
        }
        if (cleanUsername.isBlank()) {
            return Result.failure(DomainException("Username tidak boleh kosong"))
        }
        if (userDao.getByUsername(cleanUsername) != null) {
            return Result.failure(DomainException("Username '$cleanUsername' sudah digunakan"))
        }
        if (!Regex("^\\d{4,6}$").matches(pin)) {
            return Result.failure(DomainException("PIN harus berupa 4 hingga 6 digit angka"))
        }
        if (role != Role.ADMIN && role != Role.CASHIER) {
            return Result.failure(DomainException("Peran (role) pengguna tidak valid"))
        }

        val pinHash = PasswordHasher.hashPin(pin)
        val entity = UserEntity(
            username = cleanUsername,
            pinHash = pinHash,
            name = cleanName,
            role = role,
            status = 1,
            recoveryHash = null
        )
        val newId = userDao.insert(entity)
        return Result.success(newId)
    }

    suspend fun resetPin(userId: Long, newPin: String): Result<Unit> {
        if (!Regex("^\\d{4,6}$").matches(newPin)) {
            return Result.failure(DomainException("PIN baru harus berupa 4 hingga 6 digit angka"))
        }
        val user = userDao.getById(userId)
            ?: return Result.failure(DomainException("Pengguna tidak ditemukan"))
        userDao.updatePinHash(user.userId, PasswordHasher.hashPin(newPin))
        return Result.success(Unit)
    }

    suspend fun setActive(userId: Long, active: Boolean): Result<Unit> {
        val user = userDao.getById(userId)
            ?: return Result.failure(DomainException("Pengguna tidak ditemukan"))

        if (!active && user.role == Role.ADMIN && user.status == 1) {
            val activeAdmins = userDao.countActiveAdmins()
            if (activeAdmins <= 1) {
                return Result.failure(DomainException("Tidak dapat menonaktifkan satu-satunya admin yang aktif"))
            }
        }

        userDao.updateStatus(userId, if (active) 1 else 0)
        return Result.success(Unit)
    }

    suspend fun changeOwnPin(userId: Long, currentPin: String, newPin: String): Result<Unit> {
        if (!verifyPin(userId, currentPin)) {
            return Result.failure(DomainException("PIN saat ini salah"))
        }
        if (!Regex("^\\d{4,6}$").matches(newPin)) {
            return Result.failure(DomainException("PIN baru harus berupa 4 hingga 6 digit angka"))
        }
        userDao.updatePinHash(userId, PasswordHasher.hashPin(newPin))
        return Result.success(Unit)
    }

    suspend fun resetAdminPinWithRecoveryCode(
        userId: Long,
        recoveryCode: String,
        newPin: String
    ): Result<String> {
        val user = userDao.getById(userId)
            ?: return Result.failure(DomainException("Pengguna tidak ditemukan"))

        if (user.role != Role.ADMIN || user.status != 1 || user.recoveryHash == null) {
            return Result.failure(DomainException("Akun admin tidak valid atau tidak memiliki kode pemulihan"))
        }

        if (!PasswordHasher.verifyRecoveryCode(recoveryCode, user.recoveryHash)) {
            return Result.failure(DomainException("Kode pemulihan tidak cocok"))
        }

        if (!Regex("^\\d{4,6}$").matches(newPin)) {
            return Result.failure(DomainException("PIN baru harus berupa 4 hingga 6 digit angka"))
        }

        val newRecoveryCode = PasswordHasher.generateRecoveryCode()
        val newRecoveryHash = PasswordHasher.hashRecoveryCode(newRecoveryCode)
        val newPinHash = PasswordHasher.hashPin(newPin)

        userDao.updatePinHash(userId, newPinHash)
        userDao.updateRecoveryHash(userId, newRecoveryHash)

        return Result.success(newRecoveryCode)
    }

    suspend fun regenerateRecoveryCode(adminUserId: Long, currentPin: String): Result<String> {
        val user = userDao.getById(adminUserId)
            ?: return Result.failure(DomainException("Pengguna tidak ditemukan"))

        if (user.role != Role.ADMIN || user.status != 1) {
            return Result.failure(DomainException("Hanya admin aktif yang dapat memperbarui kode pemulihan"))
        }

        if (!PasswordHasher.verifyPin(currentPin, user.pinHash)) {
            return Result.failure(DomainException("PIN admin salah"))
        }

        val newCode = PasswordHasher.generateRecoveryCode()
        userDao.updateRecoveryHash(adminUserId, PasswordHasher.hashRecoveryCode(newCode))
        return Result.success(newCode)
    }
}