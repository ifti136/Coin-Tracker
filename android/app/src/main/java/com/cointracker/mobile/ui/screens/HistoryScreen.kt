package com.cointracker.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cointracker.mobile.data.ProfileEnvelope
import com.cointracker.mobile.ui.components.GlassCard
import com.cointracker.mobile.ui.theme.WebDanger
import com.cointracker.mobile.ui.theme.WebSuccess
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    envelope: ProfileEnvelope?,
    onDelete: (String) -> Unit,
    onEdit: (String, Int, String, String) -> Unit
) {
    var searchQuery       by rememberSaveable { mutableStateOf("") }
    var filterSource      by rememberSaveable { mutableStateOf("All") }
    var currentPage       by rememberSaveable { mutableIntStateOf(0) }
    var showDatePicker    by remember { mutableStateOf(false) }
    var dateRangeStartDay by remember { mutableStateOf<LocalDate?>(null) }
    var dateRangeEndDay   by remember { mutableStateOf<LocalDate?>(null) }
    val datePickerState   = rememberDateRangePickerState()
    var editingTxId       by remember { mutableStateOf<String?>(null) }
    var editAmount        by remember { mutableStateOf("") }
    var editSource        by remember { mutableStateOf("") }
    var editDate          by remember { mutableStateOf("") }
    val itemsPerPage      = 10
    val allTransactions   = envelope?.transactions ?: emptyList()
    val displayFmt        = DateTimeFormatter.ofPattern("MM/dd")

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

    val totalIncome  = filteredList.filter { it.amount > 0 }.sumOf { it.amount }
    val totalExpense = filteredList.filter { it.amount < 0 }.sumOf { it.amount }
    val net          = totalIncome + totalExpense
    val totalPages   = maxOf(1, (filteredList.size + itemsPerPage - 1) / itemsPerPage)
    val safeCurrentPage = currentPage.coerceAtMost(totalPages - 1)
    val currentItems = filteredList.drop(safeCurrentPage * itemsPerPage).take(itemsPerPage)
    val sources      = listOf("All") + allTransactions.map { it.source }.distinct().sorted()

    // ── Date picker dialog ────────────────────────────────────────────────────
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = datePickerState.selectedStartDateMillis
                    val e = datePickerState.selectedEndDateMillis
                    if (s != null) {
                        dateRangeStartDay = Instant.ofEpochMilli(s).atZone(ZoneOffset.UTC).toLocalDate()
                        dateRangeEndDay   = if (e != null) Instant.ofEpochMilli(e).atZone(ZoneOffset.UTC).toLocalDate() else dateRangeStartDay
                    }
                    showDatePicker = false; currentPage = 0
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DateRangePicker(state = datePickerState) }
    }

    // ── Edit dialog ───────────────────────────────────────────────────────────
    if (editingTxId != null) {
        AlertDialog(
            onDismissRequest = { editingTxId = null },
            containerColor   = MaterialTheme.colorScheme.surfaceVariant,
            title            = { Text("Edit Transaction") },
            text             = {
                Column {
                    OutlinedTextField(value = editSource, onValueChange = { editSource = it },
                        label = { Text("Source") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = editAmount, onValueChange = { editAmount = it },
                        label = { Text("Amount") }, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = editDate, onValueChange = { editDate = it },
                        label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. 2025-03-15") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = editAmount.toIntOrNull()
                    if (amt != null && editSource.isNotBlank() && editDate.isNotBlank()) {
                        onEdit(editingTxId!!, amt, editSource, editDate)
                        editingTxId = null
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingTxId = null }) { Text("Cancel") } }
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("History", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
        }

        // Summary row
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Income", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text("+$totalIncome", color = WebSuccess, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Expense", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text("$totalExpense", color = WebDanger, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Net", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text("$net", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Filter card
        item {
            GlassCard {
                Column(modifier = Modifier.padding(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery, onValueChange = { searchQuery = it; currentPage = 0 },
                        label = { Text("Search") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var expanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.weight(1f)) {
                            Button(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Src: $filterSource")
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                sources.forEach { src ->
                                    DropdownMenuItem(text = { Text(src) },
                                        onClick = { filterSource = src; currentPage = 0; expanded = false })
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { showDatePicker = true }) {
                            if (dateRangeStartDay != null && dateRangeEndDay != null)
                                Text("${dateRangeStartDay!!.format(displayFmt)}-${dateRangeEndDay!!.format(displayFmt)}", fontSize = 11.sp)
                            else Icon(Icons.Default.DateRange, contentDescription = "Date")
                        }
                        if (dateRangeStartDay != null) {
                            IconButton(onClick = { dateRangeStartDay = null; dateRangeEndDay = null; currentPage = 0 }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ── Pills pagination — TOP ────────────────────────────────────────────
        if (totalPages > 1) {
            item {
                PillsPagination(
                    totalPages    = totalPages,
                    currentPage   = safeCurrentPage,
                    onPageSelect  = { currentPage = it }
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        // Empty state
        if (filteredList.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center) {
                    Text("No transactions found",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        }

        // ── Swipe-to-delete items ─────────────────────────────────────────────
        items(currentItems, key = { it.id }) { tx ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.EndToStart) {
                        onDelete(tx.id); true
                    } else false
                },
                positionalThreshold = { it * 0.4f }
            )

            val isSwiping = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart

            SwipeToDismissBox(
                state = dismissState,
                enableDismissFromStartToEnd = false,
                backgroundContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(if (isSwiping) WebDanger else Color.Transparent),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        if (isSwiping) {
                            Row(
                                modifier = Modifier.padding(end = 20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Delete, "Delete",
                                    tint = Color.White, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            ) {
                GlassCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tx.source,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                            Text(tx.date.take(10),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                if (tx.amount >= 0) "+${tx.amount}" else "${tx.amount}",
                                color = if (tx.amount >= 0) WebSuccess else WebDanger,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            FilledTonalIconButton(
                                onClick = {
                                    editingTxId = tx.id
                                    editAmount  = tx.amount.toString()
                                    editSource  = tx.source
                                    editDate    = tx.date.take(10)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // ── Pills pagination — BOTTOM ─────────────────────────────────────────
        if (totalPages > 1) {
            item {
                Spacer(Modifier.height(4.dp))
                PillsPagination(
                    totalPages   = totalPages,
                    currentPage  = safeCurrentPage,
                    onPageSelect = { currentPage = it }
                )
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── Pills pagination composable ───────────────────────────────────────────────

@Composable
fun PillsPagination(
    totalPages   : Int,
    currentPage  : Int,
    onPageSelect : (Int) -> Unit
) {
    val primary  = MaterialTheme.colorScheme.primary
    val surface  = MaterialTheme.colorScheme.surface
    val onSurf   = MaterialTheme.colorScheme.onSurface
    val pillShape = RoundedCornerShape(50)

    LazyRow(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // Prev arrow
        item {
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(36.dp)
                    .clip(pillShape)
                    .background(if (currentPage > 0) primary.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable(enabled = currentPage > 0) { onPageSelect(currentPage - 1) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Previous",
                    tint   = if (currentPage > 0) primary else onSurf.copy(alpha = 0.25f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Page pills — show window of 5 around current
        val windowStart = (currentPage - 2).coerceAtLeast(0)
        val windowEnd   = (windowStart + 4).coerceAtMost(totalPages - 1)

        if (windowStart > 0) {
            item {
                PagePill(page = 0, isSelected = currentPage == 0,
                    primary = primary, surface = surface, onSurf = onSurf,
                    pillShape = pillShape, onPageSelect = onPageSelect)
            }
            if (windowStart > 1) {
                item {
                    Text("…", color = onSurf.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }

        itemsIndexed((windowStart..windowEnd).toList()) { _, page ->
            PagePill(page = page, isSelected = currentPage == page,
                primary = primary, surface = surface, onSurf = onSurf,
                pillShape = pillShape, onPageSelect = onPageSelect)
        }

        if (windowEnd < totalPages - 1) {
            if (windowEnd < totalPages - 2) {
                item {
                    Text("…", color = onSurf.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            item {
                PagePill(page = totalPages - 1, isSelected = currentPage == totalPages - 1,
                    primary = primary, surface = surface, onSurf = onSurf,
                    pillShape = pillShape, onPageSelect = onPageSelect)
            }
        }

        // Next arrow
        item {
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(36.dp)
                    .clip(pillShape)
                    .background(if (currentPage < totalPages - 1) primary.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable(enabled = currentPage < totalPages - 1) { onPageSelect(currentPage + 1) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = "Next",
                    tint   = if (currentPage < totalPages - 1) primary else onSurf.copy(alpha = 0.25f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun PagePill(
    page         : Int,
    isSelected   : Boolean,
    primary      : Color,
    surface      : Color,
    onSurf       : Color,
    pillShape    : RoundedCornerShape,
    onPageSelect : (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .height(36.dp)
            .widthIn(min = 36.dp)
            .clip(pillShape)
            .background(if (isSelected) primary else primary.copy(alpha = 0.12f))
            .clickable { onPageSelect(page) }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = "${page + 1}",
            color      = if (isSelected) Color.White else onSurf.copy(alpha = 0.7f),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize   = 14.sp
        )
    }
}
