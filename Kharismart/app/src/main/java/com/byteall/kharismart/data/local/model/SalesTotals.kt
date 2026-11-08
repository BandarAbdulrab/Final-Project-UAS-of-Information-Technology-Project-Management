package com.byteall.kharismart.data.local.model

import androidx.room.ColumnInfo

data class SalesTotals(
    @ColumnInfo(name = "revenue")
    val revenue: Long,

    @ColumnInfo(name = "gross_profit")
    val grossProfit: Long,

    @ColumnInfo(name = "cash_sales")
    val cashSales: Long,

    @ColumnInfo(name = "qris_sales")
    val qrisSales: Long,

    @ColumnInfo(name = "credit_sales")
    val creditSales: Long,

    @ColumnInfo(name = "transaction_count")
    val transactionCount: Int
)
