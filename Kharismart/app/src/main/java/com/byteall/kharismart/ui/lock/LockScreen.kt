package com.byteall.kharismart.ui.lock

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byteall.kharismart.R
import com.byteall.kharismart.data.local.Role
import com.byteall.kharismart.data.local.entity.UserEntity
import com.byteall.kharismart.di.appViewModel

@Composable
fun LockScreen(
    modifier: Modifier = Modifier,
    viewModel: LockViewModel = appViewModel { container ->
        LockViewModel(container.userRepository)
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LockScreenContent(
        uiState = uiState,
        onSelectUser = viewModel::selectUser,
        onDigitPressed = viewModel::onDigitPressed,
        onBackspacePressed = viewModel::onBackspacePressed,
        onClearPin = viewModel::onClearPin,
        onSubmitPin = viewModel::submitPin,
        onOpenRecoveryDialog = viewModel::openRecoveryDialog,
        modifier = modifier
    )

    if (uiState.showRecoveryDialog) {
        AdminRecoveryDialog(
            admins = uiState.activeAdmins,
            selectedAdminId = uiState.recoveryAdminUserId,
            recoveryCode = uiState.recoveryCodeInput,
            newPin = uiState.newPinInput,
            confirmNewPin = uiState.confirmNewPinInput,
            isSubmitting = uiState.isResettingPin,
            errorMessage = uiState.recoveryErrorMessage,
            onSelectAdmin = viewModel::selectRecoveryAdmin,
            onRecoveryCodeChanged = viewModel::onRecoveryCodeChanged,
            onNewPinChanged = viewModel::onNewPinChanged,
            onConfirmNewPinChanged = viewModel::onConfirmNewPinChanged,
            onConfirm = viewModel::submitAdminRecovery,
            onDismiss = viewModel::dismissRecoveryDialog
        )
    }

    val newRecoveryCode = uiState.generatedNewRecoveryCode
    if (newRecoveryCode != null) {
        NewRecoveryCodeDialog(
            newRecoveryCode = newRecoveryCode,
            onDismiss = viewModel::dismissNewRecoveryCodeDialog
        )
    }
}

@Composable
fun LockScreenContent(
    uiState: LockUiState,
    onSelectUser: (Long) -> Unit,
    onDigitPressed: (Char) -> Unit,
    onBackspacePressed: () -> Unit,
    onClearPin: () -> Unit,
    onSubmitPin: () -> Unit,
    onOpenRecoveryDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp)
        ) {
            val isTabletLandscape = maxWidth >= 720.dp

            if (isTabletLandscape) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center
                    ) {
                        StoreBrandHeader()
                        Spacer(modifier = Modifier.height(24.dp))
                        StaffSelectorSection(
                            users = uiState.activeUsers,
                            selectedUserId = uiState.selectedUserId,
                            enabled = !uiState.isLockedOut && !uiState.isVerifying,
                            onSelectUser = onSelectUser
                        )
                        if (uiState.activeAdmins.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            ForgotPinButton(
                                enabled = !uiState.isVerifying,
                                onClick = onOpenRecoveryDialog
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        PinPadCard(
                            uiState = uiState,
                            onDigitPressed = onDigitPressed,
                            onBackspacePressed = onBackspacePressed,
                            onClearPin = onClearPin,
                            onSubmitPin = onSubmitPin,
                            modifier = Modifier.widthIn(max = 420.dp)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    StoreBrandHeader(modifier = Modifier.widthIn(max = 480.dp))
                    StaffSelectorSection(
                        users = uiState.activeUsers,
                        selectedUserId = uiState.selectedUserId,
                        enabled = !uiState.isLockedOut && !uiState.isVerifying,
                        onSelectUser = onSelectUser,
                        modifier = Modifier.widthIn(max = 480.dp)
                    )
                    PinPadCard(
                        uiState = uiState,
                        onDigitPressed = onDigitPressed,
                        onBackspacePressed = onBackspacePressed,
                        onClearPin = onClearPin,
                        onSubmitPin = onSubmitPin,
                        modifier = Modifier.widthIn(max = 420.dp)
                    )
                    if (uiState.activeAdmins.isNotEmpty()) {
                        ForgotPinButton(
                            enabled = !uiState.isVerifying,
                            onClick = onOpenRecoveryDialog
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreBrandHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Storefront,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Column {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.lock_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StaffSelectorSection(
    users: List<UserEntity>,
    selectedUserId: Long?,
    enabled: Boolean,
    onSelectUser: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.lock_select_staff_label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (users.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = stringResource(R.string.lock_no_active_users),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            users.forEach { user ->
                val isSelected = user.userId == selectedUserId
                StaffProfileCard(
                    user = user,
                    selected = isSelected,
                    enabled = enabled,
                    onClick = { onSelectUser(user.userId) }
                )
            }
        }
    }
}

@Composable
private fun StaffProfileCard(
    user: UserEntity,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val isAdmin = user.role == Role.ADMIN
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val border = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isAdmin) {
                            Icons.Filled.AdminPanelSettings
                        } else {
                            Icons.Filled.Person
                        },
                        contentDescription = null,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isAdmin) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            ) {
                Text(
                    text = stringResource(
                        if (isAdmin) R.string.role_admin else R.string.role_cashier
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isAdmin) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PinPadCard(
    uiState: LockUiState,
    onDigitPressed: (Char) -> Unit,
    onBackspacePressed: () -> Unit,
    onClearPin: () -> Unit,
    onSubmitPin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = uiState.selectedUser?.let {
                        stringResource(R.string.lock_enter_pin_for, it.name)
                    } ?: stringResource(R.string.lock_select_staff_label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            PinDotsIndicator(
                enteredLength = uiState.pinInput.length,
                maxDigits = LockViewModel.MAX_PIN_LENGTH
            )

            Text(
                text = stringResource(R.string.lock_pin_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (uiState.isLockedOut) {
                LockStatusBanner(
                    text = stringResource(
                        R.string.lock_lockout_message,
                        uiState.lockoutSecondsRemaining
                    ),
                    isError = true
                )
            } else if (uiState.errorMessage != null) {
                LockStatusBanner(
                    text = resolveLockMessage(uiState.errorMessage),
                    isError = true
                )
            }

            NumericKeypad(
                enabled = !uiState.isLockedOut && !uiState.isVerifying && uiState.selectedUser != null,
                canBackspace = uiState.pinInput.isNotEmpty(),
                canSubmit = uiState.canSubmitPin,
                isVerifying = uiState.isVerifying,
                onDigitPressed = onDigitPressed,
                onBackspacePressed = onBackspacePressed,
                onSubmitPin = onSubmitPin
            )

            if (uiState.pinInput.isNotEmpty() && !uiState.isLockedOut && !uiState.isVerifying) {
                TextButton(onClick = onClearPin) {
                    Text(text = stringResource(R.string.lock_clear_pin))
                }
            }
        }
    }
}

@Composable
private fun PinDotsIndicator(
    enteredLength: Int,
    maxDigits: Int,
    modifier: Modifier = Modifier
) {
    val description = stringResource(R.string.lock_pin_dots_cd, enteredLength, maxDigits)
    Row(
        modifier = modifier
            .padding(vertical = 4.dp)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(maxDigits) { index ->
            val filled = index < enteredLength
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(
                        if (filled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (filled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun LockStatusBanner(
    text: String,
    isError: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
            )
        }
    }
}

@Composable
private fun NumericKeypad(
    enabled: Boolean,
    canBackspace: Boolean,
    canSubmit: Boolean,
    isVerifying: Boolean,
    onDigitPressed: (Char) -> Unit,
    onBackspacePressed: () -> Unit,
    onSubmitPin: () -> Unit
) {
    val digitRows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9')
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        digitRows.forEach { rowDigits ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowDigits.forEach { digit ->
                    KeypadDigitButton(
                        digit = digit,
                        enabled = enabled,
                        onClick = { onDigitPressed(digit) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onBackspacePressed,
                enabled = enabled && canBackspace,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = stringResource(R.string.lock_backspace_cd),
                    modifier = Modifier.size(24.dp)
                )
            }

            KeypadDigitButton(
                digit = '0',
                enabled = enabled,
                onClick = { onDigitPressed('0') },
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = onSubmitPin,
                enabled = canSubmit,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.lock_ok),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadDigitButton(
    digit: Char,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier.heightIn(min = 56.dp)
    ) {
        Text(
            text = digit.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ForgotPinButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled
    ) {
        Icon(
            imageVector = Icons.Filled.Key,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.lock_forgot_admin_pin),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun AdminRecoveryDialog(
    admins: List<UserEntity>,
    selectedAdminId: Long?,
    recoveryCode: String,
    newPin: String,
    confirmNewPin: String,
    isSubmitting: Boolean,
    errorMessage: LockMessage?,
    onSelectAdmin: (Long) -> Unit,
    onRecoveryCodeChanged: (String) -> Unit,
    onNewPinChanged: (String) -> Unit,
    onConfirmNewPinChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var pinVisible by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.lock_recovery_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.lock_recovery_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (admins.size > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        admins.forEach { admin ->
                            FilterChip(
                                selected = admin.userId == selectedAdminId,
                                onClick = { onSelectAdmin(admin.userId) },
                                enabled = !isSubmitting,
                                label = { Text(text = admin.name) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = recoveryCode,
                    onValueChange = onRecoveryCodeChanged,
                    enabled = !isSubmitting,
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.lock_recovery_code_label)) },
                    placeholder = { Text(text = stringResource(R.string.lock_recovery_code_placeholder)) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = newPin,
                    onValueChange = onNewPinChanged,
                    enabled = !isSubmitting,
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.lock_recovery_new_pin_label)) },
                    visualTransformation = if (pinVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { pinVisible = !pinVisible }) {
                            Icon(
                                imageVector = if (pinVisible) {
                                    Icons.Filled.VisibilityOff
                                } else {
                                    Icons.Filled.Visibility
                                },
                                contentDescription = stringResource(
                                    if (pinVisible) {
                                        R.string.lock_hide_pin_cd
                                    } else {
                                        R.string.lock_show_pin_cd
                                    }
                                )
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmNewPin,
                    onValueChange = onConfirmNewPinChanged,
                    enabled = !isSubmitting,
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.lock_recovery_confirm_pin_label)) },
                    visualTransformation = if (pinVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    LockStatusBanner(
                        text = resolveLockMessage(errorMessage),
                        isError = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSubmitting
            ) {
                Text(text = stringResource(R.string.lock_recovery_submit))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text(text = stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun NewRecoveryCodeDialog(
    newRecoveryCode: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.lock_new_recovery_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.lock_new_recovery_warning),
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = newRecoveryCode,
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(text = stringResource(R.string.lock_new_recovery_acknowledge))
            }
        }
    )
}

@Composable
private fun resolveLockMessage(message: LockMessage): String {
    return when (message) {
        is LockMessage.Resource -> {
            if (message.args.isEmpty()) {
                stringResource(message.resId)
            } else {
                stringResource(message.resId, *message.args.toTypedArray())
            }
        }
        is LockMessage.Plain -> message.text
    }
}