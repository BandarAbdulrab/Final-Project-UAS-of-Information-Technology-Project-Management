package com.byteall.kharismart.ui.lock

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byteall.kharismart.R
import com.byteall.kharismart.data.local.Role
import com.byteall.kharismart.data.local.entity.UserEntity
import com.byteall.kharismart.data.repository.UserRepository
import com.byteall.kharismart.session.AppSession
import com.byteall.kharismart.session.SessionUser
import com.byteall.kharismart.session.UserRole
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LockMessage {
    data class Resource(
        @StringRes val resId: Int,
        val args: List<Any> = emptyList()
    ) : LockMessage

    data class Plain(val text: String) : LockMessage
}

data class LockUiState(
    val activeUsers: List<UserEntity> = emptyList(),
    val selectedUserId: Long? = null,
    val pinInput: String = "",
    val isVerifying: Boolean = false,
    val failedAttempts: Int = 0,
    val lockoutSecondsRemaining: Int = 0,
    val errorMessage: LockMessage? = null,
    val showRecoveryDialog: Boolean = false,
    val recoveryAdminUserId: Long? = null,
    val recoveryCodeInput: String = "",
    val newPinInput: String = "",
    val confirmNewPinInput: String = "",
    val isResettingPin: Boolean = false,
    val recoveryErrorMessage: LockMessage? = null,
    val generatedNewRecoveryCode: String? = null
) {
    val selectedUser: UserEntity?
        get() = activeUsers.firstOrNull { it.userId == selectedUserId }

    val activeAdmins: List<UserEntity>
        get() = activeUsers.filter { it.role == Role.ADMIN }

    val isLockedOut: Boolean
        get() = lockoutSecondsRemaining > 0

    val canSubmitPin: Boolean
        get() = selectedUser != null &&
            !isLockedOut &&
            !isVerifying &&
            pinInput.length in LockViewModel.MIN_PIN_LENGTH..LockViewModel.MAX_PIN_LENGTH
}

class LockViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LockUiState())
    val uiState: StateFlow<LockUiState> = _uiState.asStateFlow()

    private var lockoutJob: Job? = null

    init {
        viewModelScope.launch {
            userRepository.observeActiveUsers().collect { users ->
                _uiState.update { current ->
                    val resolvedSelection = when {
                        users.any { it.userId == current.selectedUserId } -> current.selectedUserId
                        else -> users.firstOrNull()?.userId
                    }
                    val resolvedRecoveryAdmin = when {
                        users.any { it.userId == current.recoveryAdminUserId && it.role == Role.ADMIN } ->
                            current.recoveryAdminUserId
                        else -> users.firstOrNull { it.role == Role.ADMIN }?.userId
                    }
                    current.copy(
                        activeUsers = users,
                        selectedUserId = resolvedSelection,
                        recoveryAdminUserId = resolvedRecoveryAdmin
                    )
                }
            }
        }
    }

    fun selectUser(userId: Long) {
        _uiState.update { current ->
            if (current.isLockedOut || current.isVerifying) {
                current
            } else {
                current.copy(
                    selectedUserId = userId,
                    pinInput = "",
                    errorMessage = null
                )
            }
        }
    }

    fun onDigitPressed(digit: Char) {
        if (digit !in '0'..'9') return
        _uiState.update { current ->
            if (current.isLockedOut ||
                current.isVerifying ||
                current.selectedUser == null ||
                current.pinInput.length >= MAX_PIN_LENGTH
            ) {
                current
            } else {
                current.copy(
                    pinInput = current.pinInput + digit,
                    errorMessage = null
                )
            }
        }
    }

    fun onBackspacePressed() {
        _uiState.update { current ->
            if (current.isLockedOut || current.isVerifying || current.pinInput.isEmpty()) {
                current
            } else {
                current.copy(
                    pinInput = current.pinInput.dropLast(1),
                    errorMessage = null
                )
            }
        }
    }

    fun onClearPin() {
        _uiState.update { current ->
            if (current.isLockedOut || current.isVerifying) {
                current
            } else {
                current.copy(
                    pinInput = "",
                    errorMessage = null
                )
            }
        }
    }

    fun submitPin() {
        val snapshot = _uiState.value
        if (snapshot.isLockedOut || snapshot.isVerifying) return

        val user = snapshot.selectedUser
        if (user == null) {
            _uiState.update {
                it.copy(errorMessage = LockMessage.Resource(R.string.lock_error_select_staff))
            }
            return
        }

        val pin = snapshot.pinInput
        if (pin.length !in MIN_PIN_LENGTH..MAX_PIN_LENGTH) {
            _uiState.update {
                it.copy(errorMessage = LockMessage.Resource(R.string.lock_error_pin_length))
            }
            return
        }

        _uiState.update { it.copy(isVerifying = true, errorMessage = null) }

        viewModelScope.launch {
            val isValid = userRepository.verifyPin(user.userId, pin)
            if (isValid) {
                lockoutJob?.cancel()
                _uiState.update {
                    it.copy(
                        pinInput = "",
                        isVerifying = false,
                        failedAttempts = 0,
                        lockoutSecondsRemaining = 0,
                        errorMessage = null
                    )
                }
                AppSession.signIn(
                    SessionUser(
                        userId = user.userId,
                        username = user.username,
                        name = user.name,
                        role = UserRole.fromDb(user.role)
                    )
                )
                AppSession.touch()
            } else {
                val nextAttempts = _uiState.value.failedAttempts + 1
                if (nextAttempts >= MAX_FAILED_ATTEMPTS) {
                    _uiState.update {
                        it.copy(
                            pinInput = "",
                            isVerifying = false,
                            failedAttempts = nextAttempts,
                            errorMessage = null
                        )
                    }
                    startLockoutCountdown()
                } else {
                    val remainingAttempts = MAX_FAILED_ATTEMPTS - nextAttempts
                    _uiState.update {
                        it.copy(
                            pinInput = "",
                            isVerifying = false,
                            failedAttempts = nextAttempts,
                            errorMessage = LockMessage.Resource(
                                resId = R.string.lock_error_invalid_pin,
                                args = listOf(remainingAttempts)
                            )
                        )
                    }
                }
            }
        }
    }

    private fun startLockoutCountdown() {
        lockoutJob?.cancel()
        lockoutJob = viewModelScope.launch {
            for (secondsLeft in LOCKOUT_DURATION_SECONDS downTo 1) {
                _uiState.update { it.copy(lockoutSecondsRemaining = secondsLeft) }
                delay(1_000L)
            }
            _uiState.update {
                it.copy(
                    lockoutSecondsRemaining = 0,
                    failedAttempts = 0,
                    errorMessage = null
                )
            }
        }
    }

    fun openRecoveryDialog() {
        _uiState.update { current ->
            val preferredAdminId = current.selectedUser
                ?.takeIf { it.role == Role.ADMIN }
                ?.userId
                ?: current.activeAdmins.firstOrNull()?.userId

            current.copy(
                showRecoveryDialog = true,
                recoveryAdminUserId = preferredAdminId,
                recoveryCodeInput = "",
                newPinInput = "",
                confirmNewPinInput = "",
                recoveryErrorMessage = null
            )
        }
    }

    fun dismissRecoveryDialog() {
        _uiState.update { current ->
            if (current.isResettingPin) {
                current
            } else {
                current.copy(
                    showRecoveryDialog = false,
                    recoveryCodeInput = "",
                    newPinInput = "",
                    confirmNewPinInput = "",
                    recoveryErrorMessage = null
                )
            }
        }
    }

    fun selectRecoveryAdmin(userId: Long) {
        _uiState.update {
            it.copy(
                recoveryAdminUserId = userId,
                recoveryErrorMessage = null
            )
        }
    }

    fun onRecoveryCodeChanged(value: String) {
        _uiState.update {
            it.copy(
                recoveryCodeInput = value,
                recoveryErrorMessage = null
            )
        }
    }

    fun onNewPinChanged(value: String) {
        val digitsOnly = value.filter { it in '0'..'9' }.take(MAX_PIN_LENGTH)
        _uiState.update {
            it.copy(
                newPinInput = digitsOnly,
                recoveryErrorMessage = null
            )
        }
    }

    fun onConfirmNewPinChanged(value: String) {
        val digitsOnly = value.filter { it in '0'..'9' }.take(MAX_PIN_LENGTH)
        _uiState.update {
            it.copy(
                confirmNewPinInput = digitsOnly,
                recoveryErrorMessage = null
            )
        }
    }

    fun submitAdminRecovery() {
        val current = _uiState.value
        if (current.isResettingPin) return

        val adminId = current.recoveryAdminUserId
        if (adminId == null) {
            _uiState.update {
                it.copy(
                    recoveryErrorMessage = LockMessage.Resource(R.string.lock_recovery_error_no_admin)
                )
            }
            return
        }

        val code = current.recoveryCodeInput.trim()
        if (code.isEmpty()) {
            _uiState.update {
                it.copy(
                    recoveryErrorMessage = LockMessage.Resource(R.string.lock_recovery_error_code_required)
                )
            }
            return
        }

        val newPin = current.newPinInput
        if (newPin.length !in MIN_PIN_LENGTH..MAX_PIN_LENGTH) {
            _uiState.update {
                it.copy(
                    recoveryErrorMessage = LockMessage.Resource(R.string.lock_error_pin_length)
                )
            }
            return
        }

        if (newPin != current.confirmNewPinInput) {
            _uiState.update {
                it.copy(
                    recoveryErrorMessage = LockMessage.Resource(R.string.lock_recovery_error_pin_mismatch)
                )
            }
            return
        }

        _uiState.update { it.copy(isResettingPin = true, recoveryErrorMessage = null) }

        viewModelScope.launch {
            val result = userRepository.resetAdminPinWithRecoveryCode(
                userId = adminId,
                recoveryCode = code,
                newPin = newPin
            )
            result.fold(
                onSuccess = { generatedCode ->
                    lockoutJob?.cancel()
                    _uiState.update {
                        it.copy(
                            selectedUserId = adminId,
                            pinInput = "",
                            failedAttempts = 0,
                            lockoutSecondsRemaining = 0,
                            errorMessage = null,
                            showRecoveryDialog = false,
                            isResettingPin = false,
                            recoveryCodeInput = "",
                            newPinInput = "",
                            confirmNewPinInput = "",
                            recoveryErrorMessage = null,
                            generatedNewRecoveryCode = generatedCode
                        )
                    }
                },
                onFailure = { error ->
                    val message = error.message?.takeIf { it.isNotBlank() }
                        ?.let { LockMessage.Plain(it) }
                        ?: LockMessage.Resource(R.string.lock_recovery_error_failed)
                    _uiState.update {
                        it.copy(
                            isResettingPin = false,
                            recoveryErrorMessage = message
                        )
                    }
                }
            )
        }
    }

    fun dismissNewRecoveryCodeDialog() {
        _uiState.update { it.copy(generatedNewRecoveryCode = null) }
    }

    companion object {
        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 6
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_DURATION_SECONDS = 30
    }
}