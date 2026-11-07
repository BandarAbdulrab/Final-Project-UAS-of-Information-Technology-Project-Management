package com.byteall.kharismart.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["username"], unique = true)
    ]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "user_id")
    val userId: Long = 0,

    @ColumnInfo(name = "username")
    val username: String,

    @ColumnInfo(name = "pin_hash")
    val pinHash: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "role")
    val role: String,

    @ColumnInfo(name = "status", defaultValue = "1")
    val status: Int = 1,

    @ColumnInfo(name = "recovery_hash")
    val recoveryHash: String? = null
)
