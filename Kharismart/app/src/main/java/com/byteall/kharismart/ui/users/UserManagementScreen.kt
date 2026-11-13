package com.byteall.kharismart.ui.users

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byteall.kharismart.R
import com.byteall.kharismart.data.local.Role
import com.byteall.kharismart.data.local.entity.UserEntity
import com.byteall.kharismart.di.appViewModel

@Composable
fun UserManagementScreen(
    viewModel: UserManagementViewModel = appViewModel { container ->
        UserManagementViewModel(container.userRepository)
    }
) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    UserManagementContent(
        users = users,
        uiState = uiState,
        onOpenAddDialog = viewModel::openAddDialog,
        onCloseAddDialog = viewModel::closeAddDialog,
        onAddNameChange = viewModel::updateAddName,
        onAddUsernameChange = viewModel::updateAddUsername,
        onAddPinChange = viewModel::updateAddPin,
        onAddConfirmPinChange = viewModel::updateAddConfirmPin,
        onAddRoleChange = viewModel::updateAddRole,
        onSubmitAddUser = viewModel::submitAddUser,
        onOpenResetPinDialog = viewModel::openResetPinDialog,
        onCloseResetPinDialog = viewModel::closeResetPinDialog,
        onResetNewPinChange = viewModel::updateResetNewPin,
        onResetConfirmPinChange = viewModel::updateResetConfirmPin,
        onSubmitResetPin = viewModel::submitResetPin,
        onRequestToggleStatus = viewModel::requestToggleUserStatus,
        onConfirmToggleStatus = viewModel::confirmToggleUserStatus,
        onCancelToggleStatus = viewModel::cancelToggleUserStatus,
        onDismissBanner = viewModel::dismissBanner
    )
}

