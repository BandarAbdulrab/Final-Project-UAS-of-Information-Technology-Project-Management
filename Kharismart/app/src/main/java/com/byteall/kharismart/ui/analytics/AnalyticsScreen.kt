package com.byteall.kharismart.ui.analytics

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byteall.kharismart.R
import com.byteall.kharismart.data.local.model.CategorySales
import com.byteall.kharismart.data.local.model.ProductSales
import com.byteall.kharismart.data.repository.FinancialSummary
import com.byteall.kharismart.di.appViewModel
import com.byteall.kharismart.session.AppSession
import com.byteall.kharismart.util.export.CsvReportExporter
import com.byteall.kharismart.util.format.toRupiah

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = appViewModel { container ->
        AnalyticsViewModel(
            analyticsRepository = container.analyticsRepository,
            settingsRepository = container.settingsRepository
        )
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val periodLabel = when (uiState.selectedPreset) {
        AnalyticsPeriodPreset.TODAY -> stringResource(R.string.analytics_period_today)
        AnalyticsPeriodPreset.THIS_WEEK -> stringResource(R.string.analytics_period_week)
        AnalyticsPeriodPreset.THIS_MONTH -> stringResource(R.string.analytics_period_month)
        AnalyticsPeriodPreset.CUSTOM -> stringResource(R.string.analytics_period_custom)
    }
    val chooserTitle = stringResource(R.string.analytics_csv_chooser_title)

    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val csvContent = viewModel.buildCsvContent(periodLabel)
            CsvReportExporter.writeCsvToUri(
                context = context,
                destinationUri = uri,
                csvContent = csvContent
            ).onSuccess {
                viewModel.notifyCsvSaved()
            }.onFailure { err ->
                viewModel.notifyCsvError(err.message)
            }
        }
    }

    AnalyticsContent(
        uiState = uiState,
        onSelectPreset = { preset ->
            AppSession.touch()
            viewModel.selectPreset(preset)
        },
        onShareCsvClick = {
            AppSession.touch()
            val csvContent = viewModel.buildCsvContent(periodLabel)
            val fileName = CsvReportExporter.suggestedFileName(
                startDateDisplay = uiState.startDateIso,
                endDateDisplay = uiState.endDateIso
            )
            CsvReportExporter.shareCsvReport(
                context = context,
                csvContent = csvContent,
                fileName = fileName,
                chooserTitle = chooserTitle
            ).onFailure { err ->
                viewModel.notifyCsvError(err.message)
            }
        },
        onSaveCsvClick = {
            AppSession.touch()
            val fileName = CsvReportExporter.suggestedFileName(
                startDateDisplay = uiState.startDateIso,
                endDateDisplay = uiState.endDateIso
            )
            saveCsvLauncher.launch(fileName)
        },
        onSortHeaderClick = { field ->
            AppSession.touch()
            viewModel.onSortHeaderClicked(field)
        },
        onDismissBanner = viewModel::dismissBanner
    )

    if (uiState.showCustomDateDialog) {
        CustomDateRangeDialog(
            startInput = uiState.customStartInput,
            endInput = uiState.customEndInput,
            errorMessage = uiState.customDateError,
            onStartChange = viewModel::updateCustomStartInput,
            onEndChange = viewModel::updateCustomEndInput,
            onConfirm = {
                AppSession.touch()
                viewModel.applyCustomDateRange()
            },
            onDismiss = viewModel::dismissCustomDateDialog
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalyticsContent(
    uiState: AnalyticsUiState,
    onSelectPreset: (AnalyticsPeriodPreset) -> Unit,
    onShareCsvClick: () -> Unit,
    onSaveCsvClick: () -> Unit,
    onSortHeaderClick: (ProductSortField) -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header & Export Actions
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = stringResource(R.string.analytics_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.analytics_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onSaveCsvClick) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.analytics_btn_save_csv))
                }
                Button(onClick = onShareCsvClick) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.analytics_btn_share_csv))
                }
            }
        }

        // Feedback Banner
        uiState.bannerMessage?.let { message ->
            AnalyticsFeedbackBanner(
                message = message,
                onDismiss = onDismissBanner
            )
        }

        // Period Filter Chips & Active Date Indicator
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = uiState.selectedPreset == AnalyticsPeriodPreset.TODAY,
                        onClick = { onSelectPreset(AnalyticsPeriodPreset.TODAY) },
                        label = { Text(stringResource(R.string.analytics_period_today)) }
                    )
                    FilterChip(
                        selected = uiState.selectedPreset == AnalyticsPeriodPreset.THIS_WEEK,
                        onClick = { onSelectPreset(AnalyticsPeriodPreset.THIS_WEEK) },
                        label = { Text(stringResource(R.string.analytics_period_week)) }
                    )
                    FilterChip(
                        selected = uiState.selectedPreset == AnalyticsPeriodPreset.THIS_MONTH,
                        onClick = { onSelectPreset(AnalyticsPeriodPreset.THIS_MONTH) },
                        label = { Text(stringResource(R.string.analytics_period_month)) }
                    )
                    FilterChip(
                        selected = uiState.selectedPreset == AnalyticsPeriodPreset.CUSTOM,
                        onClick = { onSelectPreset(AnalyticsPeriodPreset.CUSTOM) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = { Text(stringResource(R.string.analytics_period_custom)) }
                    )
                }

                Text(
                    text = stringResource(
                        R.string.analytics_active_range_label,
                        uiState.startDateIso,
                        uiState.endDateIso,
                        uiState.summary.transactionCount
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 1. Target Achievement Gauges (Daily, Weekly, Monthly Circular Progress Rings)
        TargetAchievementSection(
            dailyRing = uiState.dailyRing,
            weeklyRing = uiState.weeklyRing,
            monthlyRing = uiState.monthlyRing
        )

        // 2. KPI Summary Cards (BR-14)
        KpiSummarySection(summary = uiState.summary)

        // 3. Cash Balance Reconciliation Detail Card
        CashBalanceDetailCard(summary = uiState.summary)

        // 4. Category Performance Breakdown Cards
        CategoryPerformanceSection(categories = uiState.categorySales)

        // 5. Sortable Best Sellers Table
        BestSellersSection(
            products = uiState.bestSellers,
            sortField = uiState.sortField,
            sortDescending = uiState.sortDescending,
            onSortHeaderClick = onSortHeaderClick
        )
    }
}

@Composable
private fun AnalyticsFeedbackBanner(
    message: AnalyticsUiMessage,
    onDismiss: () -> Unit
) {
    val (text, isError) = when (message) {
        is AnalyticsUiMessage.Resource -> stringResource(message.resId) to message.isError
        is AnalyticsUiMessage.Plain -> message.text to message.isError
    }

    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                contentDescription = null
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_close)
                )
            }
        }
    }
}

@Composable
private fun TargetAchievementSection(
    dailyRing: TargetRingUiModel,
    weeklyRing: TargetRingUiModel,
    monthlyRing: TargetRingUiModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.analytics_section_targets),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isWide = maxWidth >= 600.dp
                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TargetProgressRingCard(
                            title = stringResource(R.string.analytics_target_daily_title),
                            ring = dailyRing,
                            ringColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        TargetProgressRingCard(
                            title = stringResource(R.string.analytics_target_weekly_title),
                            ring = weeklyRing,
                            ringColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        TargetProgressRingCard(
                            title = stringResource(R.string.analytics_target_monthly_title),
                            ring = monthlyRing,
                            ringColor = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TargetProgressRingCard(
                            title = stringResource(R.string.analytics_target_daily_title),
                            ring = dailyRing,
                            ringColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TargetProgressRingCard(
                            title = stringResource(R.string.analytics_target_weekly_title),
                            ring = weeklyRing,
                            ringColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TargetProgressRingCard(
                            title = stringResource(R.string.analytics_target_monthly_title),
                            ring = monthlyRing,
                            ringColor = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetProgressRingCard(
    title: String,
    ring: TargetRingUiModel,
    ringColor: Color,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val hasTarget = ring.targetRevenue != null && ring.targetRevenue > 0L

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(112.dp)
            ) {
                Canvas(modifier = Modifier.size(100.dp)) {
                    val strokeWidth = 11.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                    val arcSize = Size(diameter, diameter)

                    // Background Track
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Progress Arc
                    if (hasTarget && ring.progressPercent > 0) {
                        val sweep = (ring.progressPercent.coerceIn(0, 100) / 100f) * 360f
                        drawArc(
                            color = ringColor,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                Text(
                    text = if (hasTarget) {
                        stringResource(R.string.analytics_target_percent, ring.progressPercent)
                    } else {
                        "—"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (hasTarget) ringColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = ring.actualRevenue.toRupiah(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (hasTarget && ring.targetRevenue != null) {
                    stringResource(R.string.analytics_target_of, ring.targetRevenue.toRupiah())
                } else {
                    stringResource(R.string.analytics_target_not_set)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun KpiSummarySection(summary: FinancialSummary) {
    val positiveGreen = Color(0xFF1B5E20)
    val expenseRed = MaterialTheme.colorScheme.error
    val netProfitColor = if (summary.netProfit >= 0L) positiveGreen else expenseRed
    val cashBalanceColor = if (summary.cashBalance >= 0L) positiveGreen else expenseRed

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.analytics_section_kpi),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isWide = maxWidth >= 650.dp
            if (isWide) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        KpiMetricCard(
                            title = stringResource(R.string.analytics_kpi_revenue),
                            amount = summary.revenue.toRupiah(),
                            subtitle = stringResource(
                                R.string.analytics_kpi_revenue_sub,
                                summary.transactionCount
                            ),
                            valueColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            title = stringResource(R.string.analytics_kpi_gross_profit),
                            amount = summary.grossProfit.toRupiah(),
                            subtitle = stringResource(R.string.analytics_kpi_gross_profit_sub),
                            valueColor = positiveGreen,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            title = stringResource(R.string.analytics_kpi_expenses),
                            amount = summary.operatingExpenses.toRupiah(),
                            subtitle = stringResource(R.string.analytics_kpi_expenses_sub),
                            valueColor = expenseRed,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        KpiMetricCard(
                            title = stringResource(R.string.analytics_kpi_net_profit),
                            amount = summary.netProfit.toRupiah(),
                            subtitle = stringResource(R.string.analytics_kpi_net_profit_sub),
                            valueColor = netProfitColor,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            title = stringResource(R.string.analytics_kpi_cash_balance),
                            amount = summary.cashBalance.toRupiah(),
                            subtitle = stringResource(R.string.analytics_kpi_cash_balance_sub),
                            valueColor = cashBalanceColor,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            title = stringResource(R.string.analytics_kpi_outstanding_debt),
                            amount = summary.outstandingDebt.toRupiah(),
                            subtitle = stringResource(R.string.analytics_kpi_outstanding_debt_sub),
                            valueColor = if (summary.outstandingDebt > 0L) expenseRed else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiMetricCard(
                        title = stringResource(R.string.analytics_kpi_revenue),
                        amount = summary.revenue.toRupiah(),
                        subtitle = stringResource(
                            R.string.analytics_kpi_revenue_sub,
                            summary.transactionCount
                        ),
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    KpiMetricCard(
                        title = stringResource(R.string.analytics_kpi_gross_profit),
                        amount = summary.grossProfit.toRupiah(),
                        subtitle = stringResource(R.string.analytics_kpi_gross_profit_sub),
                        valueColor = positiveGreen,
                        modifier = Modifier.fillMaxWidth()
                    )
                    KpiMetricCard(
                        title = stringResource(R.string.analytics_kpi_expenses),
                        amount = summary.operatingExpenses.toRupiah(),
                        subtitle = stringResource(R.string.analytics_kpi_expenses_sub),
                        valueColor = expenseRed,
                        modifier = Modifier.fillMaxWidth()
                    )
                    KpiMetricCard(
                        title = stringResource(R.string.analytics_kpi_net_profit),
                        amount = summary.netProfit.toRupiah(),
                        subtitle = stringResource(R.string.analytics_kpi_net_profit_sub),
                        valueColor = netProfitColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                    KpiMetricCard(
                        title = stringResource(R.string.analytics_kpi_cash_balance),
                        amount = summary.cashBalance.toRupiah(),
                        subtitle = stringResource(R.string.analytics_kpi_cash_balance_sub),
                        valueColor = cashBalanceColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                    KpiMetricCard(
                        title = stringResource(R.string.analytics_kpi_outstanding_debt),
                        amount = summary.outstandingDebt.toRupiah(),
                        subtitle = stringResource(R.string.analytics_kpi_outstanding_debt_sub),
                        valueColor = if (summary.outstandingDebt > 0L) expenseRed else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun KpiMetricCard(
    title: String,
    amount: String,
    subtitle: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = amount,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CashBalanceDetailCard(summary: FinancialSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.analytics_section_cash_detail),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            DetailRow(
                label = stringResource(R.string.analytics_detail_cash_sales),
                value = summary.cashSales.toRupiah()
            )
            DetailRow(
                label = stringResource(R.string.analytics_detail_qris_sales),
                value = summary.qrisSales.toRupiah()
            )
            DetailRow(
                label = stringResource(R.string.analytics_detail_credit_sales),
                value = summary.creditSales.toRupiah(),
                valueColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
            DetailRow(
                label = stringResource(R.string.analytics_detail_cash_debt_paid),
                value = summary.cashDebtPayments.toRupiah()
            )
            DetailRow(
                label = stringResource(R.string.analytics_detail_qris_debt_paid),
                value = summary.qrisDebtPayments.toRupiah()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            DetailRow(
                label = stringResource(R.string.analytics_detail_total_cash_in),
                value = summary.cashReceived.toRupiah(),
                bold = true
            )
            DetailRow(
                label = stringResource(R.string.analytics_detail_total_qris_in),
                value = summary.qrisReceived.toRupiah(),
                bold = true
            )
            DetailRow(
                label = stringResource(R.string.analytics_detail_other_income),
                value = summary.otherIncome.toRupiah(),
                valueColor = Color(0xFF1B5E20)
            )
            DetailRow(
                label = stringResource(R.string.analytics_detail_operating_expenses),
                value = (-summary.operatingExpenses).toRupiah(),
                valueColor = MaterialTheme.colorScheme.error
            )
            DetailRow(
                label = stringResource(R.string.analytics_detail_restock_purchases),
                value = (-summary.restockPurchases).toRupiah(),
                valueColor = MaterialTheme.colorScheme.error
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            DetailRow(
                label = stringResource(R.string.analytics_detail_net_cash_balance),
                value = summary.cashBalance.toRupiah(),
                bold = true,
                valueColor = if (summary.cashBalance >= 0L) {
                    Color(0xFF1B5E20)
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    bold: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (bold) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = if (bold) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPerformanceSection(categories: List<CategorySales>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.analytics_section_categories),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (categories.isEmpty()) {
                Text(
                    text = stringResource(R.string.analytics_category_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    maxItemsInEachRow = 2
                ) {
                    categories.forEach { cat ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = cat.categoryName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(R.string.analytics_category_units, cat.units),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Text(
                                    text = cat.revenue.toRupiah(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = stringResource(
                                        R.string.analytics_category_profit,
                                        cat.grossProfit.toRupiah()
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BestSellersSection(
    products: List<ProductSales>,
    sortField: ProductSortField,
    sortDescending: Boolean,
    onSortHeaderClick: (ProductSortField) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.analytics_section_best_sellers),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Sort Chips for Quick Access on Touch Screens
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SortChipItem(
                    label = stringResource(R.string.analytics_sort_units),
                    selected = sortField == ProductSortField.UNITS,
                    descending = sortDescending,
                    onClick = { onSortHeaderClick(ProductSortField.UNITS) }
                )
                SortChipItem(
                    label = stringResource(R.string.analytics_sort_revenue),
                    selected = sortField == ProductSortField.REVENUE,
                    descending = sortDescending,
                    onClick = { onSortHeaderClick(ProductSortField.REVENUE) }
                )
                SortChipItem(
                    label = stringResource(R.string.analytics_sort_profit),
                    selected = sortField == ProductSortField.PROFIT,
                    descending = sortDescending,
                    onClick = { onSortHeaderClick(ProductSortField.PROFIT) }
                )
            }

            if (products.isEmpty()) {
                Text(
                    text = stringResource(R.string.analytics_best_sellers_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                // Clickable Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.analytics_col_rank),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(28.dp)
                    )
                    Text(
                        text = stringResource(R.string.analytics_col_product),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1.6f)
                    )
                    SortableHeaderCell(
                        title = stringResource(R.string.analytics_col_units),
                        active = sortField == ProductSortField.UNITS,
                        descending = sortDescending,
                        onClick = { onSortHeaderClick(ProductSortField.UNITS) },
                        modifier = Modifier.weight(0.8f),
                        alignEnd = true
                    )
                    SortableHeaderCell(
                        title = stringResource(R.string.analytics_col_revenue),
                        active = sortField == ProductSortField.REVENUE,
                        descending = sortDescending,
                        onClick = { onSortHeaderClick(ProductSortField.REVENUE) },
                        modifier = Modifier.weight(1.2f),
                        alignEnd = true
                    )
                    SortableHeaderCell(
                        title = stringResource(R.string.analytics_col_profit),
                        active = sortField == ProductSortField.PROFIT,
                        descending = sortDescending,
                        onClick = { onSortHeaderClick(ProductSortField.PROFIT) },
                        modifier = Modifier.weight(1.2f),
                        alignEnd = true
                    )
                }

                products.forEachIndexed { index, product ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(28.dp)
                        )
                        Text(
                            text = product.productName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1.6f)
                        )
                        Text(
                            text = "${product.units}",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(0.8f)
                        )
                        Text(
                            text = product.revenue.toRupiah(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.2f)
                        )
                        Text(
                            text = product.grossProfit.toRupiah(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF1B5E20),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.2f)
                        )
                    }
                    if (index < products.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortChipItem(
    label: String,
    selected: Boolean,
    descending: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        trailingIcon = if (selected) {
            {
                Icon(
                    imageVector = if (descending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            null
        }
    )
}

@Composable
private fun SortableHeaderCell(
    title: String,
    active: Boolean,
    descending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (active) {
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = if (descending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun CustomDateRangeDialog(
    startInput: String,
    endInput: String,
    errorMessage: AnalyticsUiMessage?,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val resolvedErrorText = when (errorMessage) {
        is AnalyticsUiMessage.Resource -> stringResource(errorMessage.resId)
        is AnalyticsUiMessage.Plain -> errorMessage.text
        null -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.analytics_custom_dialog_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = startInput,
                    onValueChange = onStartChange,
                    label = { Text(stringResource(R.string.analytics_custom_start_label)) },
                    isError = resolvedErrorText != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endInput,
                    onValueChange = onEndChange,
                    label = { Text(stringResource(R.string.analytics_custom_end_label)) },
                    isError = resolvedErrorText != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (resolvedErrorText != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = resolvedErrorText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.analytics_custom_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}