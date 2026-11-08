package com.byteall.kharismart.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.byteall.kharismart.data.local.entity.TransactionEntity

data class TransactionWithCashier(
    @Embedded
    val transaction: TransactionEntity,

    @ColumnInfo(name = "cashier_name")
    val cashierName: String
)
