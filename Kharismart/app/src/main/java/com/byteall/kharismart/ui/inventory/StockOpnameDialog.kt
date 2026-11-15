package com.byteall.kharismart.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.byteall.kharismart.R
import com.byteall.kharismart.data.local.entity.ProductEntity
import kotlin.math.abs

/**
 * Modal dialog for physical stock opname inventory reconciliation (SRS-F12, BR-12).
 *
 * Requirements:
 * - Admin only.
 * - Enter Counted Stock (>= 0).
 * - Enter Reason text (required, non-blank).
 * - Real-time calculation of stock delta (difference).
 * - Clarifies BR-12: adjustments are inventory movements only, never affecting finance/cash reports.
 */
@Composable
fun StockOpnameDialog(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onConfirmAdjustment: (countedStock: Int, reason: String) -> Unit
) {
    var countedStockText by remember { mutableStateOf(product.stock.toString()) }
    var reasonText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val countedStock = countedStockText.toIntOrNull() ?: product.stock
    val delta = countedStock - product.stock

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(min = 340.dp, max = 520.dp)
                .fillMaxWidth(0.92f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Header
                Text(
                    text = stringResource(R.string.dialog_opname_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // System Stock Display Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_opname_system_stock, product.stock),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${product.stock} unit",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Counted Stock Input (>= 0)
                OutlinedTextField(
                    value = countedStockText,
                    onValueChange = {
                        countedStockText = it.filter { ch -> ch.isDigit() }
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.dialog_opname_counted_stock)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_counted_stock")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Reason for Adjustment Input (Required)
                OutlinedTextField(
                    value = reasonText,
                    onValueChange = {
                        reasonText = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.dialog_opname_reason)) },
                    placeholder = { Text(stringResource(R.string.dialog_opname_reason_hint)) },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_opname_reason")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Delta preview card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            delta < 0 -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                            delta > 0 -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_opname_delta),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        val deltaText = when {
                            delta < 0 -> stringResource(R.string.dialog_opname_delta_negative, abs(delta))
                            delta > 0 -> stringResource(R.string.dialog_opname_delta_positive, delta)
                            else -> stringResource(R.string.dialog_opname_delta_zero)
                        }
                        val deltaColor = when {
                            delta < 0 -> MaterialTheme.colorScheme.error
                            delta > 0 -> Color(0xFF2E7D32)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = deltaText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = deltaColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // BR-12 Info note
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.dialog_opname_notice),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                // Error message
                errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_cancel_opname")
                    ) {
                        Text(stringResource(R.string.dialog_btn_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (reasonText.trim().isBlank()) {
                                errorMessage = "Alasan penyesuaian stok fisik wajib diisi"
                                return@Button
                            }
                            if (countedStock < 0) {
                                errorMessage = "Jumlah stok tidak boleh negatif"
                                return@Button
                            }
                            onConfirmAdjustment(countedStock, reasonText.trim())
                        },
                        enabled = reasonText.isNotBlank(),
                        modifier = Modifier.testTag("btn_confirm_opname")
                    ) {
                        Text(stringResource(R.string.dialog_btn_opname_confirm))
                    }
                }
            }
        }
    }
}
