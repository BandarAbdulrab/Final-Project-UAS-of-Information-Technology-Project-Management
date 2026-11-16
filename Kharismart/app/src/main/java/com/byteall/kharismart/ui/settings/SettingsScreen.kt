package com.byteall.kharismart.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.byteall.kharismart.R
import com.byteall.kharismart.di.appViewModel
import com.byteall.kharismart.session.AppSession
import com.byteall.kharismart.util.format.digitsToLongOrNull
import com.byteall.kharismart.util.format.toRupiah
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = appViewModel { container ->
        SettingsViewModel(
            settingsRepository = container.settingsRepository,
            userRepository = container.userRepository
        )
    }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pickQrisLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val savedFilename = saveQrisImageToInternalStorage(context, uri)
                viewModel.onQrisImagePicked(savedFilename)
            }
        }
    }

    SettingsContent(
        state = state,
        qrisFile = remember(state.qrisImageFilename) {
            resolveQrisImageFile(context, state.qrisImageFilename)
        },
        onStoreNameChange = viewModel::onStoreNameChange,
        onStoreAddressChange = viewModel::onStoreAddressChange,
        onReceiptFooterChange = viewModel::onReceiptFooterChange,
        onDefaultMinStockChange = viewModel::onDefaultMinStockChange,
        onAutoLockMinutesChange = viewModel::onAutoLockMinutesChange,
        onQrisBankChange = viewModel::onQrisBankChange,
        onQrisOwnerNameChange = viewModel::onQrisOwnerNameChange,
        onPickQrisImage = {
            pickQrisLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        onRemoveQrisImage = viewModel::onRemoveQrisImage,
        onTargetDailyChange = viewModel::onTargetDailyChange,
        onTargetWeeklyChange = viewModel::onTargetWeeklyChange,
        onTargetMonthlyChange = viewModel::onTargetMonthlyChange,
        onSaveAll = viewModel::saveAllSettings,
        onOpenChangePin = viewModel::openChangePinDialog,
        onDismissChangePin = viewModel::dismissChangePinDialog,
        onSubmitChangePin = viewModel::submitChangeOwnPin,
        onOpenRegenRecovery = viewModel::openRegenRecoveryDialog,
        onDismissRegenRecovery = viewModel::dismissRegenRecoveryDialog,
        onSubmitRegenRecovery = viewModel::submitRegenerateRecoveryCode,
        onAcknowledgeRecoveryCode = viewModel::acknowledgeRecoveryCode,
        onClearDialogError = viewModel::clearDialogError,
        onLockNow = { AppSession.signOut() },
        onDismissBanner = viewModel::dismissBanner
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    state: SettingsUiState,
    qrisFile: File?,
    onStoreNameChange: (String) -> Unit,
    onStoreAddressChange: (String) -> Unit,
    onReceiptFooterChange: (String) -> Unit,
    onDefaultMinStockChange: (String) -> Unit,
    onAutoLockMinutesChange: (Int) -> Unit,
    onQrisBankChange: (String) -> Unit,
    onQrisOwnerNameChange: (String) -> Unit,
    onPickQrisImage: () -> Unit,
    onRemoveQrisImage: () -> Unit,
    onTargetDailyChange: (String) -> Unit,
    onTargetWeeklyChange: (String) -> Unit,
    onTargetMonthlyChange: (String) -> Unit,
    onSaveAll: () -> Unit,
    onOpenChangePin: () -> Unit,
    onDismissChangePin: () -> Unit,
    onSubmitChangePin: (String, String, String) -> Unit,
    onOpenRegenRecovery: () -> Unit,
    onDismissRegenRecovery: () -> Unit,
    onSubmitRegenRecovery: (String) -> Unit,
    onAcknowledgeRecoveryCode: () -> Unit,
    onClearDialogError: () -> Unit,
    onLockNow: () -> Unit,
    onDismissBanner: () -> Unit
) {
    val autoLockPresets = listOf(1, 2, 5, 10, 15, 30, 60)
    var autoLockExpanded by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.settings_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onSaveAll,
                        enabled = !state.isSaving
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_save_all_button))
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.settings_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onSaveAll,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_save_all_button))
                    }
                }
            }

            state.bannerMessage?.let { msg ->
                val text = when (msg) {
                    is SettingsUiMessage.Plain -> msg.text
                    is SettingsUiMessage.Resource -> stringResource(msg.resId)
                }
                SettingsFeedbackBanner(
                    message = text,
                    isError = state.isBannerError,
                    onDismiss = onDismissBanner
                )
            }

            // 1. Store Identity Section
            SettingsSectionCard(title = stringResource(R.string.settings_section_identity)) {
                OutlinedTextField(
                    value = state.storeName,
                    onValueChange = onStoreNameChange,
                    label = { Text(stringResource(R.string.settings_field_store_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.storeAddress,
                    onValueChange = onStoreAddressChange,
                    label = { Text(stringResource(R.string.settings_field_store_address)) },
                    placeholder = { Text(stringResource(R.string.settings_field_store_address_placeholder)) },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.receiptFooter,
                    onValueChange = onReceiptFooterChange,
                    label = { Text(stringResource(R.string.settings_field_receipt_footer)) },
                    placeholder = { Text(stringResource(R.string.settings_field_receipt_footer_placeholder)) },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 2. Operational Section
            SettingsSectionCard(title = stringResource(R.string.settings_section_operational)) {
                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = state.defaultMinStockText,
                            onValueChange = onDefaultMinStockChange,
                            label = { Text(stringResource(R.string.settings_field_min_stock)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        AutoLockDropdownField(
                            autoLockMinutes = state.autoLockMinutes,
                            autoLockPresets = autoLockPresets,
                            expanded = autoLockExpanded,
                            onExpandedChange = { autoLockExpanded = it },
                            onSelectMinutes = onAutoLockMinutesChange,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = state.defaultMinStockText,
                            onValueChange = onDefaultMinStockChange,
                            label = { Text(stringResource(R.string.settings_field_min_stock)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        AutoLockDropdownField(
                            autoLockMinutes = state.autoLockMinutes,
                            autoLockPresets = autoLockPresets,
                            expanded = autoLockExpanded,
                            onExpandedChange = { autoLockExpanded = it },
                            onSelectMinutes = onAutoLockMinutesChange,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 3. QRIS Section
            SettingsSectionCard(title = stringResource(R.string.settings_section_qris)) {
                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = state.qrisBank,
                            onValueChange = onQrisBankChange,
                            label = { Text(stringResource(R.string.settings_field_qris_bank)) },
                            placeholder = { Text(stringResource(R.string.settings_field_qris_bank_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = state.qrisOwnerName,
                            onValueChange = onQrisOwnerNameChange,
                            label = { Text(stringResource(R.string.settings_field_qris_owner)) },
                            placeholder = { Text(stringResource(R.string.settings_field_qris_owner_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = state.qrisBank,
                            onValueChange = onQrisBankChange,
                            label = { Text(stringResource(R.string.settings_field_qris_bank)) },
                            placeholder = { Text(stringResource(R.string.settings_field_qris_bank_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = state.qrisOwnerName,
                            onValueChange = onQrisOwnerNameChange,
                            label = { Text(stringResource(R.string.settings_field_qris_owner)) },
                            placeholder = { Text(stringResource(R.string.settings_field_qris_owner_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.settings_qris_image_label),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        if (qrisFile != null && qrisFile.exists()) {
                            AsyncImage(
                                model = qrisFile,
                                contentDescription = stringResource(R.string.settings_qris_image_label),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCode2,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.settings_qris_no_image),
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onPickQrisImage,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.settings_qris_pick_button),
                                textAlign = TextAlign.Center
                            )
                        }
                        if (state.qrisImageFilename != null) {
                            TextButton(
                                onClick = onRemoveQrisImage,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.settings_qris_remove_button))
                            }
                        }
                    }
                }
            }

            // 4. Revenue Targets Section
            SettingsSectionCard(title = stringResource(R.string.settings_section_targets)) {
                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TargetAmountField(
                            value = state.targetDailyText,
                            onValueChange = onTargetDailyChange,
                            label = stringResource(R.string.settings_field_target_daily),
                            modifier = Modifier.weight(1f)
                        )
                        TargetAmountField(
                            value = state.targetWeeklyText,
                            onValueChange = onTargetWeeklyChange,
                            label = stringResource(R.string.settings_field_target_weekly),
                            modifier = Modifier.weight(1f)
                        )
                        TargetAmountField(
                            value = state.targetMonthlyText,
                            onValueChange = onTargetMonthlyChange,
                            label = stringResource(R.string.settings_field_target_monthly),
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TargetAmountField(
                            value = state.targetDailyText,
                            onValueChange = onTargetDailyChange,
                            label = stringResource(R.string.settings_field_target_daily),
                            modifier = Modifier.fillMaxWidth()
                        )
                        TargetAmountField(
                            value = state.targetWeeklyText,
                            onValueChange = onTargetWeeklyChange,
                            label = stringResource(R.string.settings_field_target_weekly),
                            modifier = Modifier.fillMaxWidth()
                        )
                        TargetAmountField(
                            value = state.targetMonthlyText,
                            onValueChange = onTargetMonthlyChange,
                            label = stringResource(R.string.settings_field_target_monthly),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 5. Security & Session Section
            SettingsSectionCard(title = stringResource(R.string.settings_section_account)) {
                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenChangePin,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_btn_change_pin),
                                textAlign = TextAlign.Center
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenRegenRecovery,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_btn_regen_recovery),
                                textAlign = TextAlign.Center
                            )
                        }
                        Button(
                            onClick = onLockNow,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.settings_btn_lock_now),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onOpenChangePin,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_btn_change_pin))
                        }
                        OutlinedButton(
                            onClick = onOpenRegenRecovery,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_btn_regen_recovery))
                        }
                        Button(
                            onClick = onLockNow,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.settings_btn_lock_now))
                        }
                    }
                }
            }

            Button(
                onClick = onSaveAll,
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_save_all_button))
            }
        }
    }

    if (state.isChangePinDialogOpen) {
        ChangeOwnPinDialog(
            isSaving = state.isSaving,
            dialogErrorMessage = state.dialogErrorMessage,
            onClearDialogError = onClearDialogError,
            onDismiss = onDismissChangePin,
            onSubmit = onSubmitChangePin
        )
    }

    if (state.isRegenRecoveryDialogOpen) {
        RegenerateRecoveryCodeDialog(
            isSaving = state.isSaving,
            dialogErrorMessage = state.dialogErrorMessage,
            onClearDialogError = onClearDialogError,
            onDismiss = onDismissRegenRecovery,
            onSubmit = onSubmitRegenRecovery
        )
    }

    state.generatedRecoveryCode?.let { code ->
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 480.dp)
                .padding(vertical = 16.dp),
            title = { Text(stringResource(R.string.lock_new_recovery_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.lock_new_recovery_warning))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = code,
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = onAcknowledgeRecoveryCode) {
                    Text(stringResource(R.string.lock_new_recovery_acknowledge))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoLockDropdownField(
    autoLockMinutes: Int,
    autoLockPresets: List<Int>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectMinutes: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { onExpandedChange(!expanded) },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = stringResource(R.string.settings_auto_lock_option, autoLockMinutes),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_field_auto_lock)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            autoLockPresets.forEach { mins ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_auto_lock_option, mins)) },
                    onClick = {
                        onSelectMinutes(mins)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            content()
        }
    }
}

@Composable
private fun TargetAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val parsed = value.digitsToLongOrNull()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        supportingText = {
            Text(
                text = if (parsed != null && parsed > 0L) {
                    parsed.toRupiah()
                } else {
                    stringResource(R.string.settings_target_unset)
                }
            )
        },
        modifier = modifier
    )
}

@Composable
private fun ChangeOwnPinDialog(
    isSaving: Boolean,
    dialogErrorMessage: SettingsUiMessage?,
    onClearDialogError: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String) -> Unit
) {
    var currentPin by rememberSaveable { mutableStateOf("") }
    var newPin by rememberSaveable { mutableStateOf("") }
    var confirmPin by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .widthIn(max = 480.dp)
            .padding(vertical = 16.dp),
        title = { Text(stringResource(R.string.settings_change_pin_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                dialogErrorMessage?.let { msg ->
                    val errText = when (msg) {
                        is SettingsUiMessage.Plain -> msg.text
                        is SettingsUiMessage.Resource -> stringResource(msg.resId)
                    }
                    SettingsFeedbackBanner(
                        message = errText,
                        isError = true,
                        onDismiss = onClearDialogError
                    )
                }

                OutlinedTextField(
                    value = currentPin,
                    onValueChange = {
                        currentPin = it.filter { ch -> ch.isDigit() }.take(6)
                        onClearDialogError()
                    },
                    label = { Text(stringResource(R.string.settings_field_current_pin)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = {
                        newPin = it.filter { ch -> ch.isDigit() }.take(6)
                        onClearDialogError()
                    },
                    label = { Text(stringResource(R.string.settings_field_new_pin)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = {
                        confirmPin = it.filter { ch -> ch.isDigit() }.take(6)
                        onClearDialogError()
                    },
                    label = { Text(stringResource(R.string.settings_field_confirm_pin)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(currentPin, newPin, confirmPin) },
                enabled = !isSaving
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun RegenerateRecoveryCodeDialog(
    isSaving: Boolean,
    dialogErrorMessage: SettingsUiMessage?,
    onClearDialogError: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var currentPin by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .widthIn(max = 480.dp)
            .padding(vertical = 16.dp),
        title = { Text(stringResource(R.string.settings_regen_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                dialogErrorMessage?.let { msg ->
                    val errText = when (msg) {
                        is SettingsUiMessage.Plain -> msg.text
                        is SettingsUiMessage.Resource -> stringResource(msg.resId)
                    }
                    SettingsFeedbackBanner(
                        message = errText,
                        isError = true,
                        onDismiss = onClearDialogError
                    )
                }

                Text(stringResource(R.string.settings_regen_dialog_desc))
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = {
                        currentPin = it.filter { ch -> ch.isDigit() }.take(6)
                        onClearDialogError()
                    },
                    label = { Text(stringResource(R.string.settings_field_current_pin)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(currentPin) },
                enabled = !isSaving
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun SettingsFeedbackBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_close)
                )
            }
        }
    }
}

private fun resolveQrisImageFile(context: Context, filename: String?): File? {
    if (filename.isNullOrBlank()) return null
    val photosDir = File(context.filesDir, "product_photos")
    val inPhotos = File(photosDir, filename)
    if (inPhotos.exists()) return inPhotos
    val direct = File(context.filesDir, filename)
    if (direct.exists()) return direct
    val absolute = File(filename)
    return if (absolute.exists()) absolute else inPhotos
}

private suspend fun saveQrisImageToInternalStorage(context: Context, sourceUri: Uri): String? =
    withContext(Dispatchers.IO) {
        try {
            val originalBitmap = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return@withContext null

            val maxDimension = 1200
            val largestSide = max(originalBitmap.width, originalBitmap.height)
            val scaledBitmap = if (largestSide > maxDimension) {
                val scale = maxDimension.toFloat() / largestSide.toFloat()
                val targetWidth = (originalBitmap.width * scale).roundToInt().coerceAtLeast(1)
                val targetHeight = (originalBitmap.height * scale).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)
            } else {
                originalBitmap
            }

            val maxBytes = 1_000_000
            var quality = 85
            val buffer = ByteArrayOutputStream()
            do {
                buffer.reset()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, buffer)
                quality -= 10
            } while (buffer.size() > maxBytes && quality >= 30)

            val photosDir = File(context.filesDir, "product_photos")
            if (!photosDir.exists()) {
                photosDir.mkdirs()
            }

            val filename = "qris_${UUID.randomUUID()}.jpg"
            val destFile = File(photosDir, filename)
            FileOutputStream(destFile).use { out ->
                out.write(buffer.toByteArray())
                out.flush()
            }
            filename
        } catch (_: Exception) {
            null
        }
    }