package com.byteall.kharismart.ui.debt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.byteall.kharismart.R
import com.byteall.kharismart.data.local.PaymentMethod
import com.byteall.kharismart.data.local.model.DebtWithOrigin
import com.byteall.kharismart.util.format.digitsToLongOrNull
import com.byteall.kharismart.util.format.toRupiah

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebtPaymentDialog(
    debtWithOrigin: DebtWithOrigin,
    isSubmitting: Boolean,
    errorMessage: DebtUiMessage?,
    onDismiss: () -> Unit,
    onConfirmPayment: (debtId: Long, amount: Long, method: String, note: String?) -> Unit
) {
    val debt = debtWithOrigin.debt
    val remaining = (debt.totalAmount - debt.paidAmount).coerceAtLeast(0L)

    var amountInput by rememberSaveable(debt.debtId) { mutableStateOf("") }
    var selectedMethod by rememberSaveable(debt.debtId) { mutableStateOf(PaymentMethod.CASH) }
    var noteInput by rememberSaveable(debt.debtId) { mutableStateOf("") }
    var validationError by rememberSaveable(debt.debtId) { mutableStateOf<String?>(null) }

    val parsedAmount = amountInput.digitsToLongOrNull()
    val invalidAmountText = stringResource(R.string.debt_payment_err_invalid_amount)
    val exceedsRemainingText = stringResource(
        R.string.debt_payment_err_exceeds_remaining,
        remaining.toRupiah()
    )

    val quickAmounts = buildList {
        if (remaining > 0L) add(remaining)
        if (remaining > 10_000L) add(10_000L)
        if (remaining > 20_000L) add(20_000L)
        if (remaining > 50_000L) add(50_000L)
    }.distinct()

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(
                text = stringResource(R.string.debt_payment_dialog_title),
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
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.debt_payment_customer, debt.customerName),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (debt.transactionId != null) {
                                stringResource(
                                    R.string.debt_origin_pos,
                                    debtWithOrigin.receiptNo ?: "-"
                                )
                            } else {
                                stringResource(R.string.debt_origin_manual)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.debt_label_total),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = debt.totalAmount.toRupiah(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.debt_label_paid),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = debt.paidAmount.toRupiah(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.debt_label_remaining),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = remaining.toRupiah(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                if (quickAmounts.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        quickAmounts.forEach { preset ->
                            val label = if (preset == remaining) {
                                stringResource(R.string.debt_payment_quick_full, preset.toRupiah())
                            } else {
                                preset.toRupiah()
                            }
                            AssistChip(
                                onClick = {
                                    amountInput = preset.toString()
                                    validationError = null
                                },
                                enabled = !isSubmitting,
                                label = { Text(label) }
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
                    label = { Text(stringResource(R.string.debt_payment_amount_label)) },
                    placeholder = { Text(stringResource(R.string.debt_payment_amount_placeholder)) },
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

                Text(
                    text = stringResource(R.string.debt_payment_method_label),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilterChip(
                        selected = selectedMethod == PaymentMethod.CASH,
                        onClick = { selectedMethod = PaymentMethod.CASH },
                        enabled = !isSubmitting,
                        modifier = Modifier.height(48.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = null
                            )
                        },
                        label = { Text(stringResource(R.string.debt_method_cash)) }
                    )
                    FilterChip(
                        selected = selectedMethod == PaymentMethod.QRIS,
                        onClick = { selectedMethod = PaymentMethod.QRIS },
                        enabled = !isSubmitting,
                        modifier = Modifier.height(48.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = null
                            )
                        },
                        label = { Text(stringResource(R.string.debt_method_qris)) }
                    )
                }

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                    singleLine = true,
                    label = { Text(stringResource(R.string.debt_payment_note_label)) },
                    placeholder = { Text(stringResource(R.string.debt_payment_note_placeholder)) }
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
                        amount == null || amount <= 0L -> {
                            validationError = invalidAmountText
                        }
                        amount > remaining -> {
                            validationError = exceedsRemainingText
                        }
                        else -> {
                            validationError = null
                            onConfirmPayment(debt.debtId, amount, selectedMethod, noteInput)
                        }
                    }
                },
                enabled = !isSubmitting && parsedAmount != null && parsedAmount > 0L
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.debt_payment_submit))
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