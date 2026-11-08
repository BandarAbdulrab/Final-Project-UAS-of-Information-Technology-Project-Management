package com.byteall.kharismart.data.local.model

import androidx.room.ColumnInfo

data class CategorySales(
    @ColumnInfo(name = "category_name")
    val categoryName: String,

    @ColumnInfo(name = "revenue")
    val revenue: Long,

    @ColumnInfo(name = "gross_profit")
    val grossProfit: Long,

    @ColumnInfo(name = "units")
    val units: Int
)
