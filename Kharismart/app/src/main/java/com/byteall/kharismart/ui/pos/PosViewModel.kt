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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CartItemUiModel(
    val product: ProductEntity,
    val quantity: Int,
    val unitPrice: Long,
    val subtotal: Long = unitPrice * quantity.toLong(),
    val maxAvailableStock: Int = product.stock
)

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

class PosViewModel(
    private val inventoryRepository: InventoryRepository,
    private val saleRepository: SaleRepository? = null,
    private val debtRepository: DebtRepository? = null,
    private val settingsRepository: SettingsRepository? = null
) : ViewModel() {

    private var rawProducts: List<ProductEntity> = emptyList()
    private var rawCategories: List<CategoryEntity> = emptyList()

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

    private val _totalAmount = MutableStateFlow(0L)
    val totalAmount: StateFlow<Long> = _totalAmount.asStateFlow()

    private val _uiState = MutableStateFlow(PosUiState())
    val uiState: StateFlow<PosUiState> = _uiState.asStateFlow()

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

    init {
        viewModelScope.launch {
            combine(
                inventoryRepository.observeActiveProducts(),
                inventoryRepository.observeCategories()
            ) { products, categories ->
                products to categories
            }.collect { (products, categories) ->
                rawProducts = products
                rawCategories = categories
                syncState()
            }
        }
    }

    private fun syncState() {
        val selectedCatId = _selectedCategoryId.value
        val query = _searchQuery.value
        val cart = _cartItems.value
        val total = cart.sumOf { it.subtotal }
        val count = cart.sumOf { it.quantity }

        val filteredProducts = rawProducts.filter { product ->
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

        _totalAmount.value = total
        _uiState.value = PosUiState(
            catalogProducts = filteredProducts,
            categories = rawCategories,
            selectedCategoryId = selectedCatId,
            searchQuery = query,
            cartItems = cart,
            totalAmount = total,
            totalItemCount = count,
            userMessage = _userMessage.value,
            errorMessage = _errorMessage.value,
            isSubmitting = _isSubmitting.value
        )
    }

    fun onCategorySelected(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
        syncState()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        syncState()
    }

    fun clearUserMessage() {
        _userMessage.value = null
        syncState()
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
        syncState()
    }

    fun onBarcodeScanned(barcode: String) {
        val cleanBarcode = barcode.trim()
        if (cleanBarcode.isBlank()) return

        viewModelScope.launch {
            val product = inventoryRepository.findByBarcode(cleanBarcode)
            if (product == null) {
                _errorMessage.value = "Produk dengan barcode '$cleanBarcode' tidak ditemukan"
                syncState()
                return@launch
            }
            addToCart(product)
        }
    }

    fun addToCart(product: ProductEntity) {
        if (product.stock <= 0) {
            _errorMessage.value = "Stok '${product.name}' habis dan tidak dapat ditambahkan"
            syncState()
            return
        }

        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.productId == product.productId }

        if (index >= 0) {
            val existing = currentList[index]
            if (existing.quantity + 1 > product.stock) {
                _errorMessage.value = "Stok '${product.name}' tidak mencukupi (tersedia: ${product.stock})"
                syncState()
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
        syncState()
    }

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
            syncState()
            return
        } else {
            currentList[index] = existing.copy(
                quantity = newQty,
                subtotal = existing.unitPrice * newQty.toLong()
            )
        }
        _cartItems.value = currentList
        syncState()
    }

    fun removeItem(productId: Long) {
        _cartItems.value = _cartItems.value.filterNot { it.product.productId == productId }
        syncState()
    }

    fun clearCart() {
        _cartItems.value = emptyList()
        syncState()
    }

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
            syncState()
            return
        }

        val repo = saleRepository
        if (repo == null) {
            _errorMessage.value = "SaleRepository belum tersedia"
            syncState()
            return
        }

        val effectiveUserId = userId ?: AppSession.currentUser.value?.userId ?: 1L
        val lines = cart.map { CartLine(productId = it.product.productId, quantity = it.quantity) }

        _isSubmitting.value = true
        syncState()
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
                _cartItems.value = emptyList()
                _userMessage.value = "Transaksi berhasil disimpan! No: ${transaction.receiptNo}"
                syncState()
                viewModelScope.launch {
                    val receiptData = repo.getReceiptData(transaction.transactionId)
                    if (receiptData != null) {
                        _completedReceipt.value = ReceiptUiModel.fromReceiptData(receiptData, isReprint = false)
                    }
                }
                onSuccess?.invoke(transaction)
            }.onFailure { error ->
                _errorMessage.value = error.message ?: "Gagal memproses transaksi"
                syncState()
            }
        }
    }

    fun dismissCompletedReceipt() {
        _completedReceipt.value = null
    }
}