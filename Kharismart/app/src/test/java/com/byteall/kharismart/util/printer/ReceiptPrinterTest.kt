package com.byteall.kharismart.util.printer

import com.byteall.kharismart.data.local.PaymentMethod
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptPrinterTest {

    private val sampleItems = listOf(
        ReceiptItemUiModel(name = "Indomie Goreng", quantity = 2, price = 3500L, subtotal = 7000L),
        ReceiptItemUiModel(name = "Minyak Goreng 1L", quantity = 1, price = 18000L, subtotal = 18000L)
    )

    private val sampleCashReceipt = ReceiptUiModel(
        transactionId = 1L,
        receiptNo = "TRX-20261115-001",
        dateFormatted = "15/11/2026 10:30",
        storeName = "Toko Kharisma",
        storeAddress = "Jl. Saudagaran No. 12",
        cashierName = "Rizki",
        items = sampleItems,
        total = 25000L,
        paymentMethod = PaymentMethod.CASH,
        paid = 50000L,
        changeDue = 25000L,
        customerName = null,
        remainingDebt = null,
        footer = "Terima Kasih Banyak",
        isReprint = false
    )

    @Test
    fun testGeneratePlainTextReceipt_cashPaymentFormatting() {
        val plainText = ReceiptPrinter.generatePlainTextReceipt(sampleCashReceipt)

        assertTrue(plainText.contains("TOKO KHARISMA"))
        assertTrue(plainText.contains("Jl. Saudagaran No. 12"))
        assertTrue(plainText.contains("No. Nota : TRX-20261115-001"))
        assertTrue(plainText.contains("Waktu    : 15/11/2026 10:30 WIB"))
        assertTrue(plainText.contains("Kasir    : Rizki"))
        assertTrue(plainText.contains("Indomie Goreng"))
        assertTrue(plainText.contains("2 x Rp3.500"))
        assertTrue(plainText.contains("Minyak Goreng 1L"))
        assertTrue(plainText.contains("TOTAL BELANJA"))
        assertTrue(plainText.contains("Rp25.000"))
        assertTrue(plainText.contains("Metode Bayar  :            Tunai"))
        assertTrue(plainText.contains("Uang Diterima :         Rp50.000"))
        assertTrue(plainText.contains("Kembalian     :         Rp25.000"))
        assertTrue(plainText.contains("Terima Kasih Banyak"))
        // Original receipt does not show reprint banner
        assertFalse(plainText.contains("[ SALINAN / REPRINT ]"))
    }

    @Test
    fun testGeneratePlainTextReceipt_reprintBannerDisplayed() {
        val reprintReceipt = sampleCashReceipt.copy(isReprint = true)
        val plainText = ReceiptPrinter.generatePlainTextReceipt(reprintReceipt)

        assertTrue(plainText.contains("[ SALINAN / REPRINT ]"))
    }

    @Test
    fun testGeneratePlainTextReceipt_qrisPayment() {
        val qrisReceipt = sampleCashReceipt.copy(
            paymentMethod = PaymentMethod.QRIS,
            paid = 25000L,
            changeDue = 0L
        )
        val plainText = ReceiptPrinter.generatePlainTextReceipt(qrisReceipt)

        assertTrue(plainText.contains("QRIS"))
        assertTrue(plainText.contains("LUNAS"))
    }

    @Test
    fun testGeneratePlainTextReceipt_creditPaymentWithDebtor() {
        val creditReceipt = sampleCashReceipt.copy(
            paymentMethod = PaymentMethod.CREDIT,
            paid = 0L,
            changeDue = 0L,
            customerName = "Ibu Siti",
            remainingDebt = 25000L
        )
        val plainText = ReceiptPrinter.generatePlainTextReceipt(creditReceipt)

        assertTrue(plainText.contains("Utang (Kredit)"))
        assertTrue(plainText.contains("Ibu Siti"))
        assertTrue(plainText.contains("Dibayar       :              Rp0"))
        assertTrue(plainText.contains("Sisa Utang    :         Rp25.000"))
    }

    @Test
    fun testGenerateHtmlReceipt_validHtmlStructure() {
        val html = ReceiptPrinter.generateHtmlReceipt(sampleCashReceipt)

        assertTrue(html.contains("<!DOCTYPE html>"))
        assertTrue(html.contains("<title>Struk - TRX-20261115-001</title>"))
        assertTrue(html.contains("TOKO KHARISMA"))
        assertTrue(html.contains("Indomie Goreng"))
        assertTrue(html.contains("58mm"))
        assertFalse(html.contains("[ SALINAN / REPRINT ]"))

        val reprintHtml = ReceiptPrinter.generateHtmlReceipt(sampleCashReceipt.copy(isReprint = true))
        assertTrue(reprintHtml.contains("[ SALINAN / REPRINT ]"))
    }
}
