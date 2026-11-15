package com.byteall.kharismart.util.printer

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.byteall.kharismart.data.local.PaymentMethod
import com.byteall.kharismart.data.repository.ReceiptData
import com.byteall.kharismart.util.format.toRupiah
import com.byteall.kharismart.util.time.WibClock

/**
 * Presentation model for a line item on the printed/displayed receipt.
 */
data class ReceiptItemUiModel(
    val name: String,
    val quantity: Int,
    val price: Long,
    val subtotal: Long
)

/**
 * Presentation model for the complete thermal receipt (SRS-F09, F10).
 */
data class ReceiptUiModel(
    val transactionId: Long,
    val receiptNo: String,
    val dateFormatted: String,
    val storeName: String,
    val storeAddress: String?,
    val cashierName: String,
    val items: List<ReceiptItemUiModel>,
    val total: Long,
    val paymentMethod: String,
    val paid: Long,
    val changeDue: Long,
    val customerName: String? = null,
    val remainingDebt: Long? = null,
    val footer: String? = null,
    val isReprint: Boolean = false
) {
    companion object {
        fun fromReceiptData(data: ReceiptData, isReprint: Boolean = false): ReceiptUiModel {
            val trx = data.transaction
            val debt = data.debt
            val remaining = if (debt != null) debt.totalAmount - debt.paidAmount else null

            return ReceiptUiModel(
                transactionId = trx.transactionId,
                receiptNo = trx.receiptNo,
                dateFormatted = WibClock.displayDateTime(trx.date),
                storeName = data.settings.storeName,
                storeAddress = data.settings.storeAddress,
                cashierName = data.cashierName,
                items = data.items.map { itemWithName ->
                    ReceiptItemUiModel(
                        name = itemWithName.productName,
                        quantity = itemWithName.item.quantity,
                        price = itemWithName.item.price,
                        subtotal = itemWithName.item.subtotal
                    )
                },
                total = trx.total,
                paymentMethod = trx.method,
                paid = trx.paid,
                changeDue = trx.changeDue,
                customerName = debt?.customerName,
                remainingDebt = remaining,
                footer = data.settings.receiptFooter,
                isReprint = isReprint
            )
        }
    }
}

/**
 * Thermal Receipt, Printing Adapter & Plain Text Generator (SRS-F09, F10).
 *
 * Capabilities:
 * 1. Monospaced Plain-Text generation: 32-column formatted for WhatsApp/SMS sharing.
 * 2. Thermal HTML styling: 58mm/80mm receipt styling compatible with Android WebView print adapter.
 * 3. Printing: Sends to Android [PrintManager] for Bluetooth/USB thermal printers or Save-as-PDF.
 * 4. Sharing: Dispatches [Intent.ACTION_SEND] text stream.
 */
object ReceiptPrinter {

    private const val LINE_WIDTH = 32

    /**
     * Formats a clean 32-character monospaced plain-text receipt for messaging or Bluetooth printers.
     */
    fun generatePlainTextReceipt(receipt: ReceiptUiModel): String {
        val sb = StringBuilder()
        val sepDouble = "=".repeat(LINE_WIDTH)
        val sepSingle = "-".repeat(LINE_WIDTH)

        sb.appendLine(sepDouble)
        if (receipt.isReprint) {
            sb.appendLine(centerText("[ SALINAN / REPRINT ]", LINE_WIDTH))
        }
        sb.appendLine(centerText(receipt.storeName.uppercase(), LINE_WIDTH))
        receipt.storeAddress?.let {
            if (it.isNotBlank()) {
                sb.appendLine(centerText(it, LINE_WIDTH))
            }
        }
        sb.appendLine(sepDouble)

        sb.appendLine("No. Nota : ${receipt.receiptNo}")
        sb.appendLine("Waktu    : ${receipt.dateFormatted} WIB")
        sb.appendLine("Kasir    : ${receipt.cashierName}")
        sb.appendLine(sepSingle)

        for (item in receipt.items) {
            sb.appendLine(item.name)
            val left = "  ${item.quantity} x ${item.price.toRupiah()}"
            val right = item.subtotal.toRupiah()
            sb.appendLine(justifyText(left, right, LINE_WIDTH))
        }

        sb.appendLine(sepSingle)
        sb.appendLine(justifyText("TOTAL BELANJA :", receipt.total.toRupiah(), LINE_WIDTH))

        when (receipt.paymentMethod) {
            PaymentMethod.CASH -> {
                sb.appendLine(justifyText("Metode Bayar  :", "Tunai", LINE_WIDTH))
                sb.appendLine(justifyText("Uang Diterima :", receipt.paid.toRupiah(), LINE_WIDTH))
                sb.appendLine(justifyText("Kembalian     :", receipt.changeDue.toRupiah(), LINE_WIDTH))
            }
            PaymentMethod.QRIS -> {
                sb.appendLine(justifyText("Metode Bayar  :", "QRIS", LINE_WIDTH))
                sb.appendLine(justifyText("Status Bayar  :", "LUNAS", LINE_WIDTH))
            }
            PaymentMethod.CREDIT -> {
                sb.appendLine(justifyText("Metode Bayar  :", "Utang (Kredit)", LINE_WIDTH))
                receipt.customerName?.let {
                    sb.appendLine(justifyText("Pelanggan     :", it, LINE_WIDTH))
                }
                sb.appendLine(justifyText("Dibayar       :", "Rp0", LINE_WIDTH))
                receipt.remainingDebt?.let {
                    sb.appendLine(justifyText("Sisa Utang    :", it.toRupiah(), LINE_WIDTH))
                }
            }
            else -> {
                sb.appendLine(justifyText("Metode Bayar  :", receipt.paymentMethod.uppercase(), LINE_WIDTH))
            }
        }

        sb.appendLine(sepSingle)
        val footerText = receipt.footer?.ifBlank { null } ?: "Terima Kasih Atas Kunjungan Anda"
        sb.appendLine(centerText(footerText, LINE_WIDTH))
        sb.appendLine(centerText("Barang yang dibeli tidak dapat ditukar", LINE_WIDTH))
        sb.appendLine(sepDouble)

        return sb.toString()
    }

