package com.byteall.kharismart.data.repository

import com.byteall.kharismart.data.local.dao.AnalyticsDao
import com.byteall.kharismart.data.local.model.CategorySales
import com.byteall.kharismart.data.local.model.ProductSales
import com.byteall.kharismart.util.time.WibClock.DateRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.math.min

data class FinancialSummary(
    val range: DateRange,
    val revenue: Long,
    val grossProfit: Long,
    val cashSales: Long,
    val qrisSales: Long,
    val creditSales: Long,
    val cashDebtPayments: Long,
    val qrisDebtPayments: Long,
    val cashReceived: Long,
    val qrisReceived: Long,
    val otherIncome: Long,
    val operatingExpenses: Long,
    val restockPurchases: Long,
    val netProfit: Long,
    val cashBalance: Long,
    val outstandingDebt: Long,
    val transactionCount: Int
)

class AnalyticsRepository(private val analyticsDao: AnalyticsDao) {

    fun observeSummary(range: DateRange): Flow<FinancialSummary> {
        return combine(
            analyticsDao.observeSalesTotals(range.start, range.endExclusive),
            analyticsDao.observeDebtPaymentTotals(range.start, range.endExclusive),
            analyticsDao.observeFinanceTotals(range.start, range.endExclusive),
            analyticsDao.observeRestockPurchases(range.start, range.endExclusive),
            analyticsDao.observeOutstandingDebt()
        ) { sales, debts, finance, restocks, outstandingDebt ->
            // BR-14 Accounting & Financial Formulas
            val revenue = sales.revenue
            val grossProfit = sales.grossProfit
            val cashSales = sales.cashSales
            val qrisSales = sales.qrisSales
            val creditSales = sales.creditSales

            val cashDebtPayments = debts.cash
            val qrisDebtPayments = debts.qris

            val cashReceived = cashSales + cashDebtPayments
            val qrisReceived = qrisSales + qrisDebtPayments

            val otherIncome = finance.income
            val operatingExpenses = finance.expense
            val restockPurchases = restocks

            val netProfit = grossProfit + otherIncome - operatingExpenses
            val cashBalance = cashReceived + qrisReceived + otherIncome - operatingExpenses - restockPurchases

            FinancialSummary(
                range = range,
                revenue = revenue,
                grossProfit = grossProfit,
                cashSales = cashSales,
                qrisSales = qrisSales,
                creditSales = creditSales,
                cashDebtPayments = cashDebtPayments,
                qrisDebtPayments = qrisDebtPayments,
                cashReceived = cashReceived,
                qrisReceived = qrisReceived,
                otherIncome = otherIncome,
                operatingExpenses = operatingExpenses,
                restockPurchases = restockPurchases,
                netProfit = netProfit,
                cashBalance = cashBalance,
                outstandingDebt = outstandingDebt,
                transactionCount = sales.transactionCount
            )
        }
    }

    fun observeCategorySales(range: DateRange): Flow<List<CategorySales>> =
        analyticsDao.observeCategorySales(range.start, range.endExclusive)

    fun observeProductSales(range: DateRange): Flow<List<ProductSales>> =
        analyticsDao.observeProductSales(range.start, range.endExclusive)

    companion object {
        fun targetProgressPercent(periodRevenue: Long, target: Long?): Int {
            if (target == null || target <= 0L) return 0
            val progress = (periodRevenue * 100L) / target
            return min(100L, progress).toInt().coerceAtLeast(0)
        }
    }
}