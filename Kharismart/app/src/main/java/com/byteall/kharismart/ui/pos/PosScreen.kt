package com.byteall.kharismart.ui.pos

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.byteall.kharismart.R
import com.byteall.kharismart.data.local.entity.ProductEntity
import com.byteall.kharismart.di.appViewModel
import com.byteall.kharismart.ui.receipt.ReceiptDialog
import com.byteall.kharismart.util.barcode.interceptBarcodeKeyEvents
import com.byteall.kharismart.util.format.toRupiah
import com.byteall.kharismart.util.image.ImageStorageManager
import kotlinx.coroutines.launch

@Composable
fun PosScreen(
    viewModel: PosViewModel = appViewModel { container ->
        PosViewModel(
            inventoryRepository = container.inventoryRepository,
            saleRepository = container.saleRepository,
            debtRepository = container.debtRepository,
            settingsRepository = container.settingsRepository
        )
    },
    onProceedToCheckout: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val storeSettings by viewModel.storeSettings.collectAsStateWithLifecycle()
    val debtorSuggestions by viewModel.debtorSuggestions.collectAsStateWithLifecycle()
    val completedReceipt by viewModel.completedReceipt.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val barcodeFocusRequester = remember { FocusRequester() }
    var barcodeInputText by remember { mutableStateOf("") }
    var showClearCartConfirmation by remember { mutableStateOf(false) }
    var showCheckoutDialog by remember { mutableStateOf(false) }

    val handleCheckoutClick: () -> Unit = {
        if (uiState.cartItems.isEmpty()) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.checkout_err_empty_cart))
            }
        } else if (onProceedToCheckout != null) {
            onProceedToCheckout()
        } else {
            showCheckoutDialog = true
        }
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.interceptBarcodeKeyEvents { scannedCode ->
            viewModel.onBarcodeScanned(scannedCode)
        }
    ) { paddingValues ->
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.56f)
                        .fillMaxHeight()
                ) {
                    CatalogSection(
                        uiState = uiState,
                        barcodeInputText = barcodeInputText,
                        onBarcodeInputChanged = { barcodeInputText = it },
                        barcodeFocusRequester = barcodeFocusRequester,
                        onBarcodeSubmit = {
                            viewModel.onBarcodeScanned(it)
                            barcodeInputText = ""
                            focusManager.clearFocus()
                        },
                        onSearchChanged = { viewModel.onSearchQueryChanged(it) },
                        onCategorySelected = { viewModel.onCategorySelected(it) },
                        onProductClick = { product ->
                            viewModel.addToCart(product)
                        }
                    )
                }

                Card(
                    modifier = Modifier
                        .weight(0.44f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    CartSection(
                        uiState = uiState,
                        onUpdateQuantity = { id, delta ->
                            viewModel.updateQuantity(id, delta)
                        },
                        onRemoveItem = { id ->
                            viewModel.removeItem(id)
                        },
                        onClearCartClick = { showClearCartConfirmation = true },
                        onCheckoutClick = handleCheckoutClick
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(10.dp)
            ) {
                Box(modifier = Modifier.weight(0.52f)) {
                    CatalogSection(
                        uiState = uiState,
                        barcodeInputText = barcodeInputText,
                        onBarcodeInputChanged = { barcodeInputText = it },
                        barcodeFocusRequester = barcodeFocusRequester,
                        onBarcodeSubmit = {
                            viewModel.onBarcodeScanned(it)
                            barcodeInputText = ""
                            focusManager.clearFocus()
                        },
                        onSearchChanged = { viewModel.onSearchQueryChanged(it) },
                        onCategorySelected = { viewModel.onCategorySelected(it) },
                        onProductClick = { product ->
                            viewModel.addToCart(product)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier
                        .weight(0.48f)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    CartSection(
                        uiState = uiState,
                        onUpdateQuantity = { id, delta -> viewModel.updateQuantity(id, delta) },
                        onRemoveItem = { id -> viewModel.removeItem(id) },
                        onClearCartClick = { showClearCartConfirmation = true },
                        onCheckoutClick = handleCheckoutClick
                    )
                }
            }
        }
    }

    if (showCheckoutDialog) {
        CheckoutDialog(
            totalAmount = uiState.totalAmount,
            qrisBank = storeSettings?.qrisBank,
            qrisOwnerName = storeSettings?.qrisOwnerName,
            qrisImageFilename = storeSettings?.qrisImage,
            debtorSuggestions = debtorSuggestions,
            isSubmitting = uiState.isSubmitting,
            onDismissRequest = {
                showCheckoutDialog = false
            },
            onConfirmCheckout = { method, paidAmount, customerName, note ->
                viewModel.completeCheckout(
                    method = method,
                    paidAmount = paidAmount,
                    customerName = customerName,
                    note = note,
                    onSuccess = {
                        showCheckoutDialog = false
                    }
                )
            }
        )
    }

    completedReceipt?.let { receipt ->
        ReceiptDialog(
            receipt = receipt,
            onDismiss = {
                viewModel.dismissCompletedReceipt()
            }
        )
    }

    if (showClearCartConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearCartConfirmation = false },
            title = { Text(stringResource(R.string.pos_cart_clear_confirm_title)) },
            text = { Text(stringResource(R.string.pos_cart_clear_confirm_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearCart()
                        showClearCartConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.pos_cart_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCartConfirmation = false }) {
                    Text(stringResource(R.string.dialog_btn_cancel))
                }
            }
        )
    }
}

