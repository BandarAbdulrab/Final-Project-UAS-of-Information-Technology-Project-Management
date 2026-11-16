package com.byteall.kharismart.ui.finance

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byteall.kharismart.R
import com.byteall.kharismart.data.local.FinanceType
import com.byteall.kharismart.data.local.entity.FinanceEntity
import com.byteall.kharismart.data.repository.FinanceRepository
import com.byteall.kharismart.session.AppSession
import com.byteall.kharismart.util.format.digitsToLongOrNull
import com.byteall.kharismart.util.time.WibClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class FinancePeriodFilter(@StringRes val labelRes: Int) {
    TODAY(R.string.finance_period_today),
    THIS_WEEK(R.string.finance_period_week),
    THIS_MONTH(R.string.finance_period_month),
    ALL_TIME(R.string.finance_period_all)
}

enum class FinanceTypeFilter(@StringRes val labelRes: Int) {
    ALL(R.string.finance_filter_all),
    INCOME(R.string.finance_filter_income),
    EXPENSE(R.string.finance_filter_expense)
}

enum class FinanceSortOption(@StringRes val labelRes: Int) {
    DATE_DESC(R.string.finance_sort_newest),
    DATE_ASC(R.string.finance_sort_oldest),
    AMOUNT_DESC(R.string.finance_sort_amount_desc),
    AMOUNT_ASC(R.string.finance_sort_amount_asc),
    CATEGORY_ASC(R.string.finance_sort_category)
}

sealed interface FinanceUiMessage {
    data class Resource(@StringRes val resId: Int) : FinanceUiMessage
    data class Plain(val text: String) : FinanceUiMessage
}

data class FinanceFilterState(
    val period: FinancePeriodFilter = FinancePeriodFilter.THIS_MONTH,
    val typeFilter: FinanceTypeFilter = FinanceTypeFilter.ALL,
    val sortOption: FinanceSortOption = FinanceSortOption.DATE_DESC,
    val searchQuery: String = ""
)

data class FinanceDialogState(
    val isAddDialogOpen: Boolean = false,
    val recordToDelete: FinanceEntity? = null,
    val isSubmitting: Boolean = false,
    val bannerMessage: FinanceUiMessage? = null,
    val isBannerError: Boolean = false,
    val formErrorMessage: FinanceUiMessage? = null
)

data class ManualFinanceUiState(
    val records: List<FinanceEntity> = emptyList(),
    val totalIncome: Long = 0L,
    val totalExpense: Long = 0L,
    val netAmount: Long = 0L,
    val period: FinancePeriodFilter = FinancePeriodFilter.THIS_MONTH,
    val typeFilter: FinanceTypeFilter = FinanceTypeFilter.ALL,
    val sortOption: FinanceSortOption = FinanceSortOption.DATE_DESC,
    val searchQuery: String = "",
    val isAddDialogOpen: Boolean = false,
    val recordToDelete: FinanceEntity? = null,
    val isSubmitting: Boolean = false,
    val bannerMessage: FinanceUiMessage? = null,
    val isBannerError: Boolean = false,
    val formErrorMessage: FinanceUiMessage? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ManualFinanceViewModel(
    private val financeRepository: FinanceRepository
) : ViewModel() {

    private val filterState = MutableStateFlow(FinanceFilterState())
    private val dialogState = MutableStateFlow(FinanceDialogState())

    private val periodRecordsFlow = filterState.flatMapLatest { filter ->
        val range = resolveDateRange(filter.period)
        financeRepository.observeBetween(range)
    }

    val uiState: StateFlow<ManualFinanceUiState> = combine(
        periodRecordsFlow,
        filterState,
        dialogState
    ) { rawRecords, filter, dialog ->
        val totalIncome = rawRecords
            .filter { it.type == FinanceType.INCOME }
            .sumOf { it.amount }
        val totalExpense = rawRecords
            .filter { it.type == FinanceType.EXPENSE }
            .sumOf { it.amount }

        val query = filter.searchQuery.trim().lowercase()
        val filtered = rawRecords.filter { item ->
            val matchesType = when (filter.typeFilter) {
                FinanceTypeFilter.ALL -> true
                FinanceTypeFilter.INCOME -> item.type == FinanceType.INCOME
                FinanceTypeFilter.EXPENSE -> item.type == FinanceType.EXPENSE
            }
            val matchesSearch = query.isEmpty() ||
                item.category.lowercase().contains(query) ||
                (item.description?.lowercase()?.contains(query) == true) ||
                item.date.lowercase().contains(query)
            matchesType && matchesSearch
        }

        val sorted = when (filter.sortOption) {
            FinanceSortOption.DATE_DESC -> filtered.sortedByDescending { it.date }
            FinanceSortOption.DATE_ASC -> filtered.sortedBy { it.date }
            FinanceSortOption.AMOUNT_DESC -> filtered.sortedByDescending { it.amount }
            FinanceSortOption.AMOUNT_ASC -> filtered.sortedBy { it.amount }
            FinanceSortOption.CATEGORY_ASC -> filtered.sortedBy { it.category.lowercase() }
        }

        ManualFinanceUiState(
            records = sorted,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            netAmount = totalIncome - totalExpense,
            period = filter.period,
            typeFilter = filter.typeFilter,
            sortOption = filter.sortOption,
            searchQuery = filter.searchQuery,
            isAddDialogOpen = dialog.isAddDialogOpen,
            recordToDelete = dialog.recordToDelete,
            isSubmitting = dialog.isSubmitting,
            bannerMessage = dialog.bannerMessage,
            isBannerError = dialog.isBannerError,
            formErrorMessage = dialog.formErrorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ManualFinanceUiState()
    )

    fun setPeriod(period: FinancePeriodFilter) {
        AppSession.touch()
        filterState.update { it.copy(period = period) }
    }

    fun setTypeFilter(typeFilter: FinanceTypeFilter) {
        AppSession.touch()
        filterState.update { it.copy(typeFilter = typeFilter) }
    }

    fun setSortOption(sortOption: FinanceSortOption) {
        AppSession.touch()
        filterState.update { it.copy(sortOption = sortOption) }
    }

    fun setSearchQuery(query: String) {
        AppSession.touch()
        filterState.update { it.copy(searchQuery = query) }
    }

    fun openAddDialog() {
        AppSession.touch()
        dialogState.update {
            it.copy(
                isAddDialogOpen = true,
                bannerMessage = null,
                formErrorMessage = null
            )
        }
    }

    fun dismissAddDialog() {
        dialogState.update {
            it.copy(
                isAddDialogOpen = false,
                formErrorMessage = null
            )
        }
    }

    fun clearFormError() {
        if (dialogState.value.formErrorMessage != null) {
            dialogState.update { it.copy(formErrorMessage = null) }
        }
    }

    fun requestDelete(record: FinanceEntity) {
        AppSession.touch()
        dialogState.update { it.copy(recordToDelete = record) }
    }

    fun dismissDeleteDialog() {
        dialogState.update { it.copy(recordToDelete = null) }
    }

    fun dismissBanner() {
        dialogState.update { it.copy(bannerMessage = null) }
    }

    fun addRecord(
        type: String,
        category: String,
        amountText: String,
        description: String,
        customDateInput: String?,
        onSuccessResetForm: () -> Unit = {}
    ) {
        AppSession.touch()
        val sessionUser = AppSession.currentUser.value
        if (sessionUser == null) {
            dialogState.update {
                it.copy(formErrorMessage = FinanceUiMessage.Resource(R.string.finance_err_no_session))
            }
            return
        }

        val trimmedCategory = category.trim()
        if (trimmedCategory.isEmpty()) {
            dialogState.update {
                it.copy(formErrorMessage = FinanceUiMessage.Resource(R.string.finance_err_category_required))
            }
            return
        }

        val amount = amountText.digitsToLongOrNull()
        if (amount == null || amount <= 0L) {
            dialogState.update {
                it.copy(formErrorMessage = FinanceUiMessage.Resource(R.string.finance_err_invalid_amount))
            }
            return
        }

        val timestamp = resolveTimestamp(customDateInput)
        if (timestamp == null) {
            dialogState.update {
                it.copy(formErrorMessage = FinanceUiMessage.Resource(R.string.finance_err_invalid_date))
            }
            return
        }

        viewModelScope.launch {
            dialogState.update {
                it.copy(
                    isSubmitting = true,
                    bannerMessage = null,
                    formErrorMessage = null
                )
            }
            val result = financeRepository.add(
                userId = sessionUser.userId,
                date = timestamp,
                type = type,
                category = trimmedCategory,
                amount = amount,
                description = description.trim().ifEmpty { null }
            )
            result.fold(
                onSuccess = {
                    onSuccessResetForm()
                    dialogState.update {
                        it.copy(
                            isSubmitting = false,
                            isAddDialogOpen = false,
                            formErrorMessage = null,
                            bannerMessage = FinanceUiMessage.Resource(R.string.finance_msg_added_success),
                            isBannerError = false
                        )
                    }
                },
                onFailure = { err ->
                    val msg = err.message?.takeIf { it.isNotBlank() }
                        ?.let { FinanceUiMessage.Plain(it) }
                        ?: FinanceUiMessage.Resource(R.string.finance_err_generic)
                    dialogState.update {
                        it.copy(
                            isSubmitting = false,
                            formErrorMessage = msg
                        )
                    }
                }
            )
        }
    }

    fun confirmDelete() {
        AppSession.touch()
        val target = dialogState.value.recordToDelete ?: return
        viewModelScope.launch {
            dialogState.update { it.copy(isSubmitting = true, bannerMessage = null) }
            val result = financeRepository.delete(target.financeId)
            result.fold(
                onSuccess = {
                    dialogState.update {
                        it.copy(
                            isSubmitting = false,
                            recordToDelete = null,
                            bannerMessage = FinanceUiMessage.Resource(R.string.finance_msg_deleted_success),
                            isBannerError = false
                        )
                    }
                },
                onFailure = { err ->
                    val msg = err.message?.takeIf { it.isNotBlank() }
                        ?.let { FinanceUiMessage.Plain(it) }
                        ?: FinanceUiMessage.Resource(R.string.finance_err_generic)
                    dialogState.update {
                        it.copy(
                            isSubmitting = false,
                            recordToDelete = null,
                            bannerMessage = msg,
                            isBannerError = true
                        )
                    }
                }
            )
        }
    }

    private fun resolveDateRange(period: FinancePeriodFilter): WibClock.DateRange {
        val today = WibClock.today()
        return when (period) {
            FinancePeriodFilter.TODAY -> WibClock.dayRange(today)
            FinancePeriodFilter.THIS_WEEK -> WibClock.weekRange(today)
            FinancePeriodFilter.THIS_MONTH -> WibClock.monthRange(today)
            FinancePeriodFilter.ALL_TIME -> WibClock.DateRange(
                start = "2000-01-01 00:00:00",
                endExclusive = "2100-01-01 00:00:00"
            )
        }
    }

    private fun resolveTimestamp(customDateInput: String?): String? {
        val cleaned = customDateInput?.trim().orEmpty()
        if (cleaned.isEmpty()) {
            return WibClock.nowString()
        }
        return try {
            val parsedDate = LocalDate.parse(cleaned)
            val nowTimePart = WibClock.nowString().substringAfter(' ', "12:00:00")
            "$parsedDate $nowTimePart"
        } catch (_: Exception) {
            null
        }
    }
}