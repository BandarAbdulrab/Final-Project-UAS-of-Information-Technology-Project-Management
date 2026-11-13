package com.byteall.kharismart.ui.users

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byteall.kharismart.R
import com.byteall.kharismart.data.local.Role
import com.byteall.kharismart.data.local.entity.UserEntity
import com.byteall.kharismart.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UserUiMessage {
    data class Plain(val text: String, val isError: Boolean = true) : UserUiMessage
    data class Resource(@StringRes val resId: Int, val isError: Boolean = false) : UserUiMessage
}

data class UserManagementUiState(
    val isAddDialogOpen: Boolean = false,
    val addName: String = "",
    val addUsername: String = "",
    val addPin: String = "",
    val addConfirmPin: String = "",
    val addRole: String = Role.CASHIER,
    val addDialogError: UserUiMessage? = null,
    val resetTargetUser: UserEntity? = null,
    val resetNewPin: String = "",
    val resetConfirmPin: String = "",
    val resetDialogError: UserUiMessage? = null,
    val statusConfirmTargetUser: UserEntity? = null,
    val isSubmitting: Boolean = false,
    val bannerMessage: UserUiMessage? = null
)

class UserManagementViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    val users: StateFlow<List<UserEntity>> = userRepository.observeAllUsers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _uiState = MutableStateFlow(UserManagementUiState())
    val uiState: StateFlow<UserManagementUiState> = _uiState.asStateFlow()

    fun dismissBanner() {
        _uiState.update { it.copy(bannerMessage = null) }
    }

    // --- Add Staff Dialog ---

    fun openAddDialog() {
        _uiState.update {
            it.copy(
                isAddDialogOpen = true,
                addName = "",
                addUsername = "",
                addPin = "",
                addConfirmPin = "",
                addRole = Role.CASHIER,
                addDialogError = null
            )
        }
    }

    fun closeAddDialog() {
        if (_uiState.value.isSubmitting) return
        _uiState.update {
            it.copy(
                isAddDialogOpen = false,
                addDialogError = null
            )
        }
    }

    fun updateAddName(value: String) {
        _uiState.update { it.copy(addName = value, addDialogError = null) }
    }

    fun updateAddUsername(value: String) {
        _uiState.update { it.copy(addUsername = value, addDialogError = null) }
    }

    fun updateAddPin(value: String) {
        val digitsOnly = value.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(addPin = digitsOnly, addDialogError = null) }
    }

    fun updateAddConfirmPin(value: String) {
        val digitsOnly = value.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(addConfirmPin = digitsOnly, addDialogError = null) }
    }

    fun updateAddRole(role: String) {
        if (role == Role.ADMIN || role == Role.CASHIER) {
            _uiState.update { it.copy(addRole = role, addDialogError = null) }
        }
    }

    fun submitAddUser() {
        val snapshot = _uiState.value
        if (snapshot.isSubmitting) return

        if (snapshot.addPin != snapshot.addConfirmPin) {
            _uiState.update {
                it.copy(addDialogError = UserUiMessage.Resource(R.string.users_err_pin_mismatch, isError = true))
            }
            return
        }

        addUser(
            name = snapshot.addName,
            username = snapshot.addUsername,
            pin = snapshot.addPin,
            role = snapshot.addRole
        )
    }

    fun addUser(name: String, username: String, pin: String, role: String) {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, addDialogError = null) }

        viewModelScope.launch {
            val result = userRepository.addUser(
                name = name,
                username = username,
                pin = pin,
                role = role
            )
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            isAddDialogOpen = false,
                            addName = "",
                            addUsername = "",
                            addPin = "",
                            addConfirmPin = "",
                            addRole = Role.CASHIER,
                            addDialogError = null,
                            bannerMessage = UserUiMessage.Resource(
                                resId = R.string.users_msg_added_success,
                                isError = false
                            )
                        )
                    }
                },
                onFailure = { error ->
                    val msg = error.message?.takeIf { it.isNotBlank() }
                        ?.let { UserUiMessage.Plain(it, isError = true) }
                        ?: UserUiMessage.Resource(R.string.users_err_generic, isError = true)
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            addDialogError = msg
                        )
                    }
                }
            )
        }
    }

    // --- Reset PIN Dialog ---

    fun openResetPinDialog(user: UserEntity) {
        _uiState.update {
            it.copy(
                resetTargetUser = user,
                resetNewPin = "",
                resetConfirmPin = "",
                resetDialogError = null
            )
        }
    }

    fun closeResetPinDialog() {
        if (_uiState.value.isSubmitting) return
        _uiState.update {
            it.copy(
                resetTargetUser = null,
                resetNewPin = "",
                resetConfirmPin = "",
                resetDialogError = null
            )
        }
    }

    fun updateResetNewPin(value: String) {
        val digitsOnly = value.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(resetNewPin = digitsOnly, resetDialogError = null) }
    }

    fun updateResetConfirmPin(value: String) {
        val digitsOnly = value.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(resetConfirmPin = digitsOnly, resetDialogError = null) }
    }

    fun submitResetPin() {
        val snapshot = _uiState.value
        val target = snapshot.resetTargetUser ?: return
        if (snapshot.isSubmitting) return

        if (snapshot.resetNewPin != snapshot.resetConfirmPin) {
            _uiState.update {
                it.copy(resetDialogError = UserUiMessage.Resource(R.string.users_err_pin_mismatch, isError = true))
            }
            return
        }

        resetPin(userId = target.userId, newPin = snapshot.resetNewPin)
    }

    fun resetPin(userId: Long, newPin: String) {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, resetDialogError = null) }

        viewModelScope.launch {
            val result = userRepository.resetPin(userId = userId, newPin = newPin)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            resetTargetUser = null,
                            resetNewPin = "",
                            resetConfirmPin = "",
                            resetDialogError = null,
                            bannerMessage = UserUiMessage.Resource(
                                resId = R.string.users_msg_pin_reset_success,
                                isError = false
                            )
                        )
                    }
                },
                onFailure = { error ->
                    val msg = error.message?.takeIf { it.isNotBlank() }
                        ?.let { UserUiMessage.Plain(it, isError = true) }
                        ?: UserUiMessage.Resource(R.string.users_err_generic, isError = true)
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            resetDialogError = msg
                        )
                    }
                }
            )
        }
    }

    // --- Active / Inactive Status Toggle ---

    fun requestToggleUserStatus(user: UserEntity) {
        if (_uiState.value.isSubmitting) return
        _uiState.update {
            it.copy(
                statusConfirmTargetUser = user,
                bannerMessage = null
            )
        }
    }

    fun cancelToggleUserStatus() {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(statusConfirmTargetUser = null) }
    }

    fun confirmToggleUserStatus() {
        val target = _uiState.value.statusConfirmTargetUser ?: return
        toggleUserStatus(target)
    }

    fun toggleUserStatus(user: UserEntity) {
        if (_uiState.value.isSubmitting) return
        val newActiveState = user.status != 1
        _uiState.update { it.copy(isSubmitting = true, statusConfirmTargetUser = null) }

        viewModelScope.launch {
            val result = userRepository.setActive(userId = user.userId, active = newActiveState)
            result.fold(
                onSuccess = {
                    val successRes = if (newActiveState) {
                        R.string.users_msg_status_activated
                    } else {
                        R.string.users_msg_status_deactivated
                    }
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            bannerMessage = UserUiMessage.Resource(resId = successRes, isError = false)
                        )
                    }
                },
                onFailure = { error ->
                    val msg = error.message?.takeIf { it.isNotBlank() }
                        ?.let { UserUiMessage.Plain(it, isError = true) }
                        ?: UserUiMessage.Resource(R.string.users_err_generic, isError = true)
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            bannerMessage = msg
                        )
                    }
                }
            )
        }
    }
}