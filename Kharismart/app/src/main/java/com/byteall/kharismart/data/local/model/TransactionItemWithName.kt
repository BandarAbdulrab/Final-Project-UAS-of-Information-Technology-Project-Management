package com.byteall.kharismart.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.byteall.kharismart.data.local.entity.TransactionItemEntity

data class TransactionItemWithName(
    @Embedded
    val item: TransactionItemEntity,

    @ColumnInfo(name = "product_name")
    val productName: String
)
