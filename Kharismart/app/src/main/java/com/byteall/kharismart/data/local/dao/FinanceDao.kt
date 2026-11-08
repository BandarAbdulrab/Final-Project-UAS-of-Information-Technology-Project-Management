package com.byteall.kharismart.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.byteall.kharismart.data.local.entity.FinanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    @Query(
        """
        SELECT * FROM finance 
        WHERE date >= :start AND date < :endExclusive 
        ORDER BY date DESC, finance_id DESC
        """
    )
    fun observeBetween(start: String, endExclusive: String): Flow<List<FinanceEntity>>

    @Query("SELECT * FROM finance WHERE finance_id = :id")
    suspend fun getById(id: Long): FinanceEntity?

    @Insert
    suspend fun insert(record: FinanceEntity): Long

    @Query("DELETE FROM finance WHERE finance_id = :id")
    suspend fun deleteById(id: Long)
}
