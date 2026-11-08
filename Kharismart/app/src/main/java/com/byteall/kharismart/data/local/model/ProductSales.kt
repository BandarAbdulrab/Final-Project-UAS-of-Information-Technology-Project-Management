package com.byteall.kharismart.data.local.model

import androidx.room.ColumnInfo

data class ProductSales(
    @ColumnInfo(name = "product_id")
    val productId: Long,

    @ColumnInfo(name = "product_name")
    val productName: String,

    @ColumnInfo(name = "units")
    val units: Int,

    @ColumnInfo(name = "revenue")
    val revenue: Long,

    @ColumnInfo(name = "gross_profit")
    val grossProfit: Long
)
