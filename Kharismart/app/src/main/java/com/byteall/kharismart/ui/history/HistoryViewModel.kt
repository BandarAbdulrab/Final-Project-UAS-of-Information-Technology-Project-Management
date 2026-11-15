package com.byteall.kharismart.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byteall.kharismart.data.local.model.TransactionWithCashier
import com.byteall.kharismart.data.repository.ReceiptData
import com.byteall.kharismart.data.repository.SaleRepository
import com.byteall.kharismart.session.AppSession
import com.byteall.kharismart.util.printer.ReceiptUiModel
import com.byteall.kharismart.util.time.WibClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Filter period presets for Transaction History.
 */
enum class HistoryPeriodPreset {
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    THIS_MONTH
}

/**
 * UI State for Transaction History and Reprinting (SRS-F10).
 */
data class HistoryUiState(
    val transactions: List<TransactionWithCashier> = emptyList(),
    val selectedPreset: HistoryPeriodPreset = HistoryPeriodPreset.TODAY,
    val searchQuery: String = "",
    val totalSalesCount: Int = 0,
    val totalRevenue: Long = 0L,
    val totalGrossProfit: Long = 0L,
    val isAdmin: Boolean = false,
    val expandedIds: Set<Long> = emptySet(),
    val expandedReceipts: Map<Long, ReceiptData> = emptyMap(),
    val activeReceiptDialog: ReceiptUiModel? = null,
    val isLoading: Boolean = false
)

/**
 * ViewModel for Transaction History and Receipt Reprinting (SRS-F09, F10).
 *
 * Rules:
 * - Cashiers are strictly locked to Today's sales (cannot access past dates).
 * - Admins can select any period preset (Today, Yesterday, This Week, This Month).
 * - Gross profit is visible ONLY to Admin users.
 * - Rows expand to display line items lazily fetched from [SaleRepository.getReceiptData].
 * - "Reprint / Copy" creates a [ReceiptUiModel] flagged with `isReprint = true`.
 */
class HistoryViewModel(
    private val saleRepository: SaleRepository
) : ViewModel() {

    private val _selectedPreset = MutableStateFlow(HistoryPeriodPreset.TODAY)
    val selectedPreset: StateFlow<HistoryPeriodPreset> = _selectedPreset.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _expandedIds = MutableStateFlow<Set<Long>>(emptySet())
    val expandedIds: StateFlow<Set<Long>> = _expandedIds.asStateFlow()

    private val _expandedReceipts = MutableStateFlow<Map<Long, ReceiptData>>(emptyMap())

    private val _activeReceiptDialog = MutableStateFlow<ReceiptUiModel?>(null)
    val activeReceiptDialog: StateFlow<ReceiptUiModel?> = _activeReceiptDialog.asStateFlow()

    private val isAdmin: Boolean
        get() = AppSession.isAdmin()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val transactionsFlow = _selectedPreset.flatMapLatest { preset ->
        val range = computeRange(preset)
        saleRepository.observeTransactions(range)
    }

    private data class FilterParams(
        val preset: HistoryPeriodPreset,
        val query: String
    )

    private val filterFlow = combine(
        _selectedPreset,
        _searchQuery
    ) { preset, query ->
        FilterParams(preset, query)
    }

    private data class UiDetails(
        val expanded: Set<Long>,
        val receipts: Map<Long, ReceiptData>,
        val activeDialog: ReceiptUiModel?
    )

    private val detailsFlow = combine(
        _expandedIds,
        _expandedReceipts,
        _activeReceiptDialog
    ) { expanded, receipts, dialog ->
        UiDetails(expanded, receipts, dialog)
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        transactionsFlow,
        filterFlow,
        detailsFlow
    ) { transactions, filter, details ->
        val filtered = transactions.filter { item ->
            if (filter.query.isBlank()) return@filter true
            val matchesReceipt = item.transaction.receiptNo.contains(filter.query, ignoreCase = true)
            val matchesCashier = item.cashierName.contains(filter.query, ignoreCase = true)
            matchesReceipt || matchesCashier
        }

        val count = filtered.size
        val revenue = filtered.sumOf { it.transaction.total }
        val grossProfit = filtered.sumOf { it.transaction.grossProfit }

        HistoryUiState(
            transactions = filtered,
            selectedPreset = if (!isAdmin) HistoryPeriodPreset.TODAY else filter.preset,
            searchQuery = filter.query,
            totalSalesCount = count,
            totalRevenue = revenue,
            totalGrossProfit = grossProfit,
            isAdmin = isAdmin,
            expandedIds = details.expanded,
            expandedReceipts = details.receipts,
            activeReceiptDialog = details.activeDialog,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState(isAdmin = isAdmin)
    )

    fun onPresetSelected(preset: HistoryPeriodPreset) {
        if (!isAdmin) {
            // Cashiers cannot change period (locked to Today)
            return
        }
        _selectedPreset.value = preset
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    /**
     * Expands/collapses a transaction card. If newly expanded, fetches item details.
     */
    fun toggleExpandTransaction(transactionId: Long) {
        val currentSet = _expandedIds.value.toMutableSet()
        if (currentSet.contains(transactionId)) {
            currentSet.remove(transactionId)
            _expandedIds.value = currentSet
        } else {
            currentSet.add(transactionId)
            _expandedIds.value = currentSet

            if (!_expandedReceipts.value.containsKey(transactionId)) {
                viewModelScope.launch {
                    val data = saleRepository.getReceiptData(transactionId)
                    if (data != null) {
                        _expandedReceipts.value = _expandedReceipts.value + (transactionId to data)
                    }
                }
            }
        }
    }

    /**
     * Loads receipt data and triggers the [ReceiptDialog] with `isReprint = true`.
     */
    fun reprintReceipt(transactionId: Long) {
        viewModelScope.launch {
            val cached = _expandedReceipts.value[transactionId]
            val data = cached ?: saleRepository.getReceiptData(transactionId)
            if (data != null) {
                _activeReceiptDialog.value = ReceiptUiModel.fromReceiptData(data, isReprint = true)
            }
        }
    }

    fun dismissReceiptDialog() {
        _activeReceiptDialog.value = null
    }

    private fun computeRange(preset: HistoryPeriodPreset): WibClock.DateRange {
        val today = WibClock.today()
        return when (preset) {
            HistoryPeriodPreset.TODAY -> WibClock.dayRange(today)
            HistoryPeriodPreset.YESTERDAY -> WibClock.dayRange(today.minusDays(1))
            HistoryPeriodPreset.THIS_WEEK -> WibClock.weekRange(today)
            HistoryPeriodPreset.THIS_MONTH -> WibClock.monthRange(today)
        }
    }
}
