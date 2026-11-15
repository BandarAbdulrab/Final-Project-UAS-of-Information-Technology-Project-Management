package com.byteall.kharismart.ui.receipt

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.byteall.kharismart.R
import com.byteall.kharismart.data.local.PaymentMethod
import com.byteall.kharismart.util.format.toRupiah
import com.byteall.kharismart.util.printer.ReceiptPrinter
import com.byteall.kharismart.util.printer.ReceiptUiModel

/**
 * Visual Paper Receipt Preview Card and Printing Actions Dialog (SRS-F09, F10).
 *
 * Displays:
 * - Store header, date, receipt number, cashier name.
 * - Monospaced itemized table (Qty x Price = Subtotal).
 * - Total, payment method details (Cash tendered & change / QRIS status / Debtor note & balance).
 * - Store footer greeting.
 * - Prominent "SALINAN / REPRINT" banner when [ReceiptUiModel.isReprint] is true.
 *
 * Actions:
 * - "Cetak / PDF" via Android [android.print.PrintManager].
 * - "Bagikan" via Android [android.content.Intent.ACTION_SEND].
 * - "Selesai" closing the dialog.
 */
@Composable
fun ReceiptDialog(
    receipt: ReceiptUiModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth(0.92f)
                .padding(16.dp)
                .testTag("receipt_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Thermal Paper Container
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Reprint Banner if applicable
                        if (receipt.isReprint) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.receipt_badge_reprint),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        // Store Identity
                        Text(
                            text = receipt.storeName.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            textAlign = TextAlign.Center
                        )
                        receipt.storeAddress?.let { addr ->
                            if (addr.isNotBlank()) {
                                Text(
                                    text = addr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.DarkGray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        DashedDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        // Transaction Metadata
                        ReceiptMetaRow(
                            label = "No. Nota",
                            value = receipt.receiptNo,
                            isBold = true
                        )
                        ReceiptMetaRow(
                            label = "Waktu",
                            value = "${receipt.dateFormatted} WIB"
                        )
                        ReceiptMetaRow(
                            label = "Kasir",
                            value = receipt.cashierName
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        DashedDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        // Itemized Lines
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (item in receipt.items) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.Black
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "  ${item.quantity} x ${item.price.toRupiah()}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color.DarkGray
                                        )
                                        Text(
                                            text = item.subtotal.toRupiah(),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.Black
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        DashedDivider()
                        Spacer(modifier = Modifier.height(10.dp))

                        // Total Calculation
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.receipt_total_label),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Text(
                                text = receipt.total.toRupiah(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Payment Details
                        when (receipt.paymentMethod) {
                            PaymentMethod.CASH -> {
                                ReceiptMetaRow("Metode Bayar", "Tunai (Cash)")
                                ReceiptMetaRow("Uang Diterima", receipt.paid.toRupiah())
                                ReceiptMetaRow("Kembalian", receipt.changeDue.toRupiah(), isBold = true)
                            }
                            PaymentMethod.QRIS -> {
                                ReceiptMetaRow("Metode Bayar", "QRIS")
                                ReceiptMetaRow("Status Bayar", "LUNAS", isBold = true)
                            }
                            PaymentMethod.CREDIT -> {
                                ReceiptMetaRow("Metode Bayar", "Utang (Kredit)")
                                receipt.customerName?.let {
                                    ReceiptMetaRow("Pelanggan", it, isBold = true)
                                }
                                ReceiptMetaRow("Dibayar", "Rp0")
                                ReceiptMetaRow(
                                    "Sisa Utang",
                                    (receipt.remainingDebt ?: receipt.total).toRupiah(),
                                    isBold = true,
                                    highlightColor = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        DashedDivider()
                        Spacer(modifier = Modifier.height(10.dp))

                        // Store Footer
                        val footerMsg = receipt.footer?.ifBlank { null }
                            ?: stringResource(R.string.receipt_footer_default)
                        Text(
                            text = footerMsg,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Barang yang sudah dibeli tidak dapat ditukar/dikembalikan",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            fontSize = 9.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { ReceiptPrinter.shareReceipt(context, receipt) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("receipt_btn_share")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.receipt_btn_share))
                    }

                    Button(
                        onClick = { ReceiptPrinter.printReceipt(context, receipt) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("receipt_btn_print"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.receipt_btn_print))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("receipt_btn_done"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.receipt_btn_done))
                }
            }
        }
    }
}

@Composable
private fun ReceiptMetaRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    highlightColor: Color? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.DarkGray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            color = highlightColor ?: Color.Black
        )
    }
}

@Composable
private fun DashedDivider() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        thickness = 1.dp,
        color = Color.LightGray
    )
}
