package com.byteall.kharismart.data.local.model

import androidx.room.ColumnInfo

data class MethodTotals(
    @ColumnInfo(name = "cash")
    val cash: Long,

    @ColumnInfo(name = "qris")
    val qris: Long
)
