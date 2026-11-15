package com.byteall.kharismart.util.barcode

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint

/**
 * Buffer that accumulates rapid keystrokes from USB / Bluetooth HID hardware barcode scanners
 * and dispatches the complete barcode string upon encountering an Enter key event.
 */
class BarcodeScanBuffer(
    private val onBarcodeScanned: (String) -> Unit
) {
    private val buffer = StringBuilder()

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false

        if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
            val barcode = buffer.toString().trim()
            buffer.clear()
            if (barcode.isNotEmpty()) {
                onBarcodeScanned(barcode)
                return true
            }
            return false
        }

        val char = event.utf16CodePoint.toChar()
        if (char.isLetterOrDigit() || char in "-_./") {
            buffer.append(char)
            return false
        }
        return false
    }

    fun clear() {
        buffer.clear()
    }
}

/**
 * Modifier extension to intercept hardware HID barcode scanner key events on any focusable Compose hierarchy.
 */
fun Modifier.interceptBarcodeKeyEvents(
    onBarcodeScanned: (String) -> Unit
): Modifier {
    val buffer = BarcodeScanBuffer(onBarcodeScanned)
    return this.onKeyEvent { event ->
        buffer.handleKeyEvent(event)
    }
}
