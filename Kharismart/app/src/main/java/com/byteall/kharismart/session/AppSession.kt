package com.byteall.kharismart.session

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UserRole(val dbValue: String) {
    ADMIN("admin"),
    CASHIER("cashier");

    companion object {
        fun fromDb(value: String): UserRole {
            return entries.firstOrNull { it.dbValue.equals(value, ignoreCase = true) }
                ?: CASHIER
        }
    }
}

data class SessionUser(
    val userId: Long,
    val username: String,
    val name: String,
    val role: UserRole
)

object AppSession {
    private val _currentUser = MutableStateFlow<SessionUser?>(null)
    val currentUser: StateFlow<SessionUser?> = _currentUser.asStateFlow()

    @Volatile
    var lastInteractionMillis: Long = SystemClock.elapsedRealtime()
        private set

    fun signIn(user: SessionUser) {
        _currentUser.value = user
        touch()
    }

    fun signOut() {
        _currentUser.value = null
    }

    fun isAdmin(): Boolean = _currentUser.value?.role == UserRole.ADMIN

    fun touch() {
        lastInteractionMillis = SystemClock.elapsedRealtime()
    }
}