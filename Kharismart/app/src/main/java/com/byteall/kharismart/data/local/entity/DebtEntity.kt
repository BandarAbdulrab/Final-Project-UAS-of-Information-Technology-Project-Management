package com.byteall.kharismart.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "debts",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["transaction_id"],
            childColumns = ["transaction_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["transaction_id"], unique = true),
        Index(value = ["status"]),
        Index(value = ["customer_name"]),
        Index(value = ["user_id"])
    ]
)
data class DebtEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "debt_id")
    val debtId: Long = 0,

    @ColumnInfo(name = "transaction_id")
    val transactionId: Long? = null,

    @ColumnInfo(name = "user_id")
    val userId: Long,

    @ColumnInfo(name = "customer_name")
    val customerName: String,

    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "total_amount")
    val totalAmount: Long,

    @ColumnInfo(name = "paid_amount", defaultValue = "0")
    val paidAmount: Long = 0,

    @ColumnInfo(name = "status", defaultValue = "unpaid")
    val status: String = "unpaid",

    @ColumnInfo(name = "note")
    val note: String? = null
)
