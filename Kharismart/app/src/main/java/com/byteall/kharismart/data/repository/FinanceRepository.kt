package com.byteall.kharismart.data.repository

import com.byteall.kharismart.data.local.FinanceType
import com.byteall.kharismart.data.local.dao.FinanceDao
import com.byteall.kharismart.data.local.entity.FinanceEntity
import com.byteall.kharismart.util.DomainException
import com.byteall.kharismart.util.time.WibClock.DateRange
import kotlinx.coroutines.flow.Flow

class FinanceRepository(private val financeDao: FinanceDao) {

    fun observeBetween(range: DateRange): Flow<List<FinanceEntity>> {
        return financeDao.observeBetween(range.start, range.endExclusive)
    }

    suspend fun add(
        userId: Long,
        date: String,
        type: String,
        category: String,
        amount: Long,
        description: String?
    ): Result<Long> {
        if (amount <= 0) {
            return Result.failure(DomainException("Nominal kas harus lebih besar dari 0"))
        }
        if (type != FinanceType.INCOME && type != FinanceType.EXPENSE) {
            return Result.failure(DomainException("Tipe transaksi kas tidak valid"))
        }
        val cleanCategory = category.trim()
        if (cleanCategory.isBlank()) {
            return Result.failure(DomainException("Kategori kas tidak boleh kosong"))
        }

        val record = FinanceEntity(
            userId = userId,
            date = date,
            type = type,
            category = cleanCategory,
            amount = amount,
            description = description?.trim()?.ifBlank { null }
        )
        val id = financeDao.insert(record)
        return Result.success(id)
    }

    suspend fun delete(financeId: Long): Result<Unit> {
        financeDao.deleteById(financeId)
        return Result.success(Unit)
    }
}