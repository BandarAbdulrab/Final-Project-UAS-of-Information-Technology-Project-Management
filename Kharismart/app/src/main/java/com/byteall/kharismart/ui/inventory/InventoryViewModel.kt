package com.byteall.kharismart.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byteall.kharismart.data.local.entity.CategoryEntity
import com.byteall.kharismart.data.local.entity.ProductEntity
import com.byteall.kharismart.data.repository.InventoryRepository
import com.byteall.kharismart.data.repository.SettingsRepository
import com.byteall.kharismart.data.repository.StockStatus
import com.byteall.kharismart.data.repository.stockStatus
import com.byteall.kharismart.session.AppSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI presentation model for a single product card in the catalog.
 */
data class ProductCardUiModel(
    val product: ProductEntity,
    val categoryName: String,
    val stockStatus: StockStatus
)

/**
 * Represents which modal dialog is currently open in the Inventory screen.
 */
sealed interface InventoryDialogState {
    data object None : InventoryDialogState
    data object AddProduct : InventoryDialogState
    data class EditProduct(val product: ProductEntity) : InventoryDialogState
    data class Restock(val product: ProductEntity) : InventoryDialogState
    data class StockOpname(val product: ProductEntity) : InventoryDialogState
    data object AddCategory : InventoryDialogState
}

/**
 * Complete UI state for the Inventory screen.
 */
data class InventoryUiState(
    val products: List<ProductCardUiModel> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategoryId: Long? = null,
    val searchQuery: String = "",
    val lowStockFilter: Boolean = false,
    val defaultMinStock: Int = 5,
    val isAdmin: Boolean = false,
    val dialogState: InventoryDialogState = InventoryDialogState.None,
    val isLoading: Boolean = false,
    val userMessage: String? = null,
    val errorMessage: String? = null
)

/**
 * ViewModel for Master Catalog, Restock, and Stock Opname (SRS-F04, F11, F12).
 *
 * Responsibilities:
 * - Exposes reactive product list filtered by category, search term, and low-stock status.
 * - Handles add/edit product transactions via [InventoryRepository.saveProduct].
 * - Handles atomic restock actions via [InventoryRepository.restock].
 * - Handles stock opname physical count adjustments via [InventoryRepository.adjustStock].
 * - Strictly respects role-based visibility: hides cost prices and restock/opname actions for cashiers.
 */
class InventoryViewModel(
    private val inventoryRepository: InventoryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _lowStockFilter = MutableStateFlow(false)
    private val _dialogState = MutableStateFlow<InventoryDialogState>(InventoryDialogState.None)
    private val _isLoading = MutableStateFlow(false)
    private val _userMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    // Combined data from Room repositories
    private val repoDataFlow = combine(
        inventoryRepository.observeActiveProducts(),
        inventoryRepository.observeCategories(),
        settingsRepository.observeSettings()
    ) { products, categories, settings ->
        Triple(products, categories, settings)
    }

    // Combined filter parameters
    private val filterFlow = combine(
        _selectedCategoryId,
        _searchQuery,
        _lowStockFilter
    ) { categoryId, query, lowStockOnly ->
        Triple(categoryId, query, lowStockOnly)
    }

    // Combined dialog and feedback status
    private val statusFlow = combine(
        _dialogState,
        _isLoading,
        _userMessage,
        _errorMessage
    ) { dialog, loading, userMsg, errorMsg ->
        StatusState(dialog, loading, userMsg, errorMsg)
    }

    private data class StatusState(
        val dialog: InventoryDialogState,
        val loading: Boolean,
        val userMsg: String?,
        val errorMsg: String?
    )

    val uiState: StateFlow<InventoryUiState> = combine(
        repoDataFlow,
        filterFlow,
        statusFlow,
        AppSession.currentUser
    ) { (rawProducts, categories, settings), (selectedCategory, query, lowStockOnly), status, sessionUser ->
        val defaultMin = settings.defaultMinStock
        val categoryMap = categories.associate { it.categoryId to it.name }
        val isAdmin = sessionUser?.role == com.byteall.kharismart.session.UserRole.ADMIN

        val filteredProducts = rawProducts
            .filter { product ->
                // Category filter
                if (selectedCategory != null && product.categoryId != selectedCategory) {
                    return@filter false
                }
                // Text search filter (matches name or barcode)
                if (query.isNotBlank()) {
                    val matchesName = product.name.contains(query, ignoreCase = true)
                    val matchesBarcode = product.barcode?.contains(query, ignoreCase = true) == true
                    if (!matchesName && !matchesBarcode) return@filter false
                }
                // Low stock filter (items with Menipis or Habis)
                if (lowStockOnly) {
                    val statusPill = product.stockStatus(defaultMin)
                    if (statusPill == StockStatus.SAFE) return@filter false
                }
                true
            }
            .map { product ->
                ProductCardUiModel(
                    product = product,
                    categoryName = categoryMap[product.categoryId] ?: "-",
                    stockStatus = product.stockStatus(defaultMin)
                )
            }

        InventoryUiState(
            products = filteredProducts,
            categories = categories,
            selectedCategoryId = selectedCategory,
            searchQuery = query,
            lowStockFilter = lowStockOnly,
            defaultMinStock = defaultMin,
            isAdmin = isAdmin,
            dialogState = status.dialog,
            isLoading = status.loading,
            userMessage = status.userMsg,
            errorMessage = status.errorMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InventoryUiState(isAdmin = AppSession.isAdmin())
    )

    fun selectCategory(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleLowStockFilter() {
        _lowStockFilter.value = !_lowStockFilter.value
    }

    fun openAddProductDialog() {
        _errorMessage.value = null
        _dialogState.value = InventoryDialogState.AddProduct
    }

    fun openEditProductDialog(product: ProductEntity) {
        _errorMessage.value = null
        _dialogState.value = InventoryDialogState.EditProduct(product)
    }

    fun openRestockDialog(product: ProductEntity) {
        _errorMessage.value = null
        _dialogState.value = InventoryDialogState.Restock(product)
    }

    fun openStockOpnameDialog(product: ProductEntity) {
        _errorMessage.value = null
        _dialogState.value = InventoryDialogState.StockOpname(product)
    }

    fun openAddCategoryDialog() {
        _errorMessage.value = null
        _dialogState.value = InventoryDialogState.AddCategory
    }

    fun dismissDialog() {
        _dialogState.value = InventoryDialogState.None
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    /**
     * Creates or updates a product.
     * Note: Initial stock is only saved on creation; catalog editing preserves existing stock.
     */
    fun saveProduct(product: ProductEntity, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val userId = AppSession.currentUser.value?.userId ?: 1L
            val result = inventoryRepository.saveProduct(product, userId)
            _isLoading.value = false

            result.fold(
                onSuccess = {
                    _dialogState.value = InventoryDialogState.None
                    _userMessage.value = "Data produk berhasil disimpan"
                    onSuccess?.invoke()
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Gagal menyimpan produk"
                }
            )
        }
    }

    /**
     * Atomically adds stock and inserts a 'restock' movement record into stock_movements.
     */
    fun restockProduct(
        productId: Long,
        quantity: Int,
        unitCost: Long,
        updateCostPrice: Boolean,
        onSuccess: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val userId = AppSession.currentUser.value?.userId ?: 1L
            val result = inventoryRepository.restock(
                productId = productId,
                quantity = quantity,
                unitCost = unitCost,
                updateCostPrice = updateCostPrice,
                userId = userId
            )
            _isLoading.value = false

            result.fold(
                onSuccess = {
                    _dialogState.value = InventoryDialogState.None
                    _userMessage.value = "Restock barang berhasil disimpan"
                    onSuccess?.invoke()
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Gagal melakukan restock"
                }
            )
        }
    }

    /**
     * Atomically reconciles physical stock count and inserts an 'adjustment' movement record.
     * (BR-12: Does not create finance entries).
     */
    fun adjustStock(
        productId: Long,
        countedStock: Int,
        reason: String,
        onSuccess: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val userId = AppSession.currentUser.value?.userId ?: 1L
            val result = inventoryRepository.adjustStock(
                productId = productId,
                countedStock = countedStock,
                reason = reason,
                userId = userId
            )
            _isLoading.value = false

            result.fold(
                onSuccess = {
                    _dialogState.value = InventoryDialogState.None
                    _userMessage.value = "Penyesuaian stok opname berhasil disimpan"
                    onSuccess?.invoke()
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Gagal menyesuaikan stok opname"
                }
            )
        }
    }

    /**
     * Adds a new category from the product dialog.
     */
    fun addCategory(name: String, onSuccess: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = inventoryRepository.addCategory(name)
            _isLoading.value = false

            result.fold(
                onSuccess = { newId ->
                    _userMessage.value = "Kategori berhasil ditambahkan"
                    _dialogState.value = InventoryDialogState.None
                    onSuccess?.invoke(newId)
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Gagal menambahkan kategori"
                }
            )
        }
    }
}
