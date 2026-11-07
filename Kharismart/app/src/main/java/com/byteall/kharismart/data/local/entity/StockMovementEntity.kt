package com.byteall.kharismart.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stock_movements",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["product_id"],
            childColumns = ["product_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["transaction_id"],
            childColumns = ["transaction_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["product_id", "date"]),
        Index(value = ["transaction_id"]),
        Index(value = ["user_id"])
    ]
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "movement_id")
    val movementId: Long = 0,

    @ColumnInfo(name = "product_id")
    val productId: Long,

    @ColumnInfo(name = "user_id")
    val userId: Long,

    @ColumnInfo(name = "transaction_id")
    val transactionId: Long? = null,

    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "quantity_change")
    val quantityChange: Int,

    @ColumnInfo(name = "stock_after")
    val stockAfter: Int,

    @ColumnInfo(name = "unit_cost")
    val unitCost: Long? = null,

    @ColumnInfo(name = "total_cost")
    val totalCost: Long? = null,

    @ColumnInfo(name = "note")
    val note: String? = null
)
