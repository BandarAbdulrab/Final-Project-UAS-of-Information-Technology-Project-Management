package com.byteall.kharismart.util.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.byteall.kharismart.data.local.model.CategorySales
import com.byteall.kharismart.data.local.model.ProductSales
import com.byteall.kharismart.data.repository.AnalyticsRepository
import com.byteall.kharismart.data.repository.FinancialSummary
import com.byteall.kharismart.util.format.toRupiah
import java.io.File

data class CsvTargetProgressSnapshot(
    val dailyRevenue: Long,
    val dailyTarget: Long?,
    val weeklyRevenue: Long,
    val weeklyTarget: Long?,
    val monthlyRevenue: Long,
    val monthlyTarget: Long?
)

object CsvReportExporter {

    private const val CRLF = "\r\n"

    /**
     * Escapes a single CSV field according to RFC 4180:
     * Fields containing commas, double quotes, CR, or LF are enclosed in double-quotes,
     * and embedded double-quotes are doubled.
     */
    fun escapeCsvField(value: String?): String {
        val raw = value ?: ""
        val needsQuotes = raw.any { it == ',' || it == '"' || it == '\r' || it == '\n' }
        return if (needsQuotes) {
            "\"${raw.replace("\"", "\"\"")}\""
        } else {
            raw
        }
    }

    private fun StringBuilder.appendCsvRow(vararg columns: Any?) {
        columns.forEachIndexed { index, col ->
            if (index > 0) append(',')
            append(escapeCsvField(col?.toString() ?: ""))
        }
        append(CRLF)
    }

    /**
     * Builds a complete RFC 4180 CSV report containing the store header,
     * BR-14 financial summary, target achievement snapshot, category breakdown,
     * and best-selling product performance.
     */
    fun buildCsvReport(
        storeName: String,
        periodLabel: String,
        startDateDisplay: String,
        endDateDisplay: String,
        exportedAtWib: String,
        summary: FinancialSummary,
        targets: CsvTargetProgressSnapshot,
        categories: List<CategorySales>,
        products: List<ProductSales>
    ): String {
        val sb = StringBuilder()
        val formattedExportTime = if (exportedAtWib.endsWith("WIB")) {
            exportedAtWib
        } else {
            "$exportedAtWib WIB"
        }

        // 1. Report Metadata Header
        sb.appendCsvRow("LAPORAN ANALITIK KEUANGAN KHARISMART")
        sb.appendCsvRow("Nama Toko", storeName)
        sb.appendCsvRow("Periode Laporan", periodLabel)
        sb.appendCsvRow("Rentang Tanggal", "$startDateDisplay s/d $endDateDisplay")
        sb.appendCsvRow("Waktu Ekspor (WIB)", formattedExportTime)
        sb.appendCsvRow()

        // 2. BR-14 Financial Summary
        sb.appendCsvRow("RINGKASAN KINERJA KEUANGAN (BR-14)")
        sb.appendCsvRow("Metrik", "Nominal (Rp)", "Format Rupiah")
        sb.appendCsvRow("Jumlah Transaksi POS", summary.transactionCount, "${summary.transactionCount} Transaksi")
        sb.appendCsvRow("Total Omzet (Pendapatan)", summary.revenue, summary.revenue.toRupiah())
        sb.appendCsvRow("Laba Kotor (Gross Profit)", summary.grossProfit, summary.grossProfit.toRupiah())
        sb.appendCsvRow("Pemasukan Kas Manual Lainnya", summary.otherIncome, summary.otherIncome.toRupiah())
        sb.appendCsvRow("Biaya Operasional Toko", summary.operatingExpenses, summary.operatingExpenses.toRupiah())
        sb.appendCsvRow("Laba Bersih (Net Profit)", summary.netProfit, summary.netProfit.toRupiah())
        sb.appendCsvRow("Penjualan Tunai (POS)", summary.cashSales, summary.cashSales.toRupiah())
        sb.appendCsvRow("Penjualan QRIS (POS)", summary.qrisSales, summary.qrisSales.toRupiah())
        sb.appendCsvRow("Penjualan Kredit / Utang Baru (POS)", summary.creditSales, summary.creditSales.toRupiah())
        sb.appendCsvRow("Pelunasan Utang via Tunai", summary.cashDebtPayments, summary.cashDebtPayments.toRupiah())
        sb.appendCsvRow("Pelunasan Utang via QRIS", summary.qrisDebtPayments, summary.qrisDebtPayments.toRupiah())
        sb.appendCsvRow("Total Kas Tunai Diterima", summary.cashReceived, summary.cashReceived.toRupiah())
        sb.appendCsvRow("Total Dana QRIS Diterima", summary.qrisReceived, summary.qrisReceived.toRupiah())
        sb.appendCsvRow("Belanja Restok Barang Dagangan", summary.restockPurchases, summary.restockPurchases.toRupiah())
        sb.appendCsvRow("Saldo Kas & QRIS Bersih", summary.cashBalance, summary.cashBalance.toRupiah())
        sb.appendCsvRow("Total Piutang Belum Lunas Saat Ini", summary.outstandingDebt, summary.outstandingDebt.toRupiah())
        sb.appendCsvRow()

        // 3. Target Progress Snapshot
        val dailyPct = AnalyticsRepository.targetProgressPercent(targets.dailyRevenue, targets.dailyTarget)
        val weeklyPct = AnalyticsRepository.targetProgressPercent(targets.weeklyRevenue, targets.weeklyTarget)
        val monthlyPct = AnalyticsRepository.targetProgressPercent(targets.monthlyRevenue, targets.monthlyTarget)

        sb.appendCsvRow("CAPAIAN TARGET OMZET")
        sb.appendCsvRow("Periode Target", "Omzet Aktual (Rp)", "Target (Rp)", "Capaian (%)")
        sb.appendCsvRow(
            "Harian",
            targets.dailyRevenue,
            targets.dailyTarget ?: 0L,
            if (targets.dailyTarget != null && targets.dailyTarget > 0L) "$dailyPct%" else "Belum diatur"
        )
        sb.appendCsvRow(
            "Mingguan",
            targets.weeklyRevenue,
            targets.weeklyTarget ?: 0L,
            if (targets.weeklyTarget != null && targets.weeklyTarget > 0L) "$weeklyPct%" else "Belum diatur"
        )
        sb.appendCsvRow(
            "Bulanan",
            targets.monthlyRevenue,
            targets.monthlyTarget ?: 0L,
            if (targets.monthlyTarget != null && targets.monthlyTarget > 0L) "$monthlyPct%" else "Belum diatur"
        )
        sb.appendCsvRow()

        // 4. Category Breakdown
        sb.appendCsvRow("KINERJA PENJUALAN PER KATEGORI")
        sb.appendCsvRow("Kategori", "Unit Terjual", "Omzet (Rp)", "Laba Kotor (Rp)")
        if (categories.isEmpty()) {
            sb.appendCsvRow("Tidak ada data kategori pada periode ini", 0, 0, 0)
        } else {
            categories.forEach { cat ->
                sb.appendCsvRow(cat.categoryName, cat.units, cat.revenue, cat.grossProfit)
            }
        }
        sb.appendCsvRow()

        // 5. Best Sellers Table
        sb.appendCsvRow("KINERJA PRODUK TERLARIS")
        sb.appendCsvRow("Peringkat", "ID Produk", "Nama Produk", "Unit Terjual", "Omzet (Rp)", "Laba Kotor (Rp)")
        if (products.isEmpty()) {
            sb.appendCsvRow(0, 0, "Tidak ada penjualan produk pada periode ini", 0, 0, 0)
        } else {
            products.forEachIndexed { index, prod ->
                sb.appendCsvRow(
                    index + 1,
                    prod.productId,
                    prod.productName,
                    prod.units,
                    prod.revenue,
                    prod.grossProfit
                )
            }
        }

        return sb.toString()
    }

    fun suggestedFileName(startDateDisplay: String, endDateDisplay: String): String {
        val safeStart = startDateDisplay.replace("/", "-")
        val safeEnd = endDateDisplay.replace("/", "-")
        return "laporan_kharismart_${safeStart}_${safeEnd}.csv"
    }

    /**
     * Writes the CSV into a temporary file in cacheDir/exports/ and shares it as a
     * binary .csv document attachment via FileProvider (Intent.EXTRA_STREAM).
     */
    fun shareCsvReport(
        context: Context,
        csvContent: String,
        fileName: String,
        chooserTitle: String
    ): Result<Unit> = runCatching {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val csvFile = File(exportDir, fileName)
        csvFile.outputStream().use { stream ->
            val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
            stream.write(utf8Bom)
            stream.write(csvContent.toByteArray(Charsets.UTF_8))
            stream.flush()
        }

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            csvFile
        )

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            putExtra(Intent.EXTRA_TITLE, fileName)
            putExtra(Intent.EXTRA_STREAM, contentUri)
            clipData = ClipData.newRawUri(fileName, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /**
     * Writes the RFC 4180 CSV content directly to a user-selected SAF Uri.
     */
    fun writeCsvToUri(
        context: Context,
        destinationUri: Uri,
        csvContent: String
    ): Result<Unit> = runCatching {
        val outputStream = context.contentResolver.openOutputStream(destinationUri)
            ?: throw IllegalStateException("Tidak dapat membuka file tujuan.")
        outputStream.use { stream ->
            val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
            stream.write(utf8Bom)
            stream.write(csvContent.toByteArray(Charsets.UTF_8))
            stream.flush()
        }
    }
}