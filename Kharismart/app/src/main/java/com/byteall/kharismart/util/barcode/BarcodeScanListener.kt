package com.byteall.kharismart.util.barcode

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint

class BarcodeScanBuffer(
    private val maxInterKeyIntervalMs: Long = 60L,
    private val minLength: Int = 3,
    private val onBarcodeScanned: (String) -> Unit
) {
    private val buffer = StringBuilder()
    private var lastKeyTimeMs: Long = 0L

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false

        val now = System.currentTimeMillis()
        if (lastKeyTimeMs > 0L && (now - lastKeyTimeMs) > maxInterKeyIntervalMs) {
            buffer.clear()
        }
        lastKeyTimeMs = now

        if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
            val barcode = buffer.toString().trim()
            buffer.clear()
            if (barcode.length >= minLength) {
                onBarcodeScanned(barcode)
                return true
            }
            return false
        }

        val char = event.utf16CodePoint.toChar()
        if (char.isLetterOrDigit() || char in "-_./") {
            buffer.append(char)
        }
        return false
    }

    fun clear() {
        buffer.clear()
    }
}

fun Modifier.interceptBarcodeKeyEvents(
    onBarcodeScanned: (String) -> Unit
): Modifier = composed {
    val buffer = remember(onBarcodeScanned) { BarcodeScanBuffer(onBarcodeScanned = onBarcodeScanned) }
    this.onKeyEvent { event ->
        buffer.handleKeyEvent(event)
    }
}