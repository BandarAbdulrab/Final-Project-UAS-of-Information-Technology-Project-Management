package com.byteall.kharismart.ui.debt

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byteall.kharismart.R
import com.byteall.kharismart.data.local.DebtStatus
import com.byteall.kharismart.data.local.entity.DebtPaymentEntity
import com.byteall.kharismart.data.local.model.DebtWithOrigin
import com.byteall.kharismart.data.repository.DebtRepository
import com.byteall.kharismart.data.repository.ReceiptData
import com.byteall.kharismart.data.repository.SaleRepository
import com.byteall.kharismart.session.AppSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DebtFilter {
    ALL,
    UNPAID,
    PAID
}

sealed interface DebtUiMessage {
    data class Resource(@StringRes val resId: Int, val args: List<Any> = emptyList()) : DebtUiMessage
    data class Plain(val text: String) : DebtUiMessage
}

data class DebtUiState(
    val allDebts: List<DebtWithOrigin> = emptyList(),
    val filteredDebts: List<DebtWithOrigin> = emptyList(),
    val totalOutstanding: Long = 0L,
    val unpaidCount: Int = 0,
    val paidCount: Int = 0,
    val searchQuery: String = "",
    val selectedFilter: DebtFilter = DebtFilter.ALL,
    val customerSuggestions: List<String> = emptyList(),
    val selectedDebtForPayment: DebtWithOrigin? = null,
    val isAddManualDebtDialogOpen: Boolean = false,
    val debtToDelete: DebtWithOrigin? = null,
    val selectedDebtForHistory: DebtWithOrigin? = null,
    val paymentHistory: List<DebtPaymentEntity> = emptyList(),
    val receiptPreview: ReceiptData? = null,
    val isSubmitting: Boolean = false,
    val dialogErrorMessage: DebtUiMessage? = null,
    val bannerMessage: DebtUiMessage? = null,
    val isBannerError: Boolean = false
)

private data class DebtDialogAndBannerState(
    val selectedDebtForPayment: DebtWithOrigin? = null,
    val isAddManualDebtDialogOpen: Boolean = false,
    val debtToDelete: DebtWithOrigin? = null,
    val selectedDebtForHistory: DebtWithOrigin? = null,
    val paymentHistory: List<DebtPaymentEntity> = emptyList(),
    val receiptPreview: ReceiptData? = null,
    val isSubmitting: Boolean = false,
    val dialogErrorMessage: DebtUiMessage? = null,
    val bannerMessage: DebtUiMessage? = null,
    val isBannerError: Boolean = false
)

class DebtViewModel(
    private val debtRepository: DebtRepository,
    private val saleRepository: SaleRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow(DebtFilter.ALL)
    private val overlayState = MutableStateFlow(DebtDialogAndBannerState())
    private var historyObservationJob: Job? = null

    private val filterInputs = combine(searchQuery, selectedFilter) { query, filter ->
        query to filter
    }

    private val repositoryInputs = combine(
        debtRepository.observeAll(),
        debtRepository.observeOutstandingTotal(),
        debtRepository.observeCustomerNames()
    ) { debts, outstanding, customerNames ->
        Triple(debts, outstanding, customerNames)
    }

    val uiState: StateFlow<DebtUiState> = combine(
        repositoryInputs,
        filterInputs,
        overlayState
    ) { (allDebts, totalOutstanding, customerNames), (query, filter), overlay ->
        val trimmedQuery = query.trim()
        val filtered = allDebts.filter { item ->
            val matchesFilter = when (filter) {
                DebtFilter.ALL -> true
                DebtFilter.UNPAID -> item.debt.status == DebtStatus.UNPAID
                DebtFilter.PAID -> item.debt.status == DebtStatus.PAID
            }
            val matchesQuery = if (trimmedQuery.isEmpty()) {
                true
            } else {
                item.debt.customerName.contains(trimmedQuery, ignoreCase = true) ||
                    (item.receiptNo?.contains(trimmedQuery, ignoreCase = true) == true) ||
                    (item.debt.note?.contains(trimmedQuery, ignoreCase = true) == true)
            }
            matchesFilter && matchesQuery
        }

        DebtUiState(
            allDebts = allDebts,
            filteredDebts = filtered,
            totalOutstanding = totalOutstanding,
            unpaidCount = allDebts.count { it.debt.status == DebtStatus.UNPAID },
            paidCount = allDebts.count { it.debt.status == DebtStatus.PAID },
            searchQuery = query,
            selectedFilter = filter,
            customerSuggestions = customerNames,
            selectedDebtForPayment = overlay.selectedDebtForPayment,
            isAddManualDebtDialogOpen = overlay.isAddManualDebtDialogOpen,
            debtToDelete = overlay.debtToDelete,
            selectedDebtForHistory = overlay.selectedDebtForHistory,
            paymentHistory = overlay.paymentHistory,
            receiptPreview = overlay.receiptPreview,
            isSubmitting = overlay.isSubmitting,
            dialogErrorMessage = overlay.dialogErrorMessage,
            bannerMessage = overlay.bannerMessage,
            isBannerError = overlay.isBannerError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DebtUiState()
    )

    fun onSearchQueryChange(query: String) {
        AppSession.touch()
        searchQuery.value = query
    }

    fun onFilterSelected(filter: DebtFilter) {
        AppSession.touch()
        selectedFilter.value = filter
    }

    fun openPaymentDialog(item: DebtWithOrigin) {
        AppSession.touch()
        overlayState.update {
            it.copy(
                selectedDebtForPayment = item,
                dialogErrorMessage = null
            )
        }
    }

    fun dismissPaymentDialog() {
        AppSession.touch()
        overlayState.update {
            it.copy(
                selectedDebtForPayment = null,
                isSubmitting = false,
                dialogErrorMessage = null
            )
        }
    }

    fun recordPayment(debtId: Long, amount: Long, method: String, note: String?) {
        AppSession.touch()
        val userId = AppSession.currentUser.value?.userId
        if (userId == null) {
            overlayState.update {
                it.copy(dialogErrorMessage = DebtUiMessage.Resource(R.string.debt_err_no_session))
            }
            return
        }

        viewModelScope.launch {
            overlayState.update { it.copy(isSubmitting = true, dialogErrorMessage = null) }
            val result = debtRepository.recordPayment(
                userId = userId,
                debtId = debtId,
                amount = amount,
                method = method,
                note = note?.trim()?.takeIf { it.isNotEmpty() }
            )
            result.fold(
                onSuccess = {
                    overlayState.update {
                        it.copy(
                            selectedDebtForPayment = null,
                            isSubmitting = false,
                            dialogErrorMessage = null,
                            bannerMessage = DebtUiMessage.Resource(R.string.debt_msg_payment_success),
                            isBannerError = false
                        )
                    }
                },
                onFailure = { error ->
                    overlayState.update {
                        it.copy(
                            isSubmitting = false,
                            dialogErrorMessage = error.toDebtUiMessage()
                        )
                    }
                }
            )
        }
    }

    fun openAddManualDebtDialog() {
        AppSession.touch()
        overlayState.update {
            it.copy(
                isAddManualDebtDialogOpen = true,
                dialogErrorMessage = null
            )
        }
    }

    fun dismissAddManualDebtDialog() {
        AppSession.touch()
        overlayState.update {
            it.copy(
                isAddManualDebtDialogOpen = false,
                isSubmitting = false,
                dialogErrorMessage = null
            )
        }
    }

    fun addManualDebt(customerName: String, amount: Long, note: String?) {
        AppSession.touch()
        val userId = AppSession.currentUser.value?.userId
        if (userId == null) {
            overlayState.update {
                it.copy(dialogErrorMessage = DebtUiMessage.Resource(R.string.debt_err_no_session))
            }
            return
        }

        viewModelScope.launch {
            overlayState.update { it.copy(isSubmitting = true, dialogErrorMessage = null) }
            val result = debtRepository.addManualDebt(
                userId = userId,
                customerName = customerName.trim(),
                amount = amount,
                note = note?.trim()?.takeIf { it.isNotEmpty() }
            )
            result.fold(
                onSuccess = {
                    overlayState.update {
                        it.copy(
                            isAddManualDebtDialogOpen = false,
                            isSubmitting = false,
                            dialogErrorMessage = null,
                            bannerMessage = DebtUiMessage.Resource(R.string.debt_msg_added_success),
                            isBannerError = false
                        )
                    }
                },
                onFailure = { error ->
                    overlayState.update {
                        it.copy(
                            isSubmitting = false,
                            dialogErrorMessage = error.toDebtUiMessage()
                        )
                    }
                }
            )
        }
    }

    fun requestDeleteDebt(item: DebtWithOrigin) {
        AppSession.touch()
        overlayState.update { it.copy(debtToDelete = item) }
    }

    fun dismissDeleteDebtDialog() {
        AppSession.touch()
        overlayState.update { it.copy(debtToDelete = null) }
    }

    fun confirmDeleteDebt() {
        AppSession.touch()
        val target = overlayState.value.debtToDelete ?: return
        viewModelScope.launch {
            overlayState.update { it.copy(isSubmitting = true) }
            val result = debtRepository.deleteManualDebt(target.debt.debtId)
            result.fold(
                onSuccess = {
                    overlayState.update {
                        it.copy(
                            debtToDelete = null,
                            isSubmitting = false,
                            bannerMessage = DebtUiMessage.Resource(R.string.debt_msg_deleted_success),
                            isBannerError = false
                        )
                    }
                },
                onFailure = { error ->
                    overlayState.update {
                        it.copy(
                            debtToDelete = null,
                            isSubmitting = false,
                            bannerMessage = error.toDebtUiMessage(),
                            isBannerError = true
                        )
                    }
                }
            )
        }
    }

    fun openPaymentHistory(item: DebtWithOrigin) {
        AppSession.touch()
        historyObservationJob?.cancel()
        overlayState.update {
            it.copy(
                selectedDebtForHistory = item,
                paymentHistory = emptyList()
            )
        }
        historyObservationJob = viewModelScope.launch {
            debtRepository.observePayments(item.debt.debtId).collect { payments ->
                overlayState.update { it.copy(paymentHistory = payments) }
            }
        }
    }

    fun dismissPaymentHistory() {
        AppSession.touch()
        historyObservationJob?.cancel()
        historyObservationJob = null
        overlayState.update {
            it.copy(
                selectedDebtForHistory = null,
                paymentHistory = emptyList()
            )
        }
    }

    fun openReceiptPreview(transactionId: Long) {
        AppSession.touch()
        viewModelScope.launch {
            val receipt = saleRepository.getReceiptData(transactionId)
            if (receipt != null) {
                overlayState.update { it.copy(receiptPreview = receipt) }
            } else {
                overlayState.update {
                    it.copy(
                        bannerMessage = DebtUiMessage.Resource(R.string.debt_err_receipt_not_found),
                        isBannerError = true
                    )
                }
            }
        }
    }

    fun dismissReceiptPreview() {
        AppSession.touch()
        overlayState.update { it.copy(receiptPreview = null) }
    }

    fun dismissBanner() {
        overlayState.update { it.copy(bannerMessage = null) }
    }

    private fun Throwable.toDebtUiMessage(): DebtUiMessage {
        val text = message?.trim()
        return if (!text.isNullOrEmpty()) {
            DebtUiMessage.Plain(text)
        } else {
            DebtUiMessage.Resource(R.string.debt_err_generic)
        }
    }
}