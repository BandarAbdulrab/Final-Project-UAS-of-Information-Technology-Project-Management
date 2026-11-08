package com.byteall.kharismart.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.byteall.kharismart.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE status = 1 ORDER BY role ASC, name ASC")
    fun observeActiveUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users ORDER BY role ASC, name ASC")
    fun observeAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE user_id = :userId")
    suspend fun getById(userId: Long): UserEntity?

    @Query("SELECT * FROM users WHERE LOWER(username) = LOWER(:username)")
    suspend fun getByUsername(username: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users WHERE role = 'admin' AND status = 1")
    suspend fun countActiveAdmins(): Int

    @Insert
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity)

    @Query("UPDATE users SET pin_hash = :pinHash WHERE user_id = :userId")
    suspend fun updatePinHash(userId: Long, pinHash: String)

    @Query("UPDATE users SET recovery_hash = :recoveryHash WHERE user_id = :userId")
    suspend fun updateRecoveryHash(userId: Long, recoveryHash: String?)

    @Query("UPDATE users SET status = :status WHERE user_id = :userId")
    suspend fun updateStatus(userId: Long, status: Int)
}
