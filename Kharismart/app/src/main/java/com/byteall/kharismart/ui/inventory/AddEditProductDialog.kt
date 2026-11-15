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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.byteall.kharismart.R
import com.byteall.kharismart.data.local.entity.CategoryEntity
import com.byteall.kharismart.data.local.entity.ProductEntity
import com.byteall.kharismart.util.format.digitsToLongOrNull
import com.byteall.kharismart.util.format.toRupiah
import com.byteall.kharismart.util.image.ProductImagePicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProductDialog(
    initialProduct: ProductEntity?,
    categories: List<CategoryEntity>,
    defaultMinStock: Int,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onSaveProduct: (ProductEntity) -> Unit,
    onAddNewCategory: () -> Unit
) {
    val isEditMode = initialProduct != null

    var name by remember { mutableStateOf(initialProduct?.name ?: "") }
    var barcode by remember { mutableStateOf(initialProduct?.barcode ?: "") }
    var selectedCategoryId by remember {
        mutableLongStateOf(
            initialProduct?.categoryId
                ?: categories.firstOrNull()?.categoryId
                ?: 0L
        )
    }

    var sellingPriceText by remember {
        mutableStateOf(if (isEditMode) initialProduct!!.price.toString() else "")
    }
    var costPriceText by remember {
        mutableStateOf(if (isEditMode) initialProduct!!.costPrice.toString() else "")
    }
    var initialStockText by remember {
        mutableStateOf(if (isEditMode) initialProduct!!.stock.toString() else "")
    }
    var minStockText by remember {
        mutableStateOf(initialProduct?.minStock?.toString() ?: "")
    }
    var photoFilename by remember { mutableStateOf(initialProduct?.photo) }

    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val sellingPriceLong = sellingPriceText.digitsToLongOrNull() ?: 0L
    val costPriceLong = costPriceText.digitsToLongOrNull() ?: 0L
    val isPriceBelowCost = sellingPriceLong > 0 && costPriceLong > 0 && sellingPriceLong < costPriceLong

    val selectedCategoryName = categories.find { it.categoryId == selectedCategoryId }?.name ?: ""

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 340.dp, max = 640.dp)
                .fillMaxWidth(0.92f)
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(
                        if (isEditMode) R.string.dialog_edit_product_title else R.string.dialog_add_product_title
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    ProductImagePicker(
                        photoFilename = photoFilename,
                        onPhotoChanged = { photoFilename = it }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            localError = null
                        },
                        label = { Text(stringResource(R.string.dialog_product_name)) },
                        placeholder = { Text(stringResource(R.string.dialog_product_name_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_product_name")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = categoryDropdownExpanded,
                            onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded },
                            modifier = Modifier.weight(1.3f)
                        ) {
                            OutlinedTextField(
                                value = selectedCategoryName,
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                label = { Text(stringResource(R.string.dialog_product_category)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .testTag("select_category_anchor")
                            )

                            ExposedDropdownMenu(
                                expanded = categoryDropdownExpanded,
                                onDismissRequest = { categoryDropdownExpanded = false }
                            ) {
                                categories.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category.name) },
                                        onClick = {
                                            selectedCategoryId = category.categoryId
                                            categoryDropdownExpanded = false
                                            localError = null
                                        }
                                    )
                                }
                            }
                        }

                        if (isAdmin) {
                            OutlinedButton(
                                onClick = onAddNewCategory,
                                modifier = Modifier
                                    .height(56.dp)
                                    .testTag("btn_new_category")
                            ) {
                                Text(stringResource(R.string.dialog_add_category))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = barcode,
                        onValueChange = {
                            barcode = it
                            localError = null
                        },
                        label = { Text(stringResource(R.string.dialog_product_barcode)) },
                        placeholder = { Text(stringResource(R.string.dialog_product_barcode_hint)) },
                        leadingIcon = {
                            Icon(Icons.Default.QrCode, contentDescription = null)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_barcode")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = sellingPriceText,
                            onValueChange = {
                                sellingPriceText = it.filter { ch -> ch.isDigit() }
                                localError = null
                            },
                            label = { Text(stringResource(R.string.dialog_product_selling_price)) },
                            supportingText = {
                                if (sellingPriceLong > 0) {
                                    Text(sellingPriceLong.toRupiah(), fontWeight = FontWeight.SemiBold)
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_selling_price")
                        )

                        if (isAdmin) {
                            OutlinedTextField(
                                value = costPriceText,
                                onValueChange = {
                                    costPriceText = it.filter { ch -> ch.isDigit() }
                                    localError = null
                                },
                                label = { Text(stringResource(R.string.dialog_product_cost_price)) },
                                placeholder = { Text("0") },
                                supportingText = {
                                    if (costPriceLong >= 0) {
                                        Text(costPriceLong.toRupiah())
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_cost_price")
                            )
                        }
                    }

                    if (isAdmin && isPriceBelowCost) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("price_below_cost_warning")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(
                                        R.string.dialog_price_warning,
                                        sellingPriceLong.toRupiah(),
                                        costPriceLong.toRupiah()
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = initialStockText,
                            onValueChange = {
                                initialStockText = it.filter { ch -> ch.isDigit() }
                                localError = null
                            },
                            label = { Text(stringResource(R.string.dialog_product_initial_stock)) },
                            placeholder = { Text("0") },
                            supportingText = {
                                if (isEditMode) {
                                    Text(stringResource(R.string.dialog_product_stock_locked_hint))
                                }
                            },
                            enabled = !isEditMode,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_initial_stock")
                        )

                        OutlinedTextField(
                            value = minStockText,
                            onValueChange = {
                                minStockText = it.filter { ch -> ch.isDigit() }
                                localError = null
                            },
                            label = { Text(stringResource(R.string.dialog_product_min_stock)) },
                            placeholder = {
                                Text(stringResource(R.string.dialog_product_min_stock_hint, defaultMinStock))
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_min_stock")
                        )
                    }

                    localError?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_cancel_product")
                    ) {
                        Text(stringResource(R.string.dialog_btn_cancel))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (name.trim().isBlank()) {
                                localError = "Nama produk wajib diisi"
                                return@Button
                            }
                            if (selectedCategoryId <= 0L) {
                                localError = "Pilih kategori untuk produk"
                                return@Button
                            }
                            if (sellingPriceLong <= 0) {
                                localError = "Harga jual harus lebih besar dari 0"
                                return@Button
                            }
                            if (costPriceLong < 0) {
                                localError = "Harga modal tidak boleh negatif"
                                return@Button
                            }
                            val initialStock = initialStockText.toIntOrNull() ?: 0
                            if (initialStock < 0) {
                                localError = "Stok tidak boleh negatif"
                                return@Button
                            }

                            val product = ProductEntity(
                                productId = initialProduct?.productId ?: 0L,
                                barcode = barcode.trim().ifBlank { null },
                                name = name.trim(),
                                categoryId = selectedCategoryId,
                                price = sellingPriceLong,
                                costPrice = costPriceLong,
                                stock = if (isEditMode) initialProduct!!.stock else initialStock,
                                minStock = minStockText.toIntOrNull(),
                                photo = photoFilename,
                                status = initialProduct?.status ?: 1
                            )

                            onSaveProduct(product)
                        },
                        modifier = Modifier.testTag("btn_save_product")
                    ) {
                        Text(stringResource(R.string.dialog_btn_save))
                    }
                }
            }
        }
    }
}

@Composable
fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onSaveCategory: (String) -> Unit
) {
    var categoryName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_new_category_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = {
                        categoryName = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.dialog_new_category_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_category_name")
                )
                error?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (categoryName.trim().isBlank()) {
                        error = "Nama kategori tidak boleh kosong"
                    } else {
                        onSaveCategory(categoryName.trim())
                    }
                },
                modifier = Modifier.testTag("btn_confirm_add_category")
            ) {
                Text(stringResource(R.string.dialog_btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_btn_cancel))
            }
        }
    )
}