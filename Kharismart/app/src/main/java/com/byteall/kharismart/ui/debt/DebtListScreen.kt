package com.byteall.kharismart.ui.debt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byteall.kharismart.R
import com.byteall.kharismart.data.local.DebtStatus
import com.byteall.kharismart.data.local.PaymentMethod
import com.byteall.kharismart.data.local.entity.DebtPaymentEntity
import com.byteall.kharismart.data.local.model.DebtWithOrigin
import com.byteall.kharismart.data.repository.ReceiptData
import com.byteall.kharismart.di.appViewModel
import com.byteall.kharismart.session.AppSession
import com.byteall.kharismart.session.UserRole
import com.byteall.kharismart.util.format.digitsToLongOrNull
import com.byteall.kharismart.util.format.toRupiah
import com.byteall.kharismart.util.time.WibClock

@Composable
fun DebtListScreen(
    onViewReceipt: ((transactionId: Long) -> Unit)? = null,
    viewModel: DebtViewModel = appViewModel { container ->
        DebtViewModel(
            debtRepository = container.debtRepository,
            saleRepository = container.saleRepository
        )
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentUser by AppSession.currentUser.collectAsStateWithLifecycle()
    val isAdmin = currentUser?.role == UserRole.ADMIN

    DebtListContent(
        uiState = uiState,
        isAdmin = isAdmin,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onFilterSelected = viewModel::onFilterSelected,
        onOpenAddManualDebt = viewModel::openAddManualDebtDialog,
        onOpenPaymentDialog = viewModel::openPaymentDialog,
        onOpenPaymentHistory = viewModel::openPaymentHistory,
        onRequestDeleteDebt = viewModel::requestDeleteDebt,
        onViewReceiptClick = { transactionId ->
            if (onViewReceipt != null) {
                onViewReceipt(transactionId)
            } else {
                viewModel.openReceiptPreview(transactionId)
            }
        },
        onDismissBanner = viewModel::dismissBanner
    )

    uiState.selectedDebtForPayment?.let { selectedDebt ->
        DebtPaymentDialog(
            debtWithOrigin = selectedDebt,
            isSubmitting = uiState.isSubmitting,
            errorMessage = uiState.dialogErrorMessage,
            onDismiss = viewModel::dismissPaymentDialog,
            onConfirmPayment = viewModel::recordPayment
        )
    }

    if (uiState.isAddManualDebtDialogOpen) {
        AddManualDebtDialog(
            customerSuggestions = uiState.customerSuggestions,
            isSubmitting = uiState.isSubmitting,
            errorMessage = uiState.dialogErrorMessage,
            onDismiss = viewModel::dismissAddManualDebtDialog,
            onConfirmAdd = viewModel::addManualDebt
        )
    }

    uiState.debtToDelete?.let { debtItem ->
        DeleteDebtConfirmationDialog(
            debtWithOrigin = debtItem,
            isSubmitting = uiState.isSubmitting,
            onDismiss = viewModel::dismissDeleteDebtDialog,
            onConfirmDelete = viewModel::confirmDeleteDebt
        )
    }

    uiState.selectedDebtForHistory?.let { debtItem ->
        DebtPaymentHistoryDialog(
            debtWithOrigin = debtItem,
            payments = uiState.paymentHistory,
            onDismiss = viewModel::dismissPaymentHistory
        )
    }

    uiState.receiptPreview?.let { receiptData ->
        DebtReceiptPreviewDialog(
            receiptData = receiptData,
            onDismiss = viewModel::dismissReceiptPreview
        )
    }
}

@Composable
private fun DebtListContent(
    uiState: DebtUiState,
    isAdmin: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onFilterSelected: (DebtFilter) -> Unit,
    onOpenAddManualDebt: () -> Unit,
    onOpenPaymentDialog: (DebtWithOrigin) -> Unit,
    onOpenPaymentHistory: (DebtWithOrigin) -> Unit,
    onRequestDeleteDebt: (DebtWithOrigin) -> Unit,
    onViewReceiptClick: (Long) -> Unit,
    onDismissBanner: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.debt_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.debt_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = onOpenAddManualDebt,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(text = stringResource(R.string.debt_add_manual_button))
                }
            }
        }

        item(key = "outstanding_banner") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.debt_outstanding_label),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = uiState.totalOutstanding.toRupiah(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = stringResource(R.string.debt_summary_unpaid, uiState.unpaidCount),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = stringResource(R.string.debt_summary_paid, uiState.paidCount),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        uiState.bannerMessage?.let { message ->
            item(key = "feedback_banner") {
                DebtFeedbackBanner(
                    text = message.resolveDebtMessage(),
                    isError = uiState.isBannerError,
                    onDismiss = onDismissBanner
                )
            }
        }

        item(key = "search_and_filters") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.debt_search_placeholder)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = if (uiState.searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.debt_search_clear_cd)
                                )
                            }
                        }
                    } else {
                        null
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.selectedFilter == DebtFilter.ALL,
                        onClick = { onFilterSelected(DebtFilter.ALL) },
                        modifier = Modifier.height(48.dp),
                        label = { Text(stringResource(R.string.debt_filter_all)) }
                    )
                    FilterChip(
                        selected = uiState.selectedFilter == DebtFilter.UNPAID,
                        onClick = { onFilterSelected(DebtFilter.UNPAID) },
                        modifier = Modifier.height(48.dp),
                        label = { Text(stringResource(R.string.debt_filter_unpaid)) }
                    )
                    FilterChip(
                        selected = uiState.selectedFilter == DebtFilter.PAID,
                        onClick = { onFilterSelected(DebtFilter.PAID) },
                        modifier = Modifier.height(48.dp),
                        label = { Text(stringResource(R.string.debt_filter_paid)) }
                    )
                }
            }
        }

        if (uiState.filteredDebts.isEmpty()) {
            item(key = "empty_state") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.allDebts.isEmpty()) {
                            stringResource(R.string.debt_empty_state)
                        } else {
                            stringResource(R.string.debt_empty_filtered)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(
                items = uiState.filteredDebts,
                key = { it.debt.debtId }
            ) { item ->
                DebtCard(
                    item = item,
                    isAdmin = isAdmin,
                    onRecordPayment = { onOpenPaymentDialog(item) },
                    onViewReceipt = {
                        item.debt.transactionId?.let { txId -> onViewReceiptClick(txId) }
                    },
                    onViewHistory = { onOpenPaymentHistory(item) },
                    onDelete = { onRequestDeleteDebt(item) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DebtCard(
    item: DebtWithOrigin,
    isAdmin: Boolean,
    onRecordPayment: () -> Unit,
    onViewReceipt: () -> Unit,
    onViewHistory: () -> Unit,
    onDelete: () -> Unit
) {
    val debt = item.debt
    val remaining = (debt.totalAmount - debt.paidAmount).coerceAtLeast(0L)
    val isPaid = debt.status == DebtStatus.PAID || remaining == 0L
    val isPosOrigin = debt.transactionId != null
    val canDelete = isAdmin && !isPosOrigin && debt.paidAmount == 0L

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = debt.customerName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = WibClock.displayDateTime(debt.date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DebtStatusPill(isPaid = isPaid)
                    if (canDelete) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.debt_action_delete_cd),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Origin Badge
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (isPosOrigin) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isPosOrigin) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = if (isPosOrigin) {
                            stringResource(R.string.debt_origin_pos, item.receiptNo ?: "-")
                        } else {
                            stringResource(R.string.debt_origin_manual)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isPosOrigin) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            if (!debt.note.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.debt_note_prefix, debt.note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.debt_label_total),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = debt.totalAmount.toRupiah(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.debt_label_paid),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = debt.paidAmount.toRupiah(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.debt_label_remaining),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = remaining.toRupiah(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (remaining > 0L) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isPaid) {
                    Button(
                        onClick = onRecordPayment,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.debt_action_record_payment))
                    }
                }

                if (isPosOrigin) {
                    OutlinedButton(
                        onClick = onViewReceipt,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.debt_action_view_receipt))
                    }
                }

                if (debt.paidAmount > 0L) {
                    FilledTonalButton(
                        onClick = onViewHistory,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.debt_action_history))
                    }
                }
            }
        }
    }
}

@Composable
private fun DebtStatusPill(isPaid: Boolean) {
    val containerColor = if (isPaid) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (isPaid) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    val labelRes = if (isPaid) {
        R.string.debt_status_paid
    } else {
        R.string.debt_status_unpaid
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isPaid) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = contentColor
                )
            }
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@Composable
private fun DebtFeedbackBanner(
    text: String,
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
        shape = MaterialTheme.shapes.medium,
        color = containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_close),
                    color = contentColor
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddManualDebtDialog(
    customerSuggestions: List<String>,
    isSubmitting: Boolean,
    errorMessage: DebtUiMessage?,
    onDismiss: () -> Unit,
    onConfirmAdd: (customerName: String, amount: Long, note: String?) -> Unit
) {
    var customerName by rememberSaveable { mutableStateOf("") }
    var amountInput by rememberSaveable { mutableStateOf("") }
    var noteInput by rememberSaveable { mutableStateOf("") }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }

    val parsedAmount = amountInput.digitsToLongOrNull()
    val errCustomerRequired = stringResource(R.string.debt_add_err_customer_required)
    val errInvalidAmount = stringResource(R.string.debt_add_err_invalid_amount)

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(
                text = stringResource(R.string.debt_add_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
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
                    value = customerName,
                    onValueChange = {
                        customerName = it
                        validationError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                    singleLine = true,
                    label = { Text(stringResource(R.string.debt_add_customer_label)) },
                    placeholder = { Text(stringResource(R.string.debt_add_customer_placeholder)) }
                )

                if (customerSuggestions.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.debt_add_suggestions_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        customerSuggestions.take(6).forEach { suggestion ->
                            AssistChip(
                                onClick = {
                                    customerName = suggestion
                                    validationError = null
                                },
                                enabled = !isSubmitting,
                                label = { Text(suggestion) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { raw ->
                        amountInput = raw.filter { it.isDigit() }
                        validationError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                    singleLine = true,
                    label = { Text(stringResource(R.string.debt_add_amount_label)) },
                    placeholder = { Text(stringResource(R.string.debt_add_amount_placeholder)) },
                    supportingText = if (parsedAmount != null && parsedAmount > 0L) {
                        {
                            Text(
                                text = parsedAmount.toRupiah(),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                    singleLine = true,
                    label = { Text(stringResource(R.string.debt_add_note_label)) },
                    placeholder = { Text(stringResource(R.string.debt_add_note_placeholder)) }
                )

                val activeErrorText = validationError ?: errorMessage?.resolveDebtMessage()
                if (activeErrorText != null) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = activeErrorText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountInput.digitsToLongOrNull()
                    when {
                        customerName.trim().isEmpty() -> {
                            validationError = errCustomerRequired
                        }
                        amount == null || amount <= 0L -> {
                            validationError = errInvalidAmount
                        }
                        else -> {
                            validationError = null
                            onConfirmAdd(customerName, amount, noteInput)
                        }
                    }
                },
                enabled = !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.debt_add_submit))
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
private fun DeleteDebtConfirmationDialog(
    debtWithOrigin: DebtWithOrigin,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(
                text = stringResource(R.string.debt_delete_dialog_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = stringResource(
                    R.string.debt_delete_dialog_message,
                    debtWithOrigin.debt.customerName,
                    debtWithOrigin.debt.totalAmount.toRupiah()
                )
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(stringResource(R.string.debt_delete_confirm))
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
private fun DebtPaymentHistoryDialog(
    debtWithOrigin: DebtWithOrigin,
    payments: List<DebtPaymentEntity>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.debt_history_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = debtWithOrigin.debt.customerName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            if (payments.isEmpty()) {
                Text(
                    text = stringResource(R.string.debt_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    payments.forEach { payment ->
                        val methodLabel = if (payment.method == PaymentMethod.QRIS) {
                            stringResource(R.string.debt_method_qris)
                        } else {
                            stringResource(R.string.debt_method_cash)
                        }
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = payment.amount.toRupiah(),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = methodLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = WibClock.displayDateTime(payment.date),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!payment.note.isNullOrBlank()) {
                                    Text(
                                        text = payment.note,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
private fun DebtReceiptPreviewDialog(
    receiptData: ReceiptData,
    onDismiss: () -> Unit
) {
    val tx = receiptData.transaction
    val debt = receiptData.debt
    val remaining = if (debt != null) {
        (debt.totalAmount - debt.paidAmount).coerceAtLeast(0L)
    } else {
        0L
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.debt_receipt_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = receiptData.settings.storeName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    receiptData.settings.storeAddress?.takeIf { it.isNotBlank() }?.let { address ->
                        Text(
                            text = address,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = stringResource(R.string.debt_receipt_no_label, tx.receiptNo),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = stringResource(
                            R.string.debt_receipt_date_label,
                            WibClock.displayDateTime(tx.date)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = stringResource(
                            R.string.debt_receipt_cashier_label,
                            receiptData.cashierName
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    receiptData.items.forEach { line ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${line.item.quantity}x ${line.productName}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = line.item.subtotal.toRupiah(),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.debt_receipt_total_label),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = tx.total.toRupiah(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = stringResource(R.string.debt_receipt_method_credit),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )

                    if (debt != null) {
                        Text(
                            text = stringResource(R.string.debt_payment_customer, debt.customerName),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.debt_receipt_paid_label),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = tx.paid.toRupiah(),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.debt_receipt_remaining_label),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = remaining.toRupiah(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (remaining > 0L) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }

                    receiptData.settings.receiptFooter?.takeIf { it.isNotBlank() }?.let { footer ->
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = footer,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
internal fun DebtUiMessage.resolveDebtMessage(): String {
    return when (this) {
        is DebtUiMessage.Plain -> text
        is DebtUiMessage.Resource -> {
            if (args.isEmpty()) {
                stringResource(resId)
            } else {
                stringResource(resId, *args.toTypedArray())
            }
        }
    }
}