package com.byteall.kharismart.data.repository

import com.byteall.kharismart.data.local.dao.SettingsDao
import com.byteall.kharismart.data.local.entity.SettingsEntity
import com.byteall.kharismart.util.DomainException
import com.byteall.kharismart.util.time.WibClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val settingsDao: SettingsDao) {

    private val defaultFallback = SettingsEntity(
        settingsId = 1,
        storeName = "Toko Kharisma",
        receiptFooter = "Thank you",
        defaultMinStock = 5,
        autoLockMinutes = 5
    )

    fun observeSettings(): Flow<SettingsEntity> {
        return settingsDao.observeSettings().map { it ?: defaultFallback }
    }

    suspend fun getSettings(): SettingsEntity {
        return settingsDao.getSettings() ?: defaultFallback
    }

    suspend fun updateStoreIdentity(name: String, address: String?, footer: String?): Result<Unit> {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            return Result.failure(DomainException("Nama toko tidak boleh kosong"))
        }
        val current = getSettings()
        settingsDao.update(
            current.copy(
                storeName = cleanName,
                storeAddress = address?.trim()?.ifBlank { null },
                receiptFooter = footer?.trim()?.ifBlank { null }
            )
        )
        return Result.success(Unit)
    }

    suspend fun updateOperational(defaultMinStock: Int, autoLockMinutes: Int): Result<Unit> {
        if (defaultMinStock < 0) {
            return Result.failure(DomainException("Batas stok minimum tidak boleh negatif"))
        }
        if (autoLockMinutes !in 1..60) {
            return Result.failure(DomainException("Waktu kunci otomatis harus antara 1 sampai 60 menit"))
        }
        val current = getSettings()
        settingsDao.update(
            current.copy(
                defaultMinStock = defaultMinStock,
                autoLockMinutes = autoLockMinutes
            )
        )
        return Result.success(Unit)
    }

    suspend fun updateQris(bank: String?, ownerName: String?, imageFilename: String?): Result<Unit> {
        val current = getSettings()
        settingsDao.update(
            current.copy(
                qrisBank = bank?.trim()?.ifBlank { null },
                qrisOwnerName = ownerName?.trim()?.ifBlank { null },
                qrisImage = imageFilename?.trim()?.ifBlank { null }
            )
        )
        return Result.success(Unit)
    }

    suspend fun updateTargets(daily: Long?, weekly: Long?, monthly: Long?): Result<Unit> {
        if ((daily != null && daily < 0) ||
            (weekly != null && weekly < 0) ||
            (monthly != null && monthly < 0)
        ) {
            return Result.failure(DomainException("Target pendapatan tidak boleh bernilai negatif"))
        }
        val current = getSettings()
        settingsDao.update(
            current.copy(
                targetDaily = daily,
                targetWeekly = weekly,
                targetMonthly = monthly
            )
        )
        return Result.success(Unit)
    }

    suspend fun recordBackup(folder: String?) {
        settingsDao.updateLastBackup(WibClock.nowString(), folder?.trim()?.ifBlank { null })
    }
}