package com.byteall.kharismart.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byteall.kharismart.R
import com.byteall.kharismart.data.local.entity.SettingsEntity
import com.byteall.kharismart.data.repository.SettingsRepository
import com.byteall.kharismart.data.repository.UserRepository
import com.byteall.kharismart.session.AppSession
import com.byteall.kharismart.util.format.digitsToLongOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SettingsUiMessage {
    data class Resource(@StringRes val resId: Int) : SettingsUiMessage
    data class Plain(val text: String) : SettingsUiMessage
}

data class SettingsUiState(
    val isLoaded: Boolean = false,
    val storeName: String = "",
    val storeAddress: String = "",
    val receiptFooter: String = "",
    val defaultMinStockText: String = "5",
    val autoLockMinutes: Int = 5,
    val qrisBank: String = "",
    val qrisOwnerName: String = "",
    val qrisImageFilename: String? = null,
    val targetDailyText: String = "",
    val targetWeeklyText: String = "",
    val targetMonthlyText: String = "",
    val isSaving: Boolean = false,
    val isChangePinDialogOpen: Boolean = false,
    val isRegenRecoveryDialogOpen: Boolean = false,
    val generatedRecoveryCode: String? = null,
    val bannerMessage: SettingsUiMessage? = null,
    val isBannerError: Boolean = false,
    val dialogErrorMessage: SettingsUiMessage? = null
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var hasInitializedFromDb = false

    init {
        viewModelScope.launch {
            settingsRepository.observeSettings().collect { settings ->
                if (!hasInitializedFromDb) {
                    hasInitializedFromDb = true
                    applyEntityToForm(settings)
                }
            }
        }
    }

    private fun applyEntityToForm(settings: SettingsEntity) {
        _uiState.update { current ->
            current.copy(
                isLoaded = true,
                storeName = settings.storeName,
                storeAddress = settings.storeAddress.orEmpty(),
                receiptFooter = settings.receiptFooter.orEmpty(),
                defaultMinStockText = settings.defaultMinStock.toString(),
                autoLockMinutes = settings.autoLockMinutes,
                qrisBank = settings.qrisBank.orEmpty(),
                qrisOwnerName = settings.qrisOwnerName.orEmpty(),
                qrisImageFilename = settings.qrisImage,
                targetDailyText = settings.targetDaily?.toString().orEmpty(),
                targetWeeklyText = settings.targetWeekly?.toString().orEmpty(),
                targetMonthlyText = settings.targetMonthly?.toString().orEmpty()
            )
        }
    }

    fun onStoreNameChange(value: String) {
        AppSession.touch()
        _uiState.update { it.copy(storeName = value) }
    }

    fun onStoreAddressChange(value: String) {
        AppSession.touch()
        _uiState.update { it.copy(storeAddress = value) }
    }

    fun onReceiptFooterChange(value: String) {
        AppSession.touch()
        _uiState.update { it.copy(receiptFooter = value) }
    }

    fun onDefaultMinStockChange(value: String) {
        AppSession.touch()
        _uiState.update { it.copy(defaultMinStockText = value.filter { ch -> ch.isDigit() }) }
    }

    fun onAutoLockMinutesChange(minutes: Int) {
        AppSession.touch()
        _uiState.update { it.copy(autoLockMinutes = minutes) }
    }

    fun onQrisBankChange(value: String) {
        AppSession.touch()
        _uiState.update { it.copy(qrisBank = value) }
    }

    fun onQrisOwnerNameChange(value: String) {
        AppSession.touch()
        _uiState.update { it.copy(qrisOwnerName = value) }
    }

    fun onQrisImagePicked(filename: String?) {
        AppSession.touch()
        if (filename == null) {
            _uiState.update {
                it.copy(
                    bannerMessage = SettingsUiMessage.Resource(R.string.settings_err_image_save),
                    isBannerError = true
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    qrisImageFilename = filename,
                    bannerMessage = SettingsUiMessage.Resource(R.string.settings_msg_qris_uploaded),
                    isBannerError = false
                )
            }
        }
    }

    fun onRemoveQrisImage() {
        AppSession.touch()
        _uiState.update { it.copy(qrisImageFilename = null) }
    }

    fun onTargetDailyChange(value: String) {
        AppSession.touch()
        _uiState.update { it.copy(targetDailyText = value.filter { ch -> ch.isDigit() }) }
    }

    fun onTargetWeeklyChange(value: String) {
        AppSession.touch()
        _uiState.update { it.copy(targetWeeklyText = value.filter { ch -> ch.isDigit() }) }
    }

    fun onTargetMonthlyChange(value: String) {
        AppSession.touch()
        _uiState.update { it.copy(targetMonthlyText = value.filter { ch -> ch.isDigit() }) }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(bannerMessage = null) }
    }

    fun clearDialogError() {
        if (_uiState.value.dialogErrorMessage != null) {
            _uiState.update { it.copy(dialogErrorMessage = null) }
        }
    }

    fun saveAllSettings() {
        AppSession.touch()
        val snap = _uiState.value
        val trimmedName = snap.storeName.trim()
        if (trimmedName.isEmpty()) {
            _uiState.update {
                it.copy(
                    bannerMessage = SettingsUiMessage.Resource(R.string.settings_err_store_name_required),
                    isBannerError = true
                )
            }
            return
        }

        val minStock = snap.defaultMinStockText.toIntOrNull()
        if (minStock == null || minStock < 0) {
            _uiState.update {
                it.copy(
                    bannerMessage = SettingsUiMessage.Resource(R.string.settings_err_min_stock),
                    isBannerError = true
                )
            }
            return
        }

        val lockMins = snap.autoLockMinutes
        if (lockMins !in 1..60) {
            _uiState.update {
                it.copy(
                    bannerMessage = SettingsUiMessage.Resource(R.string.settings_err_auto_lock),
                    isBannerError = true
                )
            }
            return
        }

        val dailyTarget = snap.targetDailyText.trim().takeIf { it.isNotEmpty() }?.digitsToLongOrNull()
        val weeklyTarget = snap.targetWeeklyText.trim().takeIf { it.isNotEmpty() }?.digitsToLongOrNull()
        val monthlyTarget = snap.targetMonthlyText.trim().takeIf { it.isNotEmpty() }?.digitsToLongOrNull()

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, bannerMessage = null) }

            val r1 = settingsRepository.updateStoreIdentity(
                name = trimmedName,
                address = snap.storeAddress.trim().ifEmpty { null },
                footer = snap.receiptFooter.trim().ifEmpty { null }
            )
            if (r1.isFailure) {
                reportScreenFailure(r1.exceptionOrNull())
                return@launch
            }

            val r2 = settingsRepository.updateOperational(
                defaultMinStock = minStock,
                autoLockMinutes = lockMins
            )
            if (r2.isFailure) {
                reportScreenFailure(r2.exceptionOrNull())
                return@launch
            }

            val r3 = settingsRepository.updateQris(
                bank = snap.qrisBank.trim().ifEmpty { null },
                ownerName = snap.qrisOwnerName.trim().ifEmpty { null },
                imageFilename = snap.qrisImageFilename
            )
            if (r3.isFailure) {
                reportScreenFailure(r3.exceptionOrNull())
                return@launch
            }

            val r4 = settingsRepository.updateTargets(
                daily = dailyTarget,
                weekly = weeklyTarget,
                monthly = monthlyTarget
            )
            if (r4.isFailure) {
                reportScreenFailure(r4.exceptionOrNull())
                return@launch
            }

            _uiState.update {
                it.copy(
                    isSaving = false,
                    bannerMessage = SettingsUiMessage.Resource(R.string.settings_msg_saved),
                    isBannerError = false
                )
            }
        }
    }

    fun openChangePinDialog() {
        AppSession.touch()
        _uiState.update {
            it.copy(
                isChangePinDialogOpen = true,
                bannerMessage = null,
                dialogErrorMessage = null
            )
        }
    }

    fun dismissChangePinDialog() {
        _uiState.update {
            it.copy(
                isChangePinDialogOpen = false,
                dialogErrorMessage = null
            )
        }
    }

    fun submitChangeOwnPin(currentPin: String, newPin: String, confirmPin: String) {
        AppSession.touch()
        val user = AppSession.currentUser.value ?: return
        if (currentPin.length !in 4..6 || newPin.length !in 4..6) {
            _uiState.update {
                it.copy(dialogErrorMessage = SettingsUiMessage.Resource(R.string.lock_error_pin_length))
            }
            return
        }
        if (newPin != confirmPin) {
            _uiState.update {
                it.copy(dialogErrorMessage = SettingsUiMessage.Resource(R.string.settings_err_pin_mismatch))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    bannerMessage = null,
                    dialogErrorMessage = null
                )
            }
            val result = userRepository.changeOwnPin(
                userId = user.userId,
                currentPin = currentPin,
                newPin = newPin
            )
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isChangePinDialogOpen = false,
                            dialogErrorMessage = null,
                            bannerMessage = SettingsUiMessage.Resource(R.string.settings_msg_pin_changed),
                            isBannerError = false
                        )
                    }
                },
                onFailure = { err ->
                    reportDialogFailure(err)
                }
            )
        }
    }

    fun openRegenRecoveryDialog() {
        AppSession.touch()
        _uiState.update {
            it.copy(
                isRegenRecoveryDialogOpen = true,
                bannerMessage = null,
                dialogErrorMessage = null
            )
        }
    }

    fun dismissRegenRecoveryDialog() {
        _uiState.update {
            it.copy(
                isRegenRecoveryDialogOpen = false,
                dialogErrorMessage = null
            )
        }
    }

    fun submitRegenerateRecoveryCode(currentPin: String) {
        AppSession.touch()
        val user = AppSession.currentUser.value ?: return
        if (currentPin.length !in 4..6) {
            _uiState.update {
                it.copy(dialogErrorMessage = SettingsUiMessage.Resource(R.string.lock_error_pin_length))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    bannerMessage = null,
                    dialogErrorMessage = null
                )
            }
            val result = userRepository.regenerateRecoveryCode(
                adminUserId = user.userId,
                currentPin = currentPin
            )
            result.fold(
                onSuccess = { newCode ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isRegenRecoveryDialogOpen = false,
                            dialogErrorMessage = null,
                            generatedRecoveryCode = newCode
                        )
                    }
                },
                onFailure = { err ->
                    reportDialogFailure(err)
                }
            )
        }
    }

    fun acknowledgeRecoveryCode() {
        _uiState.update { it.copy(generatedRecoveryCode = null) }
    }

    private fun reportScreenFailure(err: Throwable?) {
        val msg = err?.message?.takeIf { it.isNotBlank() }
            ?.let { SettingsUiMessage.Plain(it) }
            ?: SettingsUiMessage.Resource(R.string.settings_err_generic)
        _uiState.update {
            it.copy(
                isSaving = false,
                bannerMessage = msg,
                isBannerError = true
            )
        }
    }

    private fun reportDialogFailure(err: Throwable?) {
        val msg = err?.message?.takeIf { it.isNotBlank() }
            ?.let { SettingsUiMessage.Plain(it) }
            ?: SettingsUiMessage.Resource(R.string.settings_err_generic)
        _uiState.update {
            it.copy(
                isSaving = false,
                dialogErrorMessage = msg
            )
        }
    }
}