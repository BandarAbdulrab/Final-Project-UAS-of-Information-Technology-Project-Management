package com.byteall.kharismart.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["receipt_no"], unique = true),
        Index(value = ["date"]),
        Index(value = ["user_id"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "transaction_id")
    val transactionId: Long = 0,

    @ColumnInfo(name = "user_id")
    val userId: Long,

    @ColumnInfo(name = "receipt_no")
    val receiptNo: String,

    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "method")
    val method: String,

    @ColumnInfo(name = "total")
    val total: Long,

    @ColumnInfo(name = "paid")
    val paid: Long,

    @ColumnInfo(name = "change_due")
    val changeDue: Long,

    @ColumnInfo(name = "total_cost")
    val totalCost: Long,

    @ColumnInfo(name = "gross_profit")
    val grossProfit: Long,

    @ColumnInfo(name = "note")
    val note: String? = null
)
