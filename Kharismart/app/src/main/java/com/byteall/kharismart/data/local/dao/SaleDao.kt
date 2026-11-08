package com.byteall.kharismart.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.byteall.kharismart.data.local.entity.TransactionEntity
import com.byteall.kharismart.data.local.entity.TransactionItemEntity
import com.byteall.kharismart.data.local.model.TransactionItemWithName
import com.byteall.kharismart.data.local.model.TransactionWithCashier
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleDao {
    @Insert
    suspend fun insertTransaction(trx: TransactionEntity): Long

    @Insert
    suspend fun insertItems(items: List<TransactionItemEntity>)

    @Query("SELECT * FROM transactions WHERE transaction_id = :id")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Query(
        """
        SELECT transactions.*, users.name AS cashier_name 
        FROM transactions 
        JOIN users ON transactions.user_id = users.user_id 
        WHERE transactions.transaction_id = :id
        """
    )
    suspend fun getTransactionWithCashier(id: Long): TransactionWithCashier?

    @Query(
        """
        SELECT transactions.*, users.name AS cashier_name 
        FROM transactions 
        JOIN users ON transactions.user_id = users.user_id 
        WHERE transactions.receipt_no = :receiptNo
        """
    )
    suspend fun getByReceiptNo(receiptNo: String): TransactionWithCashier?

    @Query(
        """
        SELECT transactions.*, users.name AS cashier_name 
        FROM transactions 
        JOIN users ON transactions.user_id = users.user_id 
        WHERE transactions.date >= :start AND transactions.date < :endExclusive 
        ORDER BY transactions.date DESC, transactions.transaction_id DESC
        """
    )
    fun observeTransactionsBetween(start: String, endExclusive: String): Flow<List<TransactionWithCashier>>

    @Query(
        """
        SELECT transaction_items.*, products.name AS product_name 
        FROM transaction_items 
        JOIN products ON transaction_items.product_id = products.product_id 
        WHERE transaction_items.transaction_id = :transactionId 
        ORDER BY transaction_items.item_id ASC
        """
    )
    suspend fun getItemsWithNames(transactionId: Long): List<TransactionItemWithName>

    @Query("SELECT MAX(CAST(SUBSTR(receipt_no, LENGTH(:prefix) + 1) AS INTEGER)) FROM transactions WHERE receipt_no LIKE :prefix || '%'")
    suspend fun getMaxSequenceForPrefix(prefix: String): Int?
}
