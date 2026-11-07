package com.byteall.kharismart.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["category_id"],
            childColumns = ["category_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["barcode"], unique = true),
        Index(value = ["category_id"]),
        Index(value = ["name"])
    ]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "product_id")
    val productId: Long = 0,

    @ColumnInfo(name = "barcode")
    val barcode: String? = null,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "category_id")
    val categoryId: Long,

    @ColumnInfo(name = "price")
    val price: Long,

    @ColumnInfo(name = "cost_price", defaultValue = "0")
    val costPrice: Long = 0,

    @ColumnInfo(name = "stock", defaultValue = "0")
    val stock: Int = 0,

    @ColumnInfo(name = "min_stock")
    val minStock: Int? = null,

    @ColumnInfo(name = "photo")
    val photo: String? = null,

    @ColumnInfo(name = "status", defaultValue = "1")
    val status: Int = 1
)