@Composable
private fun CatalogSection(
    uiState: PosUiState,
    barcodeInputText: String,
    onBarcodeInputChanged: (String) -> Unit,
    barcodeFocusRequester: FocusRequester,
    onBarcodeSubmit: (String) -> Unit,
    onSearchChanged: (String) -> Unit,
    onCategorySelected: (Long?) -> Unit,
    onProductClick: (ProductEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = barcodeInputText,
                onValueChange = { input ->
                    if (input.endsWith("\n") || input.endsWith("\r")) {
                        val scanned = input.trim()
                        if (scanned.isNotEmpty()) {
                            onBarcodeSubmit(scanned)
                        }
                    } else {
                        onBarcodeInputChanged(input)
                    }
                },
                placeholder = {
                    Text(
                        text = stringResource(R.string.pos_barcode_input_hint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (barcodeInputText.isNotBlank()) {
                        IconButton(onClick = { onBarcodeSubmit(barcodeInputText.trim()) }) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Tambah Barcode",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val code = barcodeInputText.trim()
                        if (code.isNotEmpty()) {
                            onBarcodeSubmit(code)
                        }
                    }
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(barcodeFocusRequester)
                    .testTag("input_barcode_scanner")
            )

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchChanged,
                placeholder = {
                    Text(
                        text = stringResource(R.string.pos_search_hint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("input_pos_search")
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = uiState.selectedCategoryId == null,
                onClick = { onCategorySelected(null) },
                label = { Text(stringResource(R.string.pos_tab_all)) },
                modifier = Modifier.testTag("pos_chip_cat_all")
            )

            uiState.categories.forEach { category ->
                FilterChip(
                    selected = uiState.selectedCategoryId == category.categoryId,
                    onClick = { onCategorySelected(category.categoryId) },
                    label = { Text(category.name) },
                    modifier = Modifier.testTag("pos_chip_cat_${category.categoryId}")
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (uiState.catalogProducts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("pos_empty_catalog"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.pos_catalog_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = uiState.catalogProducts,
                    key = { it.productId }
                ) { product ->
                    PosProductGridCard(
                        product = product,
                        onClick = { onProductClick(product) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PosProductGridCard(
    product: ProductEntity,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val isOutOfStock = product.stock <= 0

    val imageFile = remember(product.photo) {
        if (!product.photo.isNullOrBlank()) {
            ImageStorageManager.getImageFile(context, product.photo)
        } else {
            null
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pos_card_${product.productId}")
            .then(
                if (!isOutOfStock) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOutOfStock) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (imageFile != null && imageFile.exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageFile)
                            .crossfade(true)
                            .build(),
                        contentDescription = product.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                if (isOutOfStock) {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.pos_catalog_out_of_stock),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onError,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = product.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isOutOfStock) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = product.price.toRupiah(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isOutOfStock) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary
            )

            Text(
                text = stringResource(R.string.pos_item_stock_info, product.stock),
                style = MaterialTheme.typography.labelSmall,
                color = if (isOutOfStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CartSection(
    uiState: PosUiState,
    onUpdateQuantity: (productId: Long, delta: Int) -> Unit,
    onRemoveItem: (productId: Long) -> Unit,
    onClearCartClick: () -> Unit,
    onCheckoutClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.pos_cart_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (uiState.totalItemCount > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.pos_cart_items_count, uiState.totalItemCount),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (uiState.cartItems.isNotEmpty()) {
                TextButton(
                    onClick = onClearCartClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("btn_clear_cart")
                ) {
                    Text(
                        text = stringResource(R.string.pos_cart_clear),
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

        if (uiState.cartItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("pos_empty_cart"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.pos_cart_empty_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("pos_cart_list"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = uiState.cartItems,
                    key = { it.product.productId }
                ) { item ->
                    CartItemRow(
                        item = item,
                        onUpdateQuantity = onUpdateQuantity,
                        onRemoveItem = onRemoveItem
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.pos_cart_total),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = uiState.totalAmount.toRupiah(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("text_cart_total")
                )
            }

            Button(
                onClick = onCheckoutClick,
                enabled = uiState.cartItems.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(44.dp)
                    .testTag("btn_pos_checkout")
            ) {
                Text(
                    text = stringResource(R.string.pos_btn_checkout),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun CartItemRow(
    item: CartItemUiModel,
    onUpdateQuantity: (productId: Long, delta: Int) -> Unit,
    onRemoveItem: (productId: Long) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cart_item_${item.product.productId}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.product.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.unitPrice.toRupiah()} = ${item.subtotal.toRupiah()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("subtotal_${item.product.productId}")
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = { onUpdateQuantity(item.product.productId, -1) },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("btn_decrement_${item.product.productId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Kurang",
                        modifier = Modifier.size(16.dp)
                    )
                }

                Text(
                    text = item.quantity.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .testTag("qty_text_${item.product.productId}")
                )

                IconButton(
                    onClick = { onUpdateQuantity(item.product.productId, 1) },
                    enabled = item.quantity < item.maxAvailableStock,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("btn_increment_${item.product.productId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Tambah",
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = { onRemoveItem(item.product.productId) },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("btn_remove_${item.product.productId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Hapus",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}