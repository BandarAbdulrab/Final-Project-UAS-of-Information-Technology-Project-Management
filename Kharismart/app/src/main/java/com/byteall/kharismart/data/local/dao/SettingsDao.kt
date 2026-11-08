package com.byteall.kharismart.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import com.byteall.kharismart.data.local.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE settings_id = 1")
    fun observeSettings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE settings_id = 1")
    suspend fun getSettings(): SettingsEntity?

    @Update
    suspend fun update(settings: SettingsEntity)

    @Query("UPDATE settings SET last_backup_at = :lastBackupAt, backup_folder = :backupFolder WHERE settings_id = 1")
    suspend fun updateLastBackup(lastBackupAt: String, backupFolder: String?)
}
