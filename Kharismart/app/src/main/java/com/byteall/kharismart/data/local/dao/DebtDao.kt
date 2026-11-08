package com.byteall.kharismart.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.byteall.kharismart.data.local.entity.DebtEntity
import com.byteall.kharismart.data.local.entity.DebtPaymentEntity
import com.byteall.kharismart.data.local.model.DebtWithOrigin
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtDao {
    @Query(
        """
        SELECT debts.*, transactions.receipt_no 
        FROM debts 
        LEFT JOIN transactions ON debts.transaction_id = transactions.transaction_id 
        ORDER BY CASE WHEN debts.status = 'unpaid' THEN 0 ELSE 1 END, debts.date DESC, debts.debt_id DESC
        """
    )
    fun observeAllDebts(): Flow<List<DebtWithOrigin>>

    @Query("SELECT COALESCE(SUM(total_amount - paid_amount), 0) FROM debts WHERE status = 'unpaid'")
    fun observeOutstandingTotal(): Flow<Long>

    @Query("SELECT * FROM debts WHERE debt_id = :debtId")
    suspend fun getDebtById(debtId: Long): DebtEntity?

    @Query("SELECT * FROM debts WHERE transaction_id = :transactionId")
    suspend fun getDebtByTransactionId(transactionId: Long): DebtEntity?

    @Insert
    suspend fun insertDebt(debt: DebtEntity): Long

    @Query("UPDATE debts SET paid_amount = :paidAmount, status = :status WHERE debt_id = :debtId")
    suspend fun updateDebtSettlement(debtId: Long, paidAmount: Long, status: String)

    @Query("DELETE FROM debts WHERE debt_id = :debtId")
    suspend fun deleteDebt(debtId: Long)

    @Insert
    suspend fun insertPayment(payment: DebtPaymentEntity): Long

    @Query("SELECT * FROM debt_payments WHERE debt_id = :debtId ORDER BY date DESC, payment_id DESC")
    fun observePaymentsForDebt(debtId: Long): Flow<List<DebtPaymentEntity>>

    @Query("SELECT COUNT(*) FROM debt_payments WHERE debt_id = :debtId")
    suspend fun countPaymentsForDebt(debtId: Long): Int

    @Query("SELECT DISTINCT customer_name FROM debts ORDER BY customer_name ASC")
    fun observeCustomerNames(): Flow<List<String>>
}
