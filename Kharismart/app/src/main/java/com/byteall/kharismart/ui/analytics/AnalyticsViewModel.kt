package com.byteall.kharismart.ui.analytics

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.byteall.kharismart.R
import com.byteall.kharismart.data.local.entity.SettingsEntity
import com.byteall.kharismart.data.local.model.CategorySales
import com.byteall.kharismart.data.local.model.ProductSales
import com.byteall.kharismart.data.repository.AnalyticsRepository
import com.byteall.kharismart.data.repository.FinancialSummary
import com.byteall.kharismart.data.repository.SettingsRepository
import com.byteall.kharismart.util.export.CsvReportExporter
import com.byteall.kharismart.util.export.CsvTargetProgressSnapshot
import com.byteall.kharismart.util.time.WibClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class AnalyticsPeriodPreset {
    TODAY,
    THIS_WEEK,
    THIS_MONTH,
    CUSTOM
}

enum class ProductSortField {
    UNITS,
    REVENUE,
    PROFIT
}

sealed interface AnalyticsUiMessage {
    data class Resource(
        @StringRes val resId: Int,
        val isError: Boolean = false
    ) : AnalyticsUiMessage

    data class Plain(
        val text: String,
        val isError: Boolean = false
    ) : AnalyticsUiMessage
}

data class TargetRingUiModel(
    val actualRevenue: Long = 0L,
    val targetRevenue: Long? = null,
    val progressPercent: Int = 0
)

data class AnalyticsUiState(
    val selectedPreset: AnalyticsPeriodPreset = AnalyticsPeriodPreset.TODAY,
    val activeRange: WibClock.DateRange = WibClock.dayRange(WibClock.today()),
    val startDateIso: String = WibClock.todayString(),
    val endDateIso: String = WibClock.todayString(),
    val storeName: String = "Toko Kharisma",
    val summary: FinancialSummary = emptySummary(WibClock.dayRange(WibClock.today())),
    val dailyRing: TargetRingUiModel = TargetRingUiModel(),
    val weeklyRing: TargetRingUiModel = TargetRingUiModel(),
    val monthlyRing: TargetRingUiModel = TargetRingUiModel(),
    val categorySales: List<CategorySales> = emptyList(),
    val bestSellers: List<ProductSales> = emptyList(),
    val sortField: ProductSortField = ProductSortField.REVENUE,
    val sortDescending: Boolean = true,
    val showCustomDateDialog: Boolean = false,
    val customStartInput: String = WibClock.todayString(),
    val customEndInput: String = WibClock.todayString(),
    val customDateError: AnalyticsUiMessage? = null,
    val bannerMessage: AnalyticsUiMessage? = null
)

private data class PeriodSelectionState(
    val preset: AnalyticsPeriodPreset = AnalyticsPeriodPreset.TODAY,
    val customFrom: LocalDate = WibClock.today(),
    val customTo: LocalDate = WibClock.today()
) {
    fun resolveRange(): WibClock.DateRange {
        val today = WibClock.today()
        return when (preset) {
            AnalyticsPeriodPreset.TODAY -> WibClock.dayRange(today)
            AnalyticsPeriodPreset.THIS_WEEK -> WibClock.weekRange(today)
            AnalyticsPeriodPreset.THIS_MONTH -> WibClock.monthRange(today)
            AnalyticsPeriodPreset.CUSTOM -> WibClock.customRange(customFrom, customTo)
        }
    }

    fun startIso(): String {
        val range = resolveRange()
        return range.start.substringBefore(' ')
    }

    fun endInclusiveIso(): String {
        return when (preset) {
            AnalyticsPeriodPreset.TODAY -> WibClock.todayString()
            AnalyticsPeriodPreset.THIS_WEEK -> {
                val range = resolveRange()
                LocalDate.parse(range.endExclusive.substringBefore(' ')).minusDays(1).toString()
            }
            AnalyticsPeriodPreset.THIS_MONTH -> {
                val range = resolveRange()
                LocalDate.parse(range.endExclusive.substringBefore(' ')).minusDays(1).toString()
            }
            AnalyticsPeriodPreset.CUSTOM -> customTo.toString()
        }
    }
}

private data class SortAndDialogState(
    val sortField: ProductSortField = ProductSortField.REVENUE,
    val sortDescending: Boolean = true,
    val showCustomDateDialog: Boolean = false,
    val customStartInput: String = WibClock.todayString(),
    val customEndInput: String = WibClock.todayString(),
    val customDateError: AnalyticsUiMessage? = null,
    val bannerMessage: AnalyticsUiMessage? = null
)

private data class ActivePeriodData(
    val selection: PeriodSelectionState,
    val summary: FinancialSummary,
    val categories: List<CategorySales>,
    val products: List<ProductSales>
)

private data class TargetRingsData(
    val settings: SettingsEntity,
    val dailySummary: FinancialSummary,
    val weeklySummary: FinancialSummary,
    val monthlySummary: FinancialSummary
)

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(
    private val analyticsRepository: AnalyticsRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val isoDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private val periodSelection = MutableStateFlow(PeriodSelectionState())
    private val sortAndDialog = MutableStateFlow(SortAndDialogState())

    private val activePeriodFlow = periodSelection.flatMapLatest { selection ->
        val range = selection.resolveRange()
        combine(
            analyticsRepository.observeSummary(range),
            analyticsRepository.observeCategorySales(range),
            analyticsRepository.observeProductSales(range)
        ) { summary, categories, products ->
            ActivePeriodData(
                selection = selection,
                summary = summary,
                categories = categories,
                products = products
            )
        }
    }

    private val targetRingsFlow = combine(
        settingsRepository.observeSettings(),
        analyticsRepository.observeSummary(WibClock.dayRange(WibClock.today())),
        analyticsRepository.observeSummary(WibClock.weekRange(WibClock.today())),
        analyticsRepository.observeSummary(WibClock.monthRange(WibClock.today()))
    ) { settings, dailySummary, weeklySummary, monthlySummary ->
        TargetRingsData(
            settings = settings,
            dailySummary = dailySummary,
            weeklySummary = weeklySummary,
            monthlySummary = monthlySummary
        )
    }

    val uiState: StateFlow<AnalyticsUiState> = combine(
        activePeriodFlow,
        targetRingsFlow,
        sortAndDialog
    ) { activeData, ringsData, uiControls ->
        val sortedProducts = sortProducts(
            products = activeData.products,
            field = uiControls.sortField,
            descending = uiControls.sortDescending
        )

        val settings = ringsData.settings
        val dailyActual = ringsData.dailySummary.revenue
        val weeklyActual = ringsData.weeklySummary.revenue
        val monthlyActual = ringsData.monthlySummary.revenue

        AnalyticsUiState(
            selectedPreset = activeData.selection.preset,
            activeRange = activeData.selection.resolveRange(),
            startDateIso = activeData.selection.startIso(),
            endDateIso = activeData.selection.endInclusiveIso(),
            storeName = settings.storeName,
            summary = activeData.summary,
            dailyRing = TargetRingUiModel(
                actualRevenue = dailyActual,
                targetRevenue = settings.targetDaily,
                progressPercent = AnalyticsRepository.targetProgressPercent(
                    periodRevenue = dailyActual,
                    target = settings.targetDaily
                )
            ),
            weeklyRing = TargetRingUiModel(
                actualRevenue = weeklyActual,
                targetRevenue = settings.targetWeekly,
                progressPercent = AnalyticsRepository.targetProgressPercent(
                    periodRevenue = weeklyActual,
                    target = settings.targetWeekly
                )
            ),
            monthlyRing = TargetRingUiModel(
                actualRevenue = monthlyActual,
                targetRevenue = settings.targetMonthly,
                progressPercent = AnalyticsRepository.targetProgressPercent(
                    periodRevenue = monthlyActual,
                    target = settings.targetMonthly
                )
            ),
            categorySales = activeData.categories,
            bestSellers = sortedProducts,
            sortField = uiControls.sortField,
            sortDescending = uiControls.sortDescending,
            showCustomDateDialog = uiControls.showCustomDateDialog,
            customStartInput = uiControls.customStartInput,
            customEndInput = uiControls.customEndInput,
            customDateError = uiControls.customDateError,
            bannerMessage = uiControls.bannerMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AnalyticsUiState()
    )

    fun selectPreset(preset: AnalyticsPeriodPreset) {
        if (preset == AnalyticsPeriodPreset.CUSTOM) {
            openCustomDateDialog()
        } else {
            periodSelection.update { current ->
                current.copy(preset = preset)
            }
        }
    }

    fun openCustomDateDialog() {
        val current = periodSelection.value
        sortAndDialog.update { state ->
            state.copy(
                showCustomDateDialog = true,
                customStartInput = current.startIso(),
                customEndInput = current.endInclusiveIso(),
                customDateError = null
            )
        }
    }

    fun dismissCustomDateDialog() {
        sortAndDialog.update {
            it.copy(
                showCustomDateDialog = false,
                customDateError = null
            )
        }
    }

    fun updateCustomStartInput(value: String) {
        sortAndDialog.update {
            it.copy(
                customStartInput = value,
                customDateError = null
            )
        }
    }

    fun updateCustomEndInput(value: String) {
        sortAndDialog.update {
            it.copy(
                customEndInput = value,
                customDateError = null
            )
        }
    }

    fun applyCustomDateRange() {
        val controls = sortAndDialog.value
        val parsedStart = runCatching {
            LocalDate.parse(controls.customStartInput.trim(), isoDateFormatter)
        }.getOrNull()
        val parsedEnd = runCatching {
            LocalDate.parse(controls.customEndInput.trim(), isoDateFormatter)
        }.getOrNull()

        if (parsedStart == null || parsedEnd == null) {
            sortAndDialog.update {
                it.copy(
                    customDateError = AnalyticsUiMessage.Resource(
                        resId = R.string.analytics_err_invalid_date_format,
                        isError = true
                    )
                )
            }
            return
        }

        if (parsedEnd.isBefore(parsedStart)) {
            sortAndDialog.update {
                it.copy(
                    customDateError = AnalyticsUiMessage.Resource(
                        resId = R.string.analytics_err_end_before_start,
                        isError = true
                    )
                )
            }
            return
        }

        periodSelection.update {
            PeriodSelectionState(
                preset = AnalyticsPeriodPreset.CUSTOM,
                customFrom = parsedStart,
                customTo = parsedEnd
            )
        }
        sortAndDialog.update {
            it.copy(
                showCustomDateDialog = false,
                customDateError = null,
                bannerMessage = null
            )
        }
    }

    fun onSortHeaderClicked(field: ProductSortField) {
        sortAndDialog.update { current ->
            if (current.sortField == field) {
                current.copy(sortDescending = !current.sortDescending)
            } else {
                current.copy(sortField = field, sortDescending = true)
            }
        }
    }

    fun dismissBanner() {
        sortAndDialog.update { it.copy(bannerMessage = null) }
    }

    fun notifyCsvSaved() {
        sortAndDialog.update {
            it.copy(
                bannerMessage = AnalyticsUiMessage.Resource(
                    resId = R.string.analytics_msg_csv_saved,
                    isError = false
                )
            )
        }
    }

    fun notifyCsvError(message: String? = null) {
        sortAndDialog.update {
            val uiMsg = if (!message.isNullOrBlank()) {
                AnalyticsUiMessage.Plain(text = message, isError = true)
            } else {
                AnalyticsUiMessage.Resource(resId = R.string.analytics_err_csv_export, isError = true)
            }
            it.copy(bannerMessage = uiMsg)
        }
    }

    fun buildCsvContent(periodLabel: String): String {
        val state = uiState.value
        return CsvReportExporter.buildCsvReport(
            storeName = state.storeName,
            periodLabel = periodLabel,
            startDateDisplay = state.startDateIso,
            endDateDisplay = state.endDateIso,
            exportedAtWib = WibClock.nowString(),
            summary = state.summary,
            targets = CsvTargetProgressSnapshot(
                dailyRevenue = state.dailyRing.actualRevenue,
                dailyTarget = state.dailyRing.targetRevenue,
                weeklyRevenue = state.weeklyRing.actualRevenue,
                weeklyTarget = state.weeklyRing.targetRevenue,
                monthlyRevenue = state.monthlyRing.actualRevenue,
                monthlyTarget = state.monthlyRing.targetRevenue
            ),
            categories = state.categorySales,
            products = state.bestSellers
        )
    }

    private fun sortProducts(
        products: List<ProductSales>,
        field: ProductSortField,
        descending: Boolean
    ): List<ProductSales> {
        val comparator = when (field) {
            ProductSortField.UNITS -> compareBy<ProductSales> { it.units }.thenBy { it.revenue }
            ProductSortField.REVENUE -> compareBy<ProductSales> { it.revenue }.thenBy { it.units }
            ProductSortField.PROFIT -> compareBy<ProductSales> { it.grossProfit }.thenBy { it.revenue }
        }
        return if (descending) {
            products.sortedWith(comparator.reversed())
        } else {
            products.sortedWith(comparator)
        }
    }
}

private fun emptySummary(range: WibClock.DateRange): FinancialSummary = FinancialSummary(
    range = range,
    revenue = 0L,
    grossProfit = 0L,
    cashSales = 0L,
    qrisSales = 0L,
    creditSales = 0L,
    cashDebtPayments = 0L,
    qrisDebtPayments = 0L,
    cashReceived = 0L,
    qrisReceived = 0L,
    otherIncome = 0L,
    operatingExpenses = 0L,
    restockPurchases = 0L,
    netProfit = 0L,
    cashBalance = 0L,
    outstandingDebt = 0L,
    transactionCount = 0
)