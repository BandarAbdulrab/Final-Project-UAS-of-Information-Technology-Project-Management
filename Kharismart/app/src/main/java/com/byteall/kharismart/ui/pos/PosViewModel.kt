package com.byteall.kharismart.ui.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byteall.kharismart.data.local.entity.CategoryEntity
import com.byteall.kharismart.data.local.entity.ProductEntity
import com.byteall.kharismart.data.local.entity.SettingsEntity
import com.byteall.kharismart.data.local.entity.TransactionEntity
import com.byteall.kharismart.data.repository.CartLine
import com.byteall.kharismart.data.repository.DebtRepository
import com.byteall.kharismart.data.repository.InventoryRepository
import com.byteall.kharismart.data.repository.SaleRepository
import com.byteall.kharismart.data.repository.SettingsRepository
import com.byteall.kharismart.session.AppSession
import com.byteall.kharismart.util.printer.ReceiptUiModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI presentation model for a line item in the POS active cart.
 *
 * Requirements:
 * - Prices and subtotals are strictly Long in Indonesian Rupiah.
 * - Quantities and stocks are Int.
 * - Enforces available stock limit (BR-01).
 */
data class CartItemUiModel(
    val product: ProductEntity,
    val quantity: Int,
    val unitPrice: Long,
    val subtotal: Long = unitPrice * quantity.toLong(),
    val maxAvailableStock: Int = product.stock
)

/**
 * Complete UI state for the Point of Sale screen.
 */
data class PosUiState(
    val catalogProducts: List<ProductEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategoryId: Long? = null,
    val searchQuery: String = "",
    val cartItems: List<CartItemUiModel> = emptyList(),
    val totalAmount: Long = 0L,
    val totalItemCount: Int = 0,
    val userMessage: String? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false
)

/**
 * ViewModel for the POS and Cart Screen (SRS-F05, F06, F07, F08, BR-01, BR-02, BR-03, BR-04, BR-05).
 *
 * Responsibilities:
 * - Manages active cart items and real-time total calculations in IDR.
 * - Intercepts hardware barcode scanner lookups via [InventoryRepository.findByBarcode].
 * - Validates stock constraints so that cart quantities never exceed available inventory.
 * - Provides catalog filtering by category and search query.
 * - Executes atomic checkout via [SaleRepository.completeCheckout].
 */
class PosViewModel(
    private val inventoryRepository: InventoryRepository,
    private val saleRepository: SaleRepository? = null,
    private val debtRepository: DebtRepository? = null,
    private val settingsRepository: SettingsRepository? = null
) : ViewModel() {

    private val _cartItems = MutableStateFlow<List<CartItemUiModel>>(emptyList())
    val cartItems: StateFlow<List<CartItemUiModel>> = _cartItems.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategory: StateFlow<Long?> = _selectedCategoryId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isSubmitting = MutableStateFlow(false)
    private val _completedReceipt = MutableStateFlow<ReceiptUiModel?>(null)
    val completedReceipt: StateFlow<ReceiptUiModel?> = _completedReceipt.asStateFlow()

    val totalAmount: StateFlow<Long> = _cartItems
        .map { items -> items.sumOf { it.subtotal } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    val debtorSuggestions: StateFlow<List<String>> = debtRepository?.observeCustomerNames()
        ?.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        ) ?: MutableStateFlow(emptyList())

    val storeSettings: StateFlow<SettingsEntity?> = settingsRepository?.observeSettings()
        ?.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        ) ?: MutableStateFlow(null)

    private data class CatalogData(
        val products: List<ProductEntity>,
        val categories: List<CategoryEntity>,
        val selectedCatId: Long?,
        val query: String
    )

    private val catalogFlow = combine(
        inventoryRepository.observeActiveProducts(),
        inventoryRepository.observeCategories(),
        _selectedCategoryId,
        _searchQuery
    ) { products, categories, selectedCatId, query ->
        val filtered = products.filter { product ->
            if (selectedCatId != null && product.categoryId != selectedCatId) {
                return@filter false
            }
            if (query.isNotBlank()) {
                val matchName = product.name.contains(query, ignoreCase = true)
                val matchBarcode = product.barcode?.contains(query, ignoreCase = true) == true
                if (!matchName && !matchBarcode) return@filter false
            }
            true
        }
        CatalogData(filtered, categories, selectedCatId, query)
    }

    private val feedbackFlow = combine(
        _userMessage,
        _errorMessage,
        _isSubmitting
    ) { userMsg, errorMsg, submitting ->
        Triple(userMsg, errorMsg, submitting)
    }

    val uiState: StateFlow<PosUiState> = combine(
        catalogFlow,
        _cartItems,
        feedbackFlow
    ) { catalog, cart, (userMsg, errorMsg, submitting) ->
        val total = cart.sumOf { it.subtotal }
        val count = cart.sumOf { it.quantity }

        PosUiState(
            catalogProducts = catalog.products,
            categories = catalog.categories,
            selectedCategoryId = catalog.selectedCatId,
            searchQuery = catalog.query,
            cartItems = cart,
            totalAmount = total,
            totalItemCount = count,
            userMessage = userMsg,
            errorMessage = errorMsg,
            isSubmitting = submitting
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PosUiState()
    )

    fun onCategorySelected(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    /**
     * Handles hardware scanner input ending in Enter.
     * Looks up the barcode in the inventory repository and appends/increments the product.
     */
    fun onBarcodeScanned(barcode: String) {
        val cleanBarcode = barcode.trim()
        if (cleanBarcode.isBlank()) return

        viewModelScope.launch {
            val product = inventoryRepository.findByBarcode(cleanBarcode)
            if (product == null) {
                _errorMessage.value = "Produk dengan barcode '$cleanBarcode' tidak ditemukan"
                return@launch
            }
            addToCart(product)
        }
    }

    /**
     * Adds a product to the cart or increments its quantity by 1 if already in the cart,
     * ensuring it does not exceed available stock.
     */
    fun addToCart(product: ProductEntity) {
        if (product.stock <= 0) {
            _errorMessage.value = "Stok '${product.name}' habis dan tidak dapat ditambahkan"
            return
        }

        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.productId == product.productId }

        if (index >= 0) {
            val existing = currentList[index]
            if (existing.quantity + 1 > product.stock) {
                _errorMessage.value = "Stok '${product.name}' tidak mencukupi (tersedia: ${product.stock})"
                return
            }
            val newQty = existing.quantity + 1
            currentList[index] = existing.copy(
                quantity = newQty,
                subtotal = existing.unitPrice * newQty.toLong()
            )
        } else {
            currentList.add(
                CartItemUiModel(
                    product = product,
                    quantity = 1,
                    unitPrice = product.price,
                    subtotal = product.price,
                    maxAvailableStock = product.stock
                )
            )
        }
        _cartItems.value = currentList
    }

    /**
     * Adjusts the quantity of a cart line item by [delta] (+1 or -1).
     * If quantity reaches 0, the item is removed.
     */
    fun updateQuantity(productId: Long, delta: Int) {
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.productId == productId }
        if (index < 0) return

        val existing = currentList[index]
        val newQty = existing.quantity + delta

        if (newQty <= 0) {
            currentList.removeAt(index)
        } else if (newQty > existing.maxAvailableStock) {
            _errorMessage.value = "Stok '${existing.product.name}' tidak mencukupi (tersedia: ${existing.maxAvailableStock})"
            return
        } else {
            currentList[index] = existing.copy(
                quantity = newQty,
                subtotal = existing.unitPrice * newQty.toLong()
            )
        }
        _cartItems.value = currentList
    }

    /**
     * Removes an item completely from the active cart.
     */
    fun removeItem(productId: Long) {
        _cartItems.value = _cartItems.value.filterNot { it.product.productId == productId }
    }

    /**
     * Empties the entire active cart.
     */
    fun clearCart() {
        _cartItems.value = emptyList()
    }

    /**
     * Executes atomic checkout for the current cart lines (SRS-F07, F08).
     */
    fun completeCheckout(
        method: String,
        paidAmount: Long,
        customerName: String?,
        note: String?,
        userId: Long? = null,
        onSuccess: ((TransactionEntity) -> Unit)? = null
    ) {
        val cart = _cartItems.value
        if (cart.isEmpty()) {
            _errorMessage.value = "Keranjang belanja kosong"
            return
        }

        val repo = saleRepository
        if (repo == null) {
            _errorMessage.value = "SaleRepository belum tersedia"
            return
        }

        val effectiveUserId = userId ?: AppSession.currentUser.value?.userId ?: 1L
        val lines = cart.map { CartLine(productId = it.product.productId, quantity = it.quantity) }

        _isSubmitting.value = true
        viewModelScope.launch {
            val result = repo.completeCheckout(
                lines = lines,
                method = method,
                paidAmount = paidAmount,
                customerName = customerName,
                note = note,
                userId = effectiveUserId
            )

            _isSubmitting.value = false
            result.onSuccess { transaction ->
                clearCart()
                _userMessage.value = "Transaksi berhasil disimpan! No: ${transaction.receiptNo}"
                viewModelScope.launch {
                    val receiptData = repo.getReceiptData(transaction.transactionId)
                    if (receiptData != null) {
                        _completedReceipt.value = ReceiptUiModel.fromReceiptData(receiptData, isReprint = false)
                    }
                }
                onSuccess?.invoke(transaction)
            }.onFailure { error ->
                _errorMessage.value = error.message ?: "Gagal memproses transaksi"
            }
        }
    }

    fun dismissCompletedReceipt() {
        _completedReceipt.value = null
    }
}
