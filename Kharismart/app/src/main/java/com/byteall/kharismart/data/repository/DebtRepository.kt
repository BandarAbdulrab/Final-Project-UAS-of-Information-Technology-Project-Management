package com.byteall.kharismart.data.repository

import androidx.room.withTransaction
import com.byteall.kharismart.data.local.DebtStatus
import com.byteall.kharismart.data.local.KharismartDatabase
import com.byteall.kharismart.data.local.PaymentMethod
import com.byteall.kharismart.data.local.dao.DebtDao
import com.byteall.kharismart.data.local.entity.DebtEntity
import com.byteall.kharismart.data.local.entity.DebtPaymentEntity
import com.byteall.kharismart.data.local.model.DebtWithOrigin
import com.byteall.kharismart.util.DomainException
import com.byteall.kharismart.util.time.WibClock
import kotlinx.coroutines.flow.Flow

class DebtRepository(
    private val database: KharismartDatabase,
    private val debtDao: DebtDao
) {

    fun observeAll(): Flow<List<DebtWithOrigin>> = debtDao.observeAllDebts()

    fun observeOutstandingTotal(): Flow<Long> = debtDao.observeOutstandingTotal()

    fun observePayments(debtId: Long): Flow<List<DebtPaymentEntity>> = debtDao.observePaymentsForDebt(debtId)

    fun observeCustomerNames(): Flow<List<String>> = debtDao.observeCustomerNames()

    suspend fun getDebt(debtId: Long): DebtEntity? = debtDao.getDebtById(debtId)

    suspend fun getDebtByTransaction(transactionId: Long): DebtEntity? =
        debtDao.getDebtByTransactionId(transactionId)

    suspend fun recordPayment(
        userId: Long,
        debtId: Long,
        amount: Long,
        method: String,
        note: String?
    ): Result<Unit> {
        if (amount <= 0) {
            return Result.failure(DomainException("Jumlah pembayaran hutang harus lebih besar dari 0"))
        }
        if (method != PaymentMethod.CASH && method != PaymentMethod.QRIS) {
            return Result.failure(DomainException("Metode pembayaran harus cash atau qris"))
        }

        return try {
            database.withTransaction {
                val debt = debtDao.getDebtById(debtId)
                    ?: throw DomainException("Catatan hutang tidak ditemukan")

                val remaining = debt.totalAmount - debt.paidAmount
                if (amount > remaining) {
                    throw DomainException("Jumlah pembayaran ($amount) melebihi sisa hutang ($remaining)")
                }

                val payment = DebtPaymentEntity(
                    debtId = debtId,
                    userId = userId,
                    date = WibClock.nowString(),
                    method = method,
                    amount = amount,
                    note = note?.trim()?.ifBlank { null }
                )
                debtDao.insertPayment(payment)

                val newPaidAmount = debt.paidAmount + amount
                val newStatus = if (newPaidAmount >= debt.totalAmount) DebtStatus.PAID else DebtStatus.UNPAID
                debtDao.updateDebtSettlement(debtId, newPaidAmount, newStatus)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addManualDebt(
        userId: Long,
        customerName: String,
        amount: Long,
        note: String?
    ): Result<Long> {
        val cleanName = customerName.trim()
        if (cleanName.isBlank()) {
            return Result.failure(DomainException("Nama pelanggan hutang tidak boleh kosong"))
        }
        if (amount <= 0) {
            return Result.failure(DomainException("Total nominal hutang harus lebih besar dari 0"))
        }

        val debt = DebtEntity(
            transactionId = null,
            userId = userId,
            customerName = cleanName,
            date = WibClock.nowString(),
            totalAmount = amount,
            paidAmount = 0L,
            status = DebtStatus.UNPAID,
            note = note?.trim()?.ifBlank { null }
        )
        val newId = debtDao.insertDebt(debt)
        return Result.success(newId)
    }

    suspend fun deleteManualDebt(debtId: Long): Result<Unit> {
        val debt = debtDao.getDebtById(debtId)
            ?: return Result.failure(DomainException("Catatan hutang tidak ditemukan"))

        if (debt.transactionId != null) {
            return Result.failure(DomainException("Hutang yang berasal dari transaksi POS tidak dapat dihapus"))
        }

        val paymentCount = debtDao.countPaymentsForDebt(debtId)
        if (paymentCount > 0) {
            return Result.failure(DomainException("Hutang yang sudah memiliki riwayat pembayaran tidak dapat dihapus"))
        }

        debtDao.deleteDebt(debtId)
        return Result.success(Unit)
    }
}