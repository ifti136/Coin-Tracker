package com.cointracker.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cointracker.mobile.data.ProfileEnvelope
import com.cointracker.mobile.ui.components.GlassCard
import com.cointracker.mobile.ui.theme.borderSubtle
import com.cointracker.mobile.ui.theme.expense
import com.cointracker.mobile.ui.theme.expenseContainer
import com.cointracker.mobile.ui.theme.income
import com.cointracker.mobile.ui.theme.incomeContainer
import com.cointracker.mobile.ui.theme.surfaceSubtle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    envelope: ProfileEnvelope?,
    isActionLoading: (String) -> Boolean,
    onDelete: (String) -> Unit,
    onEdit: (String, Int, String, String) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var filterSource by rememberSaveable { mutableStateOf("All") }
    var currentPage by rememberSaveable { mutableIntStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var dateRangeStartDay by remember { mutableStateOf<LocalDate?>(null) }
    var dateRangeEndDay by remember { mutableStateOf<LocalDate?>(null) }
    val datePickerState = rememberDateRangePickerState()
    var editingTxId by remember { mutableStateOf<String?>(null) }
    var editAmount by remember { mutableStateOf("") }
    var editSource by remember { mutableStateOf("") }
    var editDate by remember { mutableStateOf("") }
    val itemsPerPage = 10
    val allTransactions = envelope?.transactions ?: emptyList()
    val displayFmt = DateTimeFormatter.ofPattern("MM/dd")

    val filteredList = remember(allTransactions, searchQuery, filterSource, dateRangeStartDay, dateRangeEndDay) {
        allTransactions.filter { tx ->
            val matchesSource = filterSource == "All" || tx.source == filterSource
            val matchesSearch = searchQuery.isBlank() ||
                tx.source.contains(searchQuery, ignoreCase = true) ||
                tx.amount.toString().contains(searchQuery)
            val txLocalDate: LocalDate? = runCatching {
                Instant.parse(tx.date).atZone(ZoneOffset.UTC).toLocalDate()
            }.getOrElse {
                runCatching {
                    java.time.OffsetDateTime.parse(tx.date).atZoneSameInstant(ZoneOffset.UTC).toLocalDate()
                }.getOrElse { runCatching { LocalDate.parse(tx.date.take(10)) }.getOrNull() }
            }
            val matchesDate = if (dateRangeStartDay != null && dateRangeEndDay != null && txLocalDate != null)
                !txLocalDate.isBefore(dateRangeStartDay!!) && !txLocalDate.isAfter(dateRangeEndDay!!)
            else true
            matchesSource && matchesSearch && matchesDate
        }.sortedByDescending { it.date }
    }

    val totalIncome = filteredList.filter { it.amount > 0 }.sumOf { it.amount }
    val totalExpense = filteredList.filter { it.amount < 0 }.sumOf { it.amount }
    val net = totalIncome + totalExpense
    val totalPages = maxOf(1, (filteredList.size + itemsPerPage - 1) / itemsPerPage)
    val safeCurrentPage = currentPage.coerceAtMost(totalPages - 1)
    val currentItems = filteredList.drop(safeCurrentPage * itemsPerPage).take(itemsPerPage)
    val sources = listOf("All") + allTransactions.map { it.source }.distinct().sorted()

    // ── Date picker dialog ──────────────────────────────────────────────────
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = datePickerState.selectedStartDateMillis
                    val e = datePickerState.selectedEndDateMillis
                    if (s != null) {
                        dateRangeStartDay = Instant.ofEpochMilli(s).atZone(ZoneOffset.UTC).toLocalDate()
                        dateRangeEndDay = if (e != null) Instant.ofEpochMilli(e).atZone(ZoneOffset.UTC).toLocalDate() else dateRangeStartDay
                    }
                    showDatePicker = false
                    currentPage = 0
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(state = datePickerState)
        }
    }

    // ── Edit dialog ────────────────────────────────────────────────────────
    if (editingTxId != null) {
        AlertDialog(
            onDismissRequest = { editingTxId = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "Edit Transaction",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editSource,
                        onValueChange = { editSource = it },
                        label = { Text("Source") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = editAmount,
                        onValueChange = { editAmount = it },
                        label = { Text("Amount (positive = income, negative = spend)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = editDate,
                        onValueChange = { editDate = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. 2026-03-15") },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                val isEditing = isActionLoading("updateTransaction")
                Button(
                    onClick = {
                        val amt = editAmount.toIntOrNull()
                        if (amt != null && editSource.isNotBlank() && editDate.isNotBlank()) {
                            onEdit(editingTxId!!, amt, editSource, editDate)
                            editingTxId = null
                        }
                    },
                    enabled = !isEditing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isEditing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTxId = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(Modifier.height(12.dp)) }

        // Header
        item {
            Column {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Track, search, and manage all your past coin movements",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Summary Cards Row ────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryStatBox(
                    label = "Total Income",
                    value = "+${totalIncome.formatCoins()}",
                    color = MaterialTheme.colorScheme.income,
                    bgColor = MaterialTheme.colorScheme.incomeContainer,
                    modifier = Modifier.weight(1f)
                )
                SummaryStatBox(
                    label = "Total Spent",
                    value = totalExpense.formatCoins(),
                    color = MaterialTheme.colorScheme.expense,
                    bgColor = MaterialTheme.colorScheme.expenseContainer,
                    modifier = Modifier.weight(1f)
                )
                SummaryStatBox(
                    label = "Net Change",
                    value = (if (net > 0) "+${net.formatCoins()}" else net.formatCoins()),
                    color = MaterialTheme.colorScheme.primary,
                    bgColor = MaterialTheme.colorScheme.surfaceSubtle,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Filter Card ──────────────────────────────────────────────────────
        item {
            GlassCard(shape = RoundedCornerShape(14.dp)) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            currentPage = 0
                        },
                        label = { Text("Search by source or amount") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = ""; currentPage = 0 }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var expanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Src: $filterSource",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                sources.forEach { src ->
                                    DropdownMenuItem(
                                        text = { Text(src, style = MaterialTheme.typography.bodyMedium) },
                                        onClick = {
                                            filterSource = src
                                            currentPage = 0
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            if (dateRangeStartDay != null && dateRangeEndDay != null) {
                                Text(
                                    "${dateRangeStartDay!!.format(displayFmt)} - ${dateRangeEndDay!!.format(displayFmt)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                Icon(Icons.Default.DateRange, contentDescription = "Select date range filter", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Dates", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        if (dateRangeStartDay != null) {
                            IconButton(
                                onClick = {
                                    dateRangeStartDay = null
                                    dateRangeEndDay = null
                                    currentPage = 0
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear date filter", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // ── Empty State ──────────────────────────────────────────────────────
        if (filteredList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        if (allTransactions.isEmpty()) {
                            Icon(
                                Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                "No transactions yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Start tracking your income and expenses to see your transaction history here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Icon(
                                Icons.Default.FilterListOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                "No matching transactions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "No transactions match your current search or filter criteria.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    searchQuery = ""
                                    filterSource = "All"
                                    dateRangeStartDay = null
                                    dateRangeEndDay = null
                                    currentPage = 0
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Clear Filters")
                            }
                        }
                    }
                }
            }
        }

        // ── Transaction Items (Swipe-to-delete) ──────────────────────────────
        items(currentItems, key = { it.id }) { tx ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.EndToStart) {
                        onDelete(tx.id)
                        true
                    } else false
                },
                positionalThreshold = { it * 0.4f }
            )

            val isSwiping = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val expenseColor = MaterialTheme.colorScheme.expense

            SwipeToDismissBox(
                state = dismissState,
                enableDismissFromStartToEnd = false,
                backgroundContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSwiping) expenseColor else Color.Transparent),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        if (isSwiping) {
                            Row(
                                modifier = Modifier.padding(end = 20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete transaction",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            ) {
                GlassCard(shape = RoundedCornerShape(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tx.source,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = tx.date.take(10),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isIncome = tx.amount >= 0
                            val badgeColor = if (isIncome) MaterialTheme.colorScheme.income else MaterialTheme.colorScheme.expense
                            val badgeBg = if (isIncome) MaterialTheme.colorScheme.incomeContainer else MaterialTheme.colorScheme.expenseContainer

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = badgeBg
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isIncome) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                        contentDescription = if (isIncome) "Income" else "Expense",
                                        tint = badgeColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = if (isIncome) "+${tx.amount.formatCoins()}" else tx.amount.formatCoins(),
                                        color = badgeColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            FilledTonalIconButton(
                                onClick = {
                                    editingTxId = tx.id
                                    editAmount = tx.amount.toString()
                                    editSource = tx.source
                                    editDate = tx.date.take(10)
                                },
                                modifier = Modifier.size(34.dp),
                                enabled = !isActionLoading("updateTransaction"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isActionLoading("updateTransaction")) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit transaction",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Pagination Pills (Bottom) ─────────────────────────────────────────
        if (totalPages > 1) {
            item {
                Spacer(Modifier.height(6.dp))
                PillsPagination(
                    totalPages = totalPages,
                    currentPage = safeCurrentPage,
                    onPageSelect = { currentPage = it }
                )
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── Supporting Components ───────────────────────────────────────────────────

@Composable
private fun SummaryStatBox(
    label: String,
    value: String,
    color: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = bgColor
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun PillsPagination(
    totalPages: Int,
    currentPage: Int,
    onPageSelect: (Int) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val onSurf = MaterialTheme.colorScheme.onSurface
    val pillShape = RoundedCornerShape(50)

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Prev arrow
        item {
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(34.dp)
                    .clip(pillShape)
                    .background(if (currentPage > 0) primary.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable(enabled = currentPage > 0) { onPageSelect(currentPage - 1) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Go to previous page",
                    tint = if (currentPage > 0) primary else onSurf.copy(alpha = 0.25f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Page pills — show window of 5 around current
        val windowStart = (currentPage - 2).coerceAtLeast(0)
        val windowEnd = (windowStart + 4).coerceAtMost(totalPages - 1)

        if (windowStart > 0) {
            item {
                PagePill(
                    page = 0,
                    isSelected = currentPage == 0,
                    primary = primary,
                    onSurf = onSurf,
                    pillShape = pillShape,
                    onPageSelect = onPageSelect
                )
            }
            if (windowStart > 1) {
                item {
                    Text(
                        "…",
                        color = onSurf.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }

        itemsIndexed((windowStart..windowEnd).toList()) { _, page ->
            PagePill(
                page = page,
                isSelected = currentPage == page,
                primary = primary,
                onSurf = onSurf,
                pillShape = pillShape,
                onPageSelect = onPageSelect
            )
        }

        if (windowEnd < totalPages - 1) {
            if (windowEnd < totalPages - 2) {
                item {
                    Text(
                        "…",
                        color = onSurf.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            item {
                PagePill(
                    page = totalPages - 1,
                    isSelected = currentPage == totalPages - 1,
                    primary = primary,
                    onSurf = onSurf,
                    pillShape = pillShape,
                    onPageSelect = onPageSelect
                )
            }
        }

        // Next arrow
        item {
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(34.dp)
                    .clip(pillShape)
                    .background(if (currentPage < totalPages - 1) primary.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable(enabled = currentPage < totalPages - 1) { onPageSelect(currentPage + 1) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Go to next page",
                    tint = if (currentPage < totalPages - 1) primary else onSurf.copy(alpha = 0.25f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun PagePill(
    page: Int,
    isSelected: Boolean,
    primary: Color,
    onSurf: Color,
    pillShape: RoundedCornerShape,
    onPageSelect: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .height(34.dp)
            .widthIn(min = 34.dp)
            .clip(pillShape)
            .background(if (isSelected) primary else primary.copy(alpha = 0.12f))
            .clickable { onPageSelect(page) }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${page + 1}",
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else onSurf.copy(alpha = 0.7f),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp
        )
    }
}

private fun Int.formatCoins(): String =
    toString().reversed().chunked(3).joinToString(",").reversed()