    /**
     * Builds an HTML document with 58mm thermal receipt styling for Android WebView print rendering.
     */
    fun generateHtmlReceipt(receipt: ReceiptUiModel): String {
        val methodLabel = when (receipt.paymentMethod) {
            PaymentMethod.CASH -> "Tunai (Cash)"
            PaymentMethod.QRIS -> "QRIS"
            PaymentMethod.CREDIT -> "Utang (Kredit)"
            else -> receipt.paymentMethod.uppercase()
        }

        val reprintBanner = if (receipt.isReprint) {
            """<div class="reprint-badge">[ SALINAN / REPRINT ]</div>"""
        } else ""

        val itemsHtml = receipt.items.joinToString("\n") { item ->
            """
            <tr>
                <td colspan="2" class="item-name">${escapeHtml(item.name)}</td>
            </tr>
            <tr class="item-line">
                <td class="item-calc">&nbsp;&nbsp;${item.quantity} x ${item.price.toRupiah()}</td>
                <td class="item-subtotal">${item.subtotal.toRupiah()}</td>
            </tr>
            """.trimIndent()
        }

        val paymentDetailsHtml = when (receipt.paymentMethod) {
            PaymentMethod.CASH -> """
                <tr><td>Uang Diterima:</td><td class="text-right">${receipt.paid.toRupiah()}</td></tr>
                <tr><td>Kembalian:</td><td class="text-right"><strong>${receipt.changeDue.toRupiah()}</strong></td></tr>
            """.trimIndent()
            PaymentMethod.QRIS -> """
                <tr><td>Status Pembayaran:</td><td class="text-right"><strong>LUNAS</strong></td></tr>
            """.trimIndent()
            PaymentMethod.CREDIT -> """
                <tr><td>Pelanggan:</td><td class="text-right"><strong>${escapeHtml(receipt.customerName ?: "-")}</strong></td></tr>
                <tr><td>Dibayar:</td><td class="text-right">Rp0</td></tr>
                <tr><td>Sisa Utang:</td><td class="text-right"><strong style="color: #c00;">${(receipt.remainingDebt ?: receipt.total).toRupiah()}</strong></td></tr>
            """.trimIndent()
            else -> ""
        }

        val footerText = escapeHtml(receipt.footer?.ifBlank { null } ?: "Terima Kasih Atas Kunjungan Anda")

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <title>Struk - ${receipt.receiptNo}</title>
            <style>
                @page {
                    margin: 0;
                    size: 58mm auto;
                }
                body {
                    font-family: 'Courier New', Courier, monospace;
                    font-size: 11px;
                    line-height: 1.3;
                    color: #000;
                    background: #fff;
                    margin: 0;
                    padding: 8px 6px;
                    width: 58mm;
                    box-sizing: border-box;
                }
                .text-center { text-align: center; }
                .text-right { text-align: right; }
                .reprint-badge {
                    text-align: center;
                    font-weight: bold;
                    font-size: 12px;
                    border: 1px dashed #333;
                    padding: 3px;
                    margin-bottom: 6px;
                }
                .store-title {
                    font-size: 14px;
                    font-weight: bold;
                    text-transform: uppercase;
                    margin: 0;
                }
                .store-addr {
                    font-size: 10px;
                    margin: 2px 0;
                }
                .divider {
                    border-top: 1px dashed #000;
                    margin: 6px 0;
                }
                .divider-double {
                    border-top: 2px solid #000;
                    margin: 6px 0;
                }
                .meta-table, .calc-table, .items-table {
                    width: 100%;
                    border-collapse: collapse;
                }
                .meta-table td, .calc-table td {
                    padding: 1px 0;
                    font-size: 10.5px;
                }
                .item-name {
                    font-weight: bold;
                    padding-top: 3px;
                }
                .item-calc {
                    font-size: 10px;
                    color: #222;
                }
                .item-subtotal {
                    text-align: right;
                    vertical-align: bottom;
                }
                .total-row td {
                    font-size: 13px;
                    font-weight: bold;
                    padding-top: 4px;
                    padding-bottom: 4px;
                }
                .footer {
                    margin-top: 8px;
                    font-size: 10px;
                    text-align: center;
                }
            </style>
        </head>
        <body>
            <div class="divider-double"></div>
            $reprintBanner
            <div class="text-center">
                <div class="store-title">${escapeHtml(receipt.storeName.uppercase())}</div>
                ${if (!receipt.storeAddress.isNullOrBlank()) """<div class="store-addr">${escapeHtml(receipt.storeAddress)}</div>""" else ""}
            </div>
            <div class="divider-double"></div>

            <table class="meta-table">
                <tr><td>No. Nota</td><td>: ${receipt.receiptNo}</td></tr>
                <tr><td>Waktu</td><td>: ${receipt.dateFormatted} WIB</td></tr>
                <tr><td>Kasir</td><td>: ${escapeHtml(receipt.cashierName)}</td></tr>
            </table>

            <div class="divider"></div>

            <table class="items-table">
                $itemsHtml
            </table>

            <div class="divider"></div>

            <table class="calc-table">
                <tr class="total-row">
                    <td>TOTAL</td>
                    <td class="text-right">${receipt.total.toRupiah()}</td>
                </tr>
                <tr>
                    <td>Metode Bayar:</td>
                    <td class="text-right">$methodLabel</td>
                </tr>
                $paymentDetailsHtml
            </table>

            <div class="divider"></div>

            <div class="footer">
                <div>$footerText</div>
                <div style="font-size: 9px; margin-top: 3px;">Barang yang sudah dibeli tidak dapat ditukar/dikembalikan</div>
            </div>
            <div class="divider-double"></div>
        </body>
        </html>
        """.trimIndent()
    }

    /**
     * Prints the receipt using Android's native [PrintManager] and a WebView print adapter.
     */
    fun printReceipt(context: Context, receipt: ReceiptUiModel) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return

        // Ensure execution on UI thread for WebView instantiations
        Handler(Looper.getMainLooper()).post {
            val webView = WebView(context)
            val htmlContent = generateHtmlReceipt(receipt)

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    val printAdapter = webView.createPrintDocumentAdapter("Struk-${receipt.receiptNo}")
                    val jobName = "${receipt.storeName}-${receipt.receiptNo}"
                    val printAttributes = PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build()

                    printManager.print(jobName, printAdapter, printAttributes)
                }
            }

            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
        }
    }

    /**
     * Shares the plain-text receipt via Android's [Intent.ACTION_SEND] (e.g. WhatsApp, SMS, Email).
     */
    fun shareReceipt(context: Context, receipt: ReceiptUiModel) {
        val text = generatePlainTextReceipt(receipt)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "Struk Pembelian ${receipt.receiptNo}")
        }
        val chooser = Intent.createChooser(sendIntent, "Bagikan Struk Belanja")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun centerText(text: String, width: Int): String {
        if (text.length >= width) return text
        val leftPadding = (width - text.length) / 2
        val rightPadding = width - text.length - leftPadding
        return " ".repeat(leftPadding) + text + " ".repeat(rightPadding)
    }

    private fun justifyText(left: String, right: String, width: Int): String {
        val totalLength = left.length + right.length
        return if (totalLength >= width) {
            "$left $right"
        } else {
            val spaces = width - totalLength
            left + " ".repeat(spaces) + right
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