@Composable
fun UserManagementContent(
    users: List<UserEntity>,
    uiState: UserManagementUiState,
    onOpenAddDialog: () -> Unit,
    onCloseAddDialog: () -> Unit,
    onAddNameChange: (String) -> Unit,
    onAddUsernameChange: (String) -> Unit,
    onAddPinChange: (String) -> Unit,
    onAddConfirmPinChange: (String) -> Unit,
    onAddRoleChange: (String) -> Unit,
    onSubmitAddUser: () -> Unit,
    onOpenResetPinDialog: (UserEntity) -> Unit,
    onCloseResetPinDialog: () -> Unit,
    onResetNewPinChange: (String) -> Unit,
    onResetConfirmPinChange: (String) -> Unit,
    onSubmitResetPin: () -> Unit,
    onRequestToggleStatus: (UserEntity) -> Unit,
    onConfirmToggleStatus: () -> Unit,
    onCancelToggleStatus: () -> Unit,
    onDismissBanner: () -> Unit
) {
    val activeCount = users.count { it.status == 1 }
    val activeAdminCount = users.count { it.status == 1 && it.role == Role.ADMIN }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.users_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.users_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onOpenAddDialog,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.users_add_button),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // Summary Chips Row (horizontally scrollable on narrow screens so text never wraps)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryBadge(text = stringResource(R.string.users_summary_total, users.size))
            SummaryBadge(text = stringResource(R.string.users_summary_active, activeCount))
            SummaryBadge(text = stringResource(R.string.users_summary_admins, activeAdminCount))
        }

        // Status / Error Banner
        uiState.bannerMessage?.let { message ->
            UserStatusBanner(
                message = resolveUserMessage(message),
                isError = when (message) {
                    is UserUiMessage.Plain -> message.isError
                    is UserUiMessage.Resource -> message.isError
                },
                onDismiss = onDismissBanner
            )
        }

        // Responsive Staff Table / Card List
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideTable = maxWidth >= 560.dp

                Column(modifier = Modifier.fillMaxSize()) {
                    if (isWideTable) {
                        // Wide / Tablet Table Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.users_col_name),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(2.2f)
                            )
                            Text(
                                text = stringResource(R.string.users_col_role),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1.2f)
                            )
                            Text(
                                text = stringResource(R.string.users_col_status),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1.4f)
                            )
                            Text(
                                text = stringResource(R.string.users_col_actions),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1.5f)
                            )
                        }
                        HorizontalDivider()
                    }

                    if (users.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.users_empty_state),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = users,
                                key = { it.userId }
                            ) { user ->
                                if (isWideTable) {
                                    StaffTableRowWide(
                                        user = user,
                                        isSubmitting = uiState.isSubmitting,
                                        onResetPinClick = { onOpenResetPinDialog(user) },
                                        onToggleStatusClick = { onRequestToggleStatus(user) }
                                    )
                                } else {
                                    StaffRowCompact(
                                        user = user,
                                        isSubmitting = uiState.isSubmitting,
                                        onResetPinClick = { onOpenResetPinDialog(user) },
                                        onToggleStatusClick = { onRequestToggleStatus(user) }
                                    )
                                }
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Staff Modal Dialog
    if (uiState.isAddDialogOpen) {
        AddStaffDialog(
            name = uiState.addName,
            username = uiState.addUsername,
            pin = uiState.addPin,
            confirmPin = uiState.addConfirmPin,
            selectedRole = uiState.addRole,
            errorMessage = uiState.addDialogError?.let { resolveUserMessage(it) },
            isSubmitting = uiState.isSubmitting,
            onNameChange = onAddNameChange,
            onUsernameChange = onAddUsernameChange,
            onPinChange = onAddPinChange,
            onConfirmPinChange = onAddConfirmPinChange,
            onRoleChange = onAddRoleChange,
            onDismiss = onCloseAddDialog,
            onSubmit = onSubmitAddUser
        )
    }

    // Reset PIN Modal Dialog
    uiState.resetTargetUser?.let { targetUser ->
        ResetPinDialog(
            user = targetUser,
            newPin = uiState.resetNewPin,
            confirmPin = uiState.resetConfirmPin,
            errorMessage = uiState.resetDialogError?.let { resolveUserMessage(it) },
            isSubmitting = uiState.isSubmitting,
            onNewPinChange = onResetNewPinChange,
            onConfirmPinChange = onResetConfirmPinChange,
            onDismiss = onCloseResetPinDialog,
            onSubmit = onSubmitResetPin
        )
    }

    // Active / Inactive Confirmation AlertDialog
    uiState.statusConfirmTargetUser?.let { targetUser ->
        val isCurrentlyActive = targetUser.status == 1
        AlertDialog(
            onDismissRequest = onCancelToggleStatus,
            title = {
                Text(
                    text = stringResource(
                        if (isCurrentlyActive) {
                            R.string.users_confirm_deactivate_title
                        } else {
                            R.string.users_confirm_activate_title
                        }
                    )
                )
            },
            text = {
                Text(
                    text = stringResource(
                        if (isCurrentlyActive) {
                            R.string.users_confirm_deactivate_message
                        } else {
                            R.string.users_confirm_activate_message
                        },
                        targetUser.name,
                        targetUser.username
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmToggleStatus,
                    enabled = !uiState.isSubmitting
                ) {
                    Text(
                        text = stringResource(
                            if (isCurrentlyActive) {
                                R.string.users_confirm_deactivate_yes
                            } else {
                                R.string.users_confirm_activate_yes
                            }
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onCancelToggleStatus,
                    enabled = !uiState.isSubmitting
                ) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun StaffTableRowWide(
    user: UserEntity,
    isSubmitting: Boolean,
    onResetPinClick: () -> Unit,
    onToggleStatusClick: () -> Unit
) {
    val isActive = user.status == 1
    val isAdmin = user.role == Role.ADMIN

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Name & Username
        Column(modifier = Modifier.weight(2.2f)) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isActive) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.users_username_prefix, user.username),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Role Badge
        Box(modifier = Modifier.weight(1.2f)) {
            RoleBadge(isAdmin = isAdmin)
        }

        // Status Switch + Label
        Row(
            modifier = Modifier.weight(1.4f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Switch(
                checked = isActive,
                onCheckedChange = { onToggleStatusClick() },
                enabled = !isSubmitting
            )
            Text(
                text = stringResource(
                    if (isActive) R.string.users_status_active else R.string.users_status_inactive
                ),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                softWrap = false,
                color = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        // Actions (Reset PIN)
        Box(modifier = Modifier.weight(1.5f)) {
            OutlinedButton(
                onClick = onResetPinClick,
                enabled = !isSubmitting,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.users_reset_pin_button),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun StaffRowCompact(
    user: UserEntity,
    isSubmitting: Boolean,
    onResetPinClick: () -> Unit,
    onToggleStatusClick: () -> Unit
) {
    val isActive = user.status == 1
    val isAdmin = user.role == Role.ADMIN

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Line: Name + Username on left, Role Badge on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.users_username_prefix, user.username),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            RoleBadge(isAdmin = isAdmin)
        }

        // Bottom Line: Status Switch + Label on left, Reset PIN Button on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Switch(
                    checked = isActive,
                    onCheckedChange = { onToggleStatusClick() },
                    enabled = !isSubmitting
                )
                Text(
                    text = stringResource(
                        if (isActive) R.string.users_status_active else R.string.users_status_inactive
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    softWrap = false,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            OutlinedButton(
                onClick = onResetPinClick,
                enabled = !isSubmitting,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.users_reset_pin_button),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun RoleBadge(isAdmin: Boolean) {
    val backgroundColor = if (isAdmin) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondary
    }
    val contentColor = if (isAdmin) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSecondary
    }
    val label = stringResource(if (isAdmin) R.string.role_admin else R.string.role_cashier)

    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SummaryBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun UserStatusBanner(
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
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
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

@Composable
private fun AddStaffDialog(
    name: String,
    username: String,
    pin: String,
    confirmPin: String,
    selectedRole: String,
    errorMessage: String?,
    isSubmitting: Boolean,
    onNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onConfirmPinChange: (String) -> Unit,
    onRoleChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    var pinVisible by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.users_add_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.users_field_name_label)) },
                    placeholder = { Text(stringResource(R.string.users_field_name_placeholder)) },
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = { Text(stringResource(R.string.users_field_username_label)) },
                    placeholder = { Text(stringResource(R.string.users_field_username_placeholder)) },
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = onPinChange,
                    label = { Text(stringResource(R.string.users_field_pin_label)) },
                    singleLine = true,
                    enabled = !isSubmitting,
                    visualTransformation = if (pinVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    trailingIcon = {
                        IconButton(onClick = { pinVisible = !pinVisible }) {
                            Icon(
                                imageVector = if (pinVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = stringResource(
                                    if (pinVisible) R.string.lock_hide_pin_cd else R.string.lock_show_pin_cd
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = onConfirmPinChange,
                    label = { Text(stringResource(R.string.users_field_confirm_pin_label)) },
                    singleLine = true,
                    enabled = !isSubmitting,
                    visualTransformation = if (pinVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.users_field_role_label),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterChip(
                            selected = selectedRole == Role.CASHIER,
                            onClick = { onRoleChange(Role.CASHIER) },
                            enabled = !isSubmitting,
                            label = { Text(stringResource(R.string.role_cashier)) }
                        )
                        FilterChip(
                            selected = selectedRole == Role.ADMIN,
                            onClick = { onRoleChange(Role.ADMIN) },
                            enabled = !isSubmitting,
                            label = { Text(stringResource(R.string.role_admin)) }
                        )
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = !isSubmitting &&
                    name.isNotBlank() &&
                    username.isNotBlank() &&
                    pin.length in 4..6 &&
                    confirmPin.length in 4..6
            ) {
                Text(stringResource(R.string.users_add_submit))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun ResetPinDialog(
    user: UserEntity,
    newPin: String,
    confirmPin: String,
    errorMessage: String?,
    isSubmitting: Boolean,
    onNewPinChange: (String) -> Unit,
    onConfirmPinChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    var pinVisible by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.users_reset_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.users_reset_dialog_target, user.name, user.username),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = newPin,
                    onValueChange = onNewPinChange,
                    label = { Text(stringResource(R.string.users_field_new_pin_label)) },
                    singleLine = true,
                    enabled = !isSubmitting,
                    visualTransformation = if (pinVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    trailingIcon = {
                        IconButton(onClick = { pinVisible = !pinVisible }) {
                            Icon(
                                imageVector = if (pinVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = stringResource(
                                    if (pinVisible) R.string.lock_hide_pin_cd else R.string.lock_show_pin_cd
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = onConfirmPinChange,
                    label = { Text(stringResource(R.string.users_field_confirm_pin_label)) },
                    singleLine = true,
                    enabled = !isSubmitting,
                    visualTransformation = if (pinVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = !isSubmitting &&
                    newPin.length in 4..6 &&
                    confirmPin.length in 4..6
            ) {
                Text(stringResource(R.string.users_reset_submit))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun resolveUserMessage(message: UserUiMessage): String {
    return when (message) {
        is UserUiMessage.Plain -> message.text
        is UserUiMessage.Resource -> stringResource(message.resId)
    }
}