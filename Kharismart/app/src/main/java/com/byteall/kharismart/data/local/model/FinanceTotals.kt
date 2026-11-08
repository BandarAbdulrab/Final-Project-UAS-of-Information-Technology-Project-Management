package com.byteall.kharismart.data.local.model

import androidx.room.ColumnInfo

data class FinanceTotals(
    @ColumnInfo(name = "income")
    val income: Long,

    @ColumnInfo(name = "expense")
    val expense: Long
)
