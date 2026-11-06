package com.byteall.kharismart.util.format

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import kotlin.math.abs

fun Long.toRupiah(): String {
    val symbols = DecimalFormatSymbols().apply {
        groupingSeparator = '.'
    }
    val formatter = DecimalFormat("#,##0", symbols)
    return if (this < 0) {
        "-Rp${formatter.format(abs(this))}"
    } else {
        "Rp${formatter.format(this)}"
    }
}

fun String.digitsToLongOrNull(): Long? {
    val digits = this.filter { it.isDigit() }
    return if (digits.isEmpty()) null else digits.toLongOrNull()
}