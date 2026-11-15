package com.byteall.kharismart.ui.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.byteall.kharismart.R
import com.byteall.kharismart.data.local.PaymentMethod
import com.byteall.kharismart.util.format.toRupiah
import com.byteall.kharismart.util.image.ImageStorageManager

/**
 * Atomic 3-Way Checkout Dialog (SRS-F07, F08, BR-03, BR-04, BR-04b, BR-05).
 *
 * Supported Payment Methods:
 * 1. [PaymentMethod.CASH]: Input tendered amount, live change calculation, quick cash chips (+10k, +20k, +50k, exact).
 * 2. [PaymentMethod.QRIS]: Displays store QRIS bitmap image, bank and owner name, and mandatory funds received confirmation checkbox.
 * 3. [PaymentMethod.CREDIT]: Mandatory customer debtor name (with autocomplete suggestions), summary display, zero payment.
 *
 * Safety:
 * - Double-click protection via submission state.
 * - Confirmation alert dialog before final processing.
 */
@Composable
fun CheckoutDialog(
    totalAmount: Long,
    qrisBank: String?,
    qrisOwnerName: String?,
    qrisImageFilename: String?,
    debtorSuggestions: List<String> = emptyList(),
    isSubmitting: Boolean = false,
    onDismissRequest: () -> Unit,
    onConfirmCheckout: (method: String, paidAmount: Long, customerName: String?, note: String?) -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0: Cash, 1: QRIS, 2: Credit
    var paidInputText by remember { mutableStateOf("") }
    var qrisConfirmed by remember { mutableStateOf(false) }
    var debtorNameInput by remember { mutableStateOf("") }
    var transactionNoteInput by remember { mutableStateOf("") }
    var showConfirmationModal by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val parsedPaidAmount = paidInputText.filter { it.isDigit() }.toLongOrNull() ?: 0L

    val isCashValid = selectedTabIndex == 0 && parsedPaidAmount >= totalAmount
    val isQrisValid = selectedTabIndex == 1 && qrisConfirmed
    val isCreditValid = selectedTabIndex == 2 && debtorNameInput.trim().isNotBlank()

    val canProceed = !isSubmitting && (
        when (selectedTabIndex) {
            0 -> isCashValid
            1 -> isQrisValid
            2 -> isCreditValid
            else -> false
        }
    )

    val currentMethod = when (selectedTabIndex) {
        0 -> PaymentMethod.CASH
        1 -> PaymentMethod.QRIS
        else -> PaymentMethod.CREDIT
    }

    val methodLabel = when (selectedTabIndex) {
        0 -> stringResource(R.string.checkout_tab_cash)
        1 -> stringResource(R.string.checkout_tab_qris)
        else -> stringResource(R.string.checkout_tab_credit)
    }

    Dialog(
        onDismissRequest = {
            if (!isSubmitting) onDismissRequest()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth(0.94f)
                .padding(16.dp)
                .testTag("checkout_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.checkout_dialog_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = stringResource(R.string.checkout_total_label),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = totalAmount.toRupiah(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        enabled = !isSubmitting
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.dialog_btn_cancel)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Payment Method Tabs
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text(stringResource(R.string.checkout_tab_cash)) },
                        icon = { Icon(Icons.Default.Payments, contentDescription = null) },
                        modifier = Modifier.testTag("checkout_tab_cash")
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text(stringResource(R.string.checkout_tab_qris)) },
                        icon = { Icon(Icons.Default.QrCode, contentDescription = null) },
                        modifier = Modifier.testTag("checkout_tab_qris")
                    )
                    Tab(
                        selected = selectedTabIndex == 2,
                        onClick = { selectedTabIndex = 2 },
                        text = { Text(stringResource(R.string.checkout_tab_credit)) },
                        icon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.testTag("checkout_tab_credit")
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Content based on selected tab
                when (selectedTabIndex) {
                    0 -> CashPaymentContent(
                        totalAmount = totalAmount,
                        paidInputText = paidInputText,
                        onPaidInputChange = { paidInputText = it },
                        parsedPaidAmount = parsedPaidAmount
                    )
                    1 -> QrisPaymentContent(
                        totalAmount = totalAmount,
                        qrisBank = qrisBank,
                        qrisOwnerName = qrisOwnerName,
                        qrisImageFilename = qrisImageFilename,
                        qrisConfirmed = qrisConfirmed,
                        onQrisConfirmedChange = { qrisConfirmed = it }
                    )
                    2 -> CreditPaymentContent(
                        totalAmount = totalAmount,
                        debtorNameInput = debtorNameInput,
                        onDebtorNameChange = { debtorNameInput = it },
                        debtorSuggestions = debtorSuggestions
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Optional Note
                OutlinedTextField(
                    value = transactionNoteInput,
                    onValueChange = { transactionNoteInput = it },
                    label = { Text(stringResource(R.string.checkout_note_label)) },
                    placeholder = { Text(stringResource(R.string.checkout_note_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("checkout_input_note")
                )

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        enabled = !isSubmitting,
                        modifier = Modifier.testTag("checkout_btn_cancel")
                    ) {
                        Text(stringResource(R.string.dialog_btn_cancel))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = { showConfirmationModal = true },
                        enabled = canProceed,
                        modifier = Modifier.testTag("checkout_btn_submit")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.checkout_btn_processing))
                        } else {
                            Text(stringResource(R.string.checkout_btn_confirm))
                        }
                    }
                }
            }
        }
    }

    // Confirmation Modal before saving
    if (showConfirmationModal) {
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showConfirmationModal = false },
            title = { Text(stringResource(R.string.checkout_confirm_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.checkout_confirm_dialog_message,
                        totalAmount.toRupiah(),
                        methodLabel
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmationModal = false
                        val effectivePaid = when (currentMethod) {
                            PaymentMethod.CASH -> parsedPaidAmount
                            PaymentMethod.QRIS -> totalAmount
                            PaymentMethod.CREDIT -> 0L
                            else -> 0L
                        }
                        onConfirmCheckout(
                            currentMethod,
                            effectivePaid,
                            if (currentMethod == PaymentMethod.CREDIT) debtorNameInput.trim() else null,
                            transactionNoteInput.trim().ifBlank { null }
                        )
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier.testTag("checkout_confirm_btn_proceed")
                ) {
                    Text(stringResource(R.string.dialog_btn_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmationModal = false },
                    enabled = !isSubmitting
                ) {
                    Text(stringResource(R.string.dialog_btn_cancel))
                }
            }
        )
    }
}

/**
 * Tab 1: Tunai / Cash Payment Content.
 */
@Composable
private fun CashPaymentContent(
    totalAmount: Long,
    paidInputText: String,
    onPaidInputChange: (String) -> Unit,
    parsedPaidAmount: Long
) {
    val changeDue = parsedPaidAmount - totalAmount
    val isSufficient = parsedPaidAmount >= totalAmount

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = paidInputText,
            onValueChange = { input ->
                val digits = input.filter { it.isDigit() }
                onPaidInputChange(digits)
            },
            label = { Text(stringResource(R.string.checkout_cash_paid_label)) },
            placeholder = { Text("0") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("checkout_input_cash_paid")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Quick Cash Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // "Uang Pas" chip
            FilterChip(
                selected = parsedPaidAmount == totalAmount,
                onClick = { onPaidInputChange(totalAmount.toString()) },
                label = { Text(stringResource(R.string.checkout_cash_exact)) }
            )

            // Smart rounded denominations
            val quickAmounts = listOf(
                calculateNextMultiple(totalAmount, 10_000L),
                calculateNextMultiple(totalAmount, 20_000L),
                calculateNextMultiple(totalAmount, 50_000L),
                calculateNextMultiple(totalAmount, 100_000L)
            ).filter { it > totalAmount }.distinct()

            for (amt in quickAmounts) {
                FilterChip(
                    selected = parsedPaidAmount == amt,
                    onClick = { onPaidInputChange(amt.toString()) },
                    label = { Text(amt.toRupiah()) }
                )
            }

            // Quick additive chips
            FilterChip(
                selected = false,
                onClick = {
                    val current = parsedPaidAmount.coerceAtLeast(totalAmount)
                    onPaidInputChange((current + 10_000L).toString())
                },
                label = { Text("+10rb") }
            )
            FilterChip(
                selected = false,
                onClick = {
                    val current = parsedPaidAmount.coerceAtLeast(totalAmount)
                    onPaidInputChange((current + 20_000L).toString())
                },
                label = { Text("+20rb") }
            )
            FilterChip(
                selected = false,
                onClick = {
                    val current = parsedPaidAmount.coerceAtLeast(totalAmount)
                    onPaidInputChange((current + 50_000L).toString())
                },
                label = { Text("+50rb") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Change or Insufficient Indicator
        if (paidInputText.isNotBlank()) {
            if (isSufficient) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("checkout_change_amount"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.checkout_cash_change_label),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = changeDue.toRupiah(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.checkout_cash_insufficient_label),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = (totalAmount - parsedPaidAmount).toRupiah(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tab 2: QRIS Payment Content.
 */
@Composable
private fun QrisPaymentContent(
    totalAmount: Long,
    qrisBank: String?,
    qrisOwnerName: String?,
    qrisImageFilename: String?,
    qrisConfirmed: Boolean,
    onQrisConfirmedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val qrisFile = remember(qrisImageFilename) {
        if (!qrisImageFilename.isNullOrBlank()) {
            ImageStorageManager.getImageFile(context, qrisImageFilename)
        } else {
            null
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.checkout_qris_instruction),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Store / Bank details
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(
                    R.string.checkout_qris_bank_label,
                    qrisBank ?: "BCA / Bank Partner"
                ),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = stringResource(
                    R.string.checkout_qris_owner_label,
                    qrisOwnerName ?: "Toko Kharisma"
                ),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // QR Code Image or Placeholder
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(16.dp)
                )
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (qrisFile != null && qrisFile.exists()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(qrisFile)
                        .crossfade(true)
                        .build(),
                    contentDescription = "QRIS Toko Kharisma",
                    modifier = Modifier.size(190.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.checkout_qris_no_image),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Mandatory Confirmation Checkbox (BR-04b)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onQrisConfirmedChange(!qrisConfirmed) },
            colors = CardDefaults.cardColors(
                containerColor = if (qrisConfirmed) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = qrisConfirmed,
                    onCheckedChange = onQrisConfirmedChange,
                    modifier = Modifier.testTag("checkout_qris_checkbox")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.checkout_qris_confirm_checkbox),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Tab 3: Utang / Credit Payment Content.
 */
@Composable
private fun CreditPaymentContent(
    totalAmount: Long,
    debtorNameInput: String,
    onDebtorNameChange: (String) -> Unit,
    debtorSuggestions: List<String>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = debtorNameInput,
            onValueChange = onDebtorNameChange,
            label = { Text(stringResource(R.string.checkout_credit_customer_label)) },
            placeholder = { Text(stringResource(R.string.checkout_credit_customer_hint)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("checkout_input_customer_name")
        )

        // Customer Suggestions Chips
        val filteredSuggestions = debtorSuggestions.filter {
            debtorNameInput.isBlank() || it.contains(debtorNameInput, ignoreCase = true)
        }.take(5)

        if (filteredSuggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (name in filteredSuggestions) {
                    FilterChip(
                        selected = debtorNameInput.equals(name, ignoreCase = true),
                        onClick = { onDebtorNameChange(name) },
                        label = { Text(name) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Credit summary
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.checkout_credit_paid_info),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Rp0",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.checkout_credit_remaining_info,
                            ""
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = totalAmount.toRupiah(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

/**
 * Calculates the next multiple of [step] that is strictly >= [amount].
 */
private fun calculateNextMultiple(amount: Long, step: Long): Long {
    if (amount <= 0L) return step
    val remainder = amount % step
    return if (remainder == 0L) amount else amount + (step - remainder)
}
