package com.byteall.kharismart.ui.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byteall.kharismart.data.backup.BackupManager
import com.byteall.kharismart.data.local.KharismartDatabase
import com.byteall.kharismart.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BackupUiState(
    val lastBackupAt: String? = null,
    val backupFolder: String? = null,
    val isWorking: Boolean = false,
    val pendingRestoreUri: Uri? = null,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val restoreSucceeded: Boolean = false
)

class BackupViewModel(
    private val database: KharismartDatabase,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val isWorking = MutableStateFlow(false)
    private val pendingRestoreUri = MutableStateFlow<Uri?>(null)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val isError = MutableStateFlow(false)
    private val restoreSucceeded = MutableStateFlow(false)

    val uiState: StateFlow<BackupUiState> = combine(
        settingsRepository.observeSettings(),
        isWorking,
        pendingRestoreUri,
        combine(statusMessage, isError, restoreSucceeded) { msg, err, done ->
            Triple(msg, err, done)
        }
    ) { settings, working, pendingUri, (msg, err, done) ->
        BackupUiState(
            lastBackupAt = settings.lastBackupAt,
            backupFolder = settings.backupFolder,
            isWorking = working,
            pendingRestoreUri = pendingUri,
            statusMessage = msg,
            isError = err,
            restoreSucceeded = done
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BackupUiState()
    )

    fun createBackup(context: Context, destinationUri: Uri, successTemplate: String) {
        if (isWorking.value) return
        isWorking.value = true
        statusMessage.value = null
        viewModelScope.launch {
            val result = BackupManager.createBackup(
                context = context.applicationContext,
                database = database,
                destinationUri = destinationUri
            )
            result.onSuccess { fileName ->
                settingsRepository.recordBackup(destinationUri.toString())
                isError.value = false
                statusMessage.value = String.format(successTemplate, fileName)
            }.onFailure { err ->
                isError.value = true
                statusMessage.value = err.message ?: "Gagal membuat file cadangan."
            }
            isWorking.value = false
        }
    }

    fun requestRestoreConfirmation(sourceUri: Uri) {
        if (isWorking.value) return
        pendingRestoreUri.value = sourceUri
    }

    fun dismissRestoreConfirmation() {
        pendingRestoreUri.value = null
    }

    fun confirmRestore(context: Context, successMsg: String) {
        val targetUri = pendingRestoreUri.value ?: return
        pendingRestoreUri.value = null
        if (isWorking.value) return

        isWorking.value = true
        statusMessage.value = null
        viewModelScope.launch {
            val result = BackupManager.restoreBackup(
                context = context.applicationContext,
                backupZipUri = targetUri
            )
            result.onSuccess {
                isError.value = false
                statusMessage.value = successMsg
                restoreSucceeded.value = true
            }.onFailure { err ->
                isError.value = true
                statusMessage.value = err.message ?: "Gagal memulihkan cadangan."
            }
            isWorking.value = false
        }
    }

    fun clearMessage() {
        statusMessage.value = null
        isError.value = false
    }
}
