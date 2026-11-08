package com.byteall.kharismart.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.byteall.kharismart.data.local.entity.DebtEntity

data class DebtWithOrigin(
    @Embedded
    val debt: DebtEntity,

    @ColumnInfo(name = "receipt_no")
    val receiptNo: String?
)
