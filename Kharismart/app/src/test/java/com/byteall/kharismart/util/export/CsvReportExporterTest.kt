package com.byteall.kharismart.util.export

import com.byteall.kharismart.data.local.model.CategorySales
import com.byteall.kharismart.data.local.model.ProductSales
import com.byteall.kharismart.data.repository.FinancialSummary
import com.byteall.kharismart.util.time.WibClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvReportExporterTest {

    @Test
    fun escapeCsvField_handlesPlainCommasAndQuotesPerRfc4180() {
        assertEquals("Sembako", CsvReportExporter.escapeCsvField("Sembako"))
        assertEquals("\"Minyak, Gula\"", CsvReportExporter.escapeCsvField("Minyak, Gula"))
        assertEquals("\"Indomie \"\"Spesial\"\"\"", CsvReportExporter.escapeCsvField("Indomie \"Spesial\""))
    }

    @Test
    fun buildCsvReport_includesAllBr14SectionsAndProducts() {
        val range = WibClock.DateRange("2026-11-14 00:00:00", "2026-11-15 00:00:00")
        val summary = FinancialSummary(
            range = range,
            revenue = 49_000L,
            grossProfit = 19_000L,
            cashSales = 0L,
            qrisSales = 0L,
            creditSales = 49_000L,
            cashDebtPayments = 10_000L,
            qrisDebtPayments = 0L,
            cashReceived = 10_000L,
            qrisReceived = 0L,
            otherIncome = 5_000L,
            operatingExpenses = 20_000L,
            restockPurchases = 50_000L,
            netProfit = 4_000L,
            cashBalance = -55_000L,
            outstandingDebt = 39_000L,
            transactionCount = 1
        )
        val targets = CsvTargetProgressSnapshot(
            dailyRevenue = 49_000L,
            dailyTarget = 100_000L,
            weeklyRevenue = 49_000L,
            weeklyTarget = null,
            monthlyRevenue = 49_000L,
            monthlyTarget = 200_000L
        )
        val categories = listOf(
            CategorySales(categoryName = "Sembako", revenue = 49_000L, grossProfit = 19_000L, units = 7)
        )
        val products = listOf(
            ProductSales(
                productId = 1L,
                productName = "Indomie Goreng, Jumbo",
                units = 7,
                revenue = 49_000L,
                grossProfit = 19_000L
            )
        )

        val csv = CsvReportExporter.buildCsvReport(
            storeName = "Toko Kharisma",
            periodLabel = "Hari Ini",
            startDateDisplay = "2026-11-14",
            endDateDisplay = "2026-11-14",
            exportedAtWib = "2026-11-14 10:00:00",
            summary = summary,
            targets = targets,
            categories = categories,
            products = products
        )

        assertTrue(csv.contains("Waktu Ekspor (WIB),2026-11-14 10:00:00 WIB\r\n"))
        assertTrue(csv.contains("RINGKASAN KINERJA KEUANGAN (BR-14)\r\n"))
        assertTrue(csv.contains("Total Omzet (Pendapatan),49000,Rp49.000\r\n"))
        assertTrue(csv.contains("Laba Bersih (Net Profit),4000,Rp4.000\r\n"))
        assertTrue(csv.contains("Saldo Kas & QRIS Bersih,-55000,-Rp55.000\r\n"))
        assertTrue(csv.contains("Harian,49000,100000,49%\r\n"))
        assertTrue(csv.contains("1,1,\"Indomie Goreng, Jumbo\",7,49000,19000\r\n"))
    }
}