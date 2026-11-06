package com.byteall.kharismart.util.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RupiahFormatterTest {

    @Test
    fun testToRupiahFormatting() {
        assertEquals("Rp0", 0L.toRupiah())
        assertEquals("Rp999", 999L.toRupiah())
        assertEquals("Rp1.000", 1000L.toRupiah())
        assertEquals("Rp14.000", 14000L.toRupiah())
        assertEquals("Rp1.234.567", 1234567L.toRupiah())
        assertEquals("-Rp14.000", (-14000L).toRupiah())
    }

    @Test
    fun testDigitsToLongOrNull() {
        assertEquals(14000L, "Rp14.000".digitsToLongOrNull())
        assertEquals(0L, "0".digitsToLongOrNull())
        assertEquals(123456L, "ab12c34d56".digitsToLongOrNull())
        assertNull("".digitsToLongOrNull())
        assertNull("abc".digitsToLongOrNull())
        assertNull("9999999999999999999999999".digitsToLongOrNull())
    }
}