package com.byteall.kharismart.ui.finance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byteall.kharismart.R
import com.byteall.kharismart.data.local.FinanceType
import com.byteall.kharismart.data.local.entity.FinanceEntity
import com.byteall.kharismart.di.appViewModel
import com.byteall.kharismart.util.format.digitsToLongOrNull
import com.byteall.kharismart.util.format.toRupiah
import com.byteall.kharismart.util.time.WibClock

@Composable
fun ManualFinanceScreen(
    viewModel: ManualFinanceViewModel = appViewModel { container ->
        ManualFinanceViewModel(container.financeRepository)
    }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ManualFinanceContent(
        state = state,
        onSelectPeriod = viewModel::setPeriod,
        onSelectTypeFilter = viewModel::setTypeFilter,
        onSelectSort = viewModel::setSortOption,
        onSearchQueryChange = viewModel::setSearchQuery,
        onOpenAddDialog = viewModel::openAddDialog,
        onDismissAddDialog = viewModel::dismissAddDialog,
        onClearFormError = viewModel::clearFormError,
        onSubmitRecord = viewModel::addRecord,
        onRequestDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onDismissDelete = viewModel::dismissDeleteDialog,
        onDismissBanner = viewModel::dismissBanner
    )
}

@Composable
fun ManualFinanceContent(
    state: ManualFinanceUiState,
    onSelectPeriod: (FinancePeriodFilter) -> Unit,
    onSelectTypeFilter: (FinanceTypeFilter) -> Unit,
    onSelectSort: (FinanceSortOption) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenAddDialog: () -> Unit,
    onDismissAddDialog: () -> Unit,
    onClearFormError: () -> Unit,
    onSubmitRecord: (
        type: String,
        category: String,
        amountText: String,
        description: String,
        customDateInput: String?,
        onSuccessResetForm: () -> Unit
    ) -> Unit,
    onRequestDelete: (FinanceEntity) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onDismissBanner: () -> Unit
) {
    val defaultCategories = listOf(
        stringResource(R.string.finance_cat_electricity),
        stringResource(R.string.finance_cat_packaging),
        stringResource(R.string.finance_cat_rent),
        stringResource(R.string.finance_cat_salaries),
        stringResource(R.string.finance_cat_other)
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        val isWideTablet = maxWidth >= 900.dp
        val isCompactCards = maxWidth < 600.dp

        if (isWideTablet) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier
                        .width(360.dp)
                        .fillMaxHeight(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.finance_form_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FinanceEntryForm(
                            categories = defaultCategories,
                            isSubmitting = state.isSubmitting,
                            formErrorMessage = state.formErrorMessage,
                            onClearFormError = onClearFormError,
                            onSubmit = onSubmitRecord
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    FinanceRecordsPane(
                        state = state,
                        showAddButton = false,
                        isCompactCards = false,
                        onSelectPeriod = onSelectPeriod,
                        onSelectTypeFilter = onSelectTypeFilter,
                        onSelectSort = onSelectSort,
                        onSearchQueryChange = onSearchQueryChange,
                        onOpenAddDialog = onOpenAddDialog,
                        onRequestDelete = onRequestDelete,
                        onDismissBanner = onDismissBanner
                    )
                }
            }
        } else {
            FinanceRecordsPane(
                state = state,
                showAddButton = true,
                isCompactCards = isCompactCards,
                onSelectPeriod = onSelectPeriod,
                onSelectTypeFilter = onSelectTypeFilter,
                onSelectSort = onSelectSort,
                onSearchQueryChange = onSearchQueryChange,
                onOpenAddDialog = onOpenAddDialog,
                onRequestDelete = onRequestDelete,
                onDismissBanner = onDismissBanner
            )
        }
    }

    if (state.isAddDialogOpen) {
        AlertDialog(
            onDismissRequest = onDismissAddDialog,
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp)
                .padding(vertical = 16.dp),
            title = {
                Text(
                    text = stringResource(R.string.finance_form_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    FinanceEntryForm(
                        categories = defaultCategories,
                        isSubmitting = state.isSubmitting,
                        formErrorMessage = state.formErrorMessage,
                        onClearFormError = onClearFormError,
                        onSubmit = onSubmitRecord
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismissAddDialog) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    state.recordToDelete?.let { target ->
        val typeLabel = if (target.type == FinanceType.INCOME) {
            stringResource(R.string.finance_type_income)
        } else {
            stringResource(R.string.finance_type_expense)
        }
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(stringResource(R.string.finance_delete_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.finance_delete_dialog_message,
                        typeLabel,
                        target.category,
                        target.amount.toRupiah()
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmDelete,
                    enabled = !state.isSubmitting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.finance_delete_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = onDismissDelete,
                    enabled = !state.isSubmitting
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun FinanceRecordsPane(
    state: ManualFinanceUiState,
    showAddButton: Boolean,
    isCompactCards: Boolean,
    onSelectPeriod: (FinancePeriodFilter) -> Unit,
    onSelectTypeFilter: (FinanceTypeFilter) -> Unit,
    onSelectSort: (FinanceSortOption) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenAddDialog: () -> Unit,
    onRequestDelete: (FinanceEntity) -> Unit,
    onDismissBanner: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.finance_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.finance_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showAddButton) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(onClick = onOpenAddDialog) {
                        Text(stringResource(R.string.finance_add_button))
                    }
                }
            }
        }

        state.bannerMessage?.let { message ->
            item(key = "banner") {
                val text = when (message) {
                    is FinanceUiMessage.Plain -> message.text
                    is FinanceUiMessage.Resource -> stringResource(message.resId)
                }
                FinanceFeedbackBanner(
                    message = text,
                    isError = state.isBannerError,
                    onDismiss = onDismissBanner
                )
            }
        }

        item(key = "period_chips") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FinancePeriodFilter.entries.forEach { period ->
                    FilterChip(
                        selected = state.period == period,
                        onClick = { onSelectPeriod(period) },
                        label = { Text(stringResource(period.labelRes)) }
                    )
                }
            }
        }

        item(key = "summary_cards") {
            val netColor = if (state.netAmount >= 0L) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
            if (isCompactCards) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FinanceSummaryCard(
                            title = stringResource(R.string.finance_kpi_income),
                            amountText = state.totalIncome.toRupiah(),
                            accentColor = Color(0xFF2E7D32),
                            modifier = Modifier.weight(1f)
                        )
                        FinanceSummaryCard(
                            title = stringResource(R.string.finance_kpi_expense),
                            amountText = state.totalExpense.toRupiah(),
                            accentColor = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    FinanceSummaryCard(
                        title = stringResource(R.string.finance_kpi_net),
                        amountText = state.netAmount.toRupiah(),
                        accentColor = netColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FinanceSummaryCard(
                        title = stringResource(R.string.finance_kpi_income),
                        amountText = state.totalIncome.toRupiah(),
                        accentColor = Color(0xFF2E7D32),
                        modifier = Modifier.weight(1f)
                    )
                    FinanceSummaryCard(
                        title = stringResource(R.string.finance_kpi_expense),
                        amountText = state.totalExpense.toRupiah(),
                        accentColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    FinanceSummaryCard(
                        title = stringResource(R.string.finance_kpi_net),
                        amountText = state.netAmount.toRupiah(),
                        accentColor = netColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item(key = "search_bar") {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.finance_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.debt_search_clear_cd)
                            )
                        }
                    }
                }
            )
        }

        item(key = "filters_and_sort") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FinanceTypeFilter.entries.forEach { tf ->
                    FilterChip(
                        selected = state.typeFilter == tf,
                        onClick = { onSelectTypeFilter(tf) },
                        label = { Text(stringResource(tf.labelRes)) }
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.finance_sort_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FinanceSortOption.entries.forEach { sortOpt ->
                    FilterChip(
                        selected = state.sortOption == sortOpt,
                        onClick = { onSelectSort(sortOpt) },
                        label = { Text(stringResource(sortOpt.labelRes)) }
                    )
                }
            }
        }

        if (state.records.isEmpty()) {
            item(key = "empty_state") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.finance_empty_state),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(
                items = state.records,
                key = { it.financeId }
            ) { item ->
                FinanceRecordRowCard(
                    record = item,
                    onDeleteClick = { onRequestDelete(item) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinanceEntryForm(
    categories: List<String>,
    isSubmitting: Boolean,
    formErrorMessage: FinanceUiMessage?,
    onClearFormError: () -> Unit,
    onSubmit: (
        type: String,
        category: String,
        amountText: String,
        description: String,
        customDateInput: String?,
        onSuccessResetForm: () -> Unit
    ) -> Unit
) {
    var selectedType by rememberSaveable { mutableStateOf(FinanceType.EXPENSE) }
    var selectedCategory by rememberSaveable { mutableStateOf(categories.firstOrNull().orEmpty()) }
    var categoryExpanded by rememberSaveable { mutableStateOf(false) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var descriptionText by rememberSaveable { mutableStateOf("") }
    var customDateText by rememberSaveable { mutableStateOf(WibClock.todayString()) }

    formErrorMessage?.let { msg ->
        val errText = when (msg) {
            is FinanceUiMessage.Plain -> msg.text
            is FinanceUiMessage.Resource -> stringResource(msg.resId)
        }
        FinanceFeedbackBanner(
            message = errText,
            isError = true,
            onDismiss = onClearFormError
        )
        Spacer(modifier = Modifier.height(10.dp))
    }

    Text(
        text = stringResource(R.string.finance_field_type_label),
        style = MaterialTheme.typography.labelLarge
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedType == FinanceType.EXPENSE,
            onClick = {
                selectedType = FinanceType.EXPENSE
                onClearFormError()
            },
            label = { Text(stringResource(R.string.finance_type_expense)) },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = selectedType == FinanceType.INCOME,
            onClick = {
                selectedType = FinanceType.INCOME
                onClearFormError()
            },
            label = { Text(stringResource(R.string.finance_type_income)) },
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    ExposedDropdownMenuBox(
        expanded = categoryExpanded,
        onExpandedChange = { categoryExpanded = !categoryExpanded }
    ) {
        OutlinedTextField(
            value = selectedCategory,
            onValueChange = {
                selectedCategory = it
                onClearFormError()
            },
            label = { Text(stringResource(R.string.finance_field_category_label)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(
            expanded = categoryExpanded,
            onDismissRequest = { categoryExpanded = false }
        ) {
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat) },
                    onClick = {
                        selectedCategory = cat
                        categoryExpanded = false
                        onClearFormError()
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    OutlinedTextField(
        value = amountText,
        onValueChange = { input ->
            amountText = input.filter { it.isDigit() }
            onClearFormError()
        },
        label = { Text(stringResource(R.string.finance_field_amount_label)) },
        placeholder = { Text(stringResource(R.string.finance_field_amount_placeholder)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        supportingText = {
            val parsed = amountText.digitsToLongOrNull()
            if (parsed != null && parsed > 0L) {
                Text(parsed.toRupiah())
            }
        },
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(6.dp))

    OutlinedTextField(
        value = customDateText,
        onValueChange = {
            customDateText = it
            onClearFormError()
        },
        label = { Text(stringResource(R.string.finance_field_date_label)) },
        singleLine = true,
        trailingIcon = {
            TextButton(onClick = {
                customDateText = WibClock.todayString()
                onClearFormError()
            }) {
                Text(stringResource(R.string.finance_period_today))
            }
        },
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(10.dp))

    OutlinedTextField(
        value = descriptionText,
        onValueChange = {
            descriptionText = it
            onClearFormError()
        },
        label = { Text(stringResource(R.string.finance_field_desc_label)) },
        placeholder = { Text(stringResource(R.string.finance_field_desc_placeholder)) },
        maxLines = 3,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = {
            onSubmit(
                selectedType,
                selectedCategory,
                amountText,
                descriptionText,
                customDateText
            ) {
                amountText = ""
                descriptionText = ""
                customDateText = WibClock.todayString()
            }
        },
        enabled = !isSubmitting,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.finance_submit_button))
    }
}

@Composable
private fun FinanceSummaryCard(
    title: String,
    amountText: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = amountText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FinanceRecordRowCard(
    record: FinanceEntity,
    onDeleteClick: () -> Unit
) {
    val isIncome = record.type == FinanceType.INCOME
    val badgeColor = if (isIncome) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
    val badgeText = if (isIncome) {
        stringResource(R.string.finance_type_income)
    } else {
        stringResource(R.string.finance_type_expense)
    }
    val signedPrefix = if (isIncome) "+" else "-"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = badgeColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = record.category,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!record.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = record.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = WibClock.displayDateTime(record.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$signedPrefix${record.amount.toRupiah()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.finance_delete_cd),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun FinanceFeedbackBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit
) {
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
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
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