package com.byteall.kharismart.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.byteall.kharismart.data.local.model.CategorySales
import com.byteall.kharismart.data.local.model.FinanceTotals
import com.byteall.kharismart.data.local.model.MethodTotals
import com.byteall.kharismart.data.local.model.ProductSales
import com.byteall.kharismart.data.local.model.SalesTotals
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalyticsDao {
    @Query(
        """
        SELECT 
            COALESCE(SUM(total), 0) AS revenue,
            COALESCE(SUM(gross_profit), 0) AS gross_profit,
            COALESCE(SUM(CASE WHEN method = 'cash' THEN total ELSE 0 END), 0) AS cash_sales,
            COALESCE(SUM(CASE WHEN method = 'qris' THEN total ELSE 0 END), 0) AS qris_sales,
            COALESCE(SUM(CASE WHEN method = 'credit' THEN total ELSE 0 END), 0) AS credit_sales,
            COUNT(*) AS transaction_count
        FROM transactions
        WHERE date >= :start AND date < :endExclusive
        """
    )
    fun observeSalesTotals(start: String, endExclusive: String): Flow<SalesTotals>

    @Query(
        """
        SELECT 
            COALESCE(SUM(CASE WHEN method = 'cash' THEN amount ELSE 0 END), 0) AS cash,
            COALESCE(SUM(CASE WHEN method = 'qris' THEN amount ELSE 0 END), 0) AS qris
        FROM debt_payments
        WHERE date >= :start AND date < :endExclusive
        """
    )
    fun observeDebtPaymentTotals(start: String, endExclusive: String): Flow<MethodTotals>

    @Query(
        """
        SELECT 
            COALESCE(SUM(CASE WHEN type = 'income' THEN amount ELSE 0 END), 0) AS income,
            COALESCE(SUM(CASE WHEN type = 'expense' THEN amount ELSE 0 END), 0) AS expense
        FROM finance
        WHERE date >= :start AND date < :endExclusive
        """
    )
    fun observeFinanceTotals(start: String, endExclusive: String): Flow<FinanceTotals>

    @Query(
        """
        SELECT COALESCE(SUM(total_cost), 0)
        FROM stock_movements
        WHERE type = 'restock' AND date >= :start AND date < :endExclusive
        """
    )
    fun observeRestockPurchases(start: String, endExclusive: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(total_amount - paid_amount), 0) FROM debts WHERE status = 'unpaid'")
    fun observeOutstandingDebt(): Flow<Long>

    @Query(
        """
        SELECT 
            c.name AS category_name,
            COALESCE(SUM(ti.subtotal), 0) AS revenue,
            COALESCE(SUM(ti.subtotal - ti.cost_price * ti.quantity), 0) AS gross_profit,
            COALESCE(SUM(ti.quantity), 0) AS units
        FROM transaction_items ti
        JOIN transactions t ON ti.transaction_id = t.transaction_id
        JOIN products p ON ti.product_id = p.product_id
        JOIN categories c ON p.category_id = c.category_id
        WHERE t.date >= :start AND t.date < :endExclusive
        GROUP BY c.category_id, c.name
        ORDER BY revenue DESC
        """
    )
    fun observeCategorySales(start: String, endExclusive: String): Flow<List<CategorySales>>

    @Query(
        """
        SELECT 
            p.product_id AS product_id,
            p.name AS product_name,
            COALESCE(SUM(ti.quantity), 0) AS units,
            COALESCE(SUM(ti.subtotal), 0) AS revenue,
            COALESCE(SUM(ti.subtotal - ti.cost_price * ti.quantity), 0) AS gross_profit
        FROM transaction_items ti
        JOIN transactions t ON ti.transaction_id = t.transaction_id
        JOIN products p ON ti.product_id = p.product_id
        WHERE t.date >= :start AND t.date < :endExclusive
        GROUP BY p.product_id, p.name
        ORDER BY revenue DESC
        """
    )
    fun observeProductSales(start: String, endExclusive: String): Flow<List<ProductSales>>
}
