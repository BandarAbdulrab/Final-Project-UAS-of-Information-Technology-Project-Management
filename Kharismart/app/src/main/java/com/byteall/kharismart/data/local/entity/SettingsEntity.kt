package com.byteall.kharismart.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "settings_id")
    val settingsId: Long = 1,

    @ColumnInfo(name = "store_name")
    val storeName: String,

    @ColumnInfo(name = "store_address")
    val storeAddress: String? = null,

    @ColumnInfo(name = "receipt_footer")
    val receiptFooter: String? = null,

    @ColumnInfo(name = "default_min_stock", defaultValue = "5")
    val defaultMinStock: Int = 5,

    @ColumnInfo(name = "qris_bank")
    val qrisBank: String? = null,

    @ColumnInfo(name = "qris_owner_name")
    val qrisOwnerName: String? = null,

    @ColumnInfo(name = "qris_image")
    val qrisImage: String? = null,

    @ColumnInfo(name = "target_daily")
    val targetDaily: Long? = null,

    @ColumnInfo(name = "target_weekly")
    val targetWeekly: Long? = null,

    @ColumnInfo(name = "target_monthly")
    val targetMonthly: Long? = null,

    @ColumnInfo(name = "auto_lock_minutes", defaultValue = "5")
    val autoLockMinutes: Int = 5,

    @ColumnInfo(name = "backup_folder")
    val backupFolder: String? = null,

    @ColumnInfo(name = "last_backup_at")
    val lastBackupAt: String? = null
)
