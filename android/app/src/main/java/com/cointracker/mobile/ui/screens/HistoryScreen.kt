package com.cointracker.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun HistoryScreen(envelope: ProfileEnvelope?, onDelete: (String) -> Unit, onEdit: (String, Int, String, String) -> Unit) {
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
            val matchesSearch = searchQuery.isBlank() || tx.source.contains(searchQuery, ignoreCase = true) || tx.amount.toString().contains(searchQuery)
            val txLocalDate: LocalDate? = runCatching { Instant.parse(tx.date).atZone(ZoneOffset.UTC).toLocalDate() }
                .getOrElse { runCatching { java.time.OffsetDateTime.parse(tx.date).atZoneSameInstant(ZoneOffset.UTC).toLocalDate() }
                    .getOrElse { runCatching { LocalDate.parse(tx.date.take(10)) }.getOrNull() } }
            val matchesDate = if (dateRangeStartDay != null && dateRangeEndDay != null && txLocalDate != null)
                !txLocalDate.isBefore(dateRangeStartDay!!) && !txLocalDate.isAfter(dateRangeEndDay!!) else true
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

    if (showDatePicker) {
        DatePickerDialog(onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val startMillis = datePickerState.selectedStartDateMillis
                    val endMillis = datePickerState.selectedEndDateMillis
                    if (startMillis != null) {
                        dateRangeStartDay = Instant.ofEpochMilli(startMillis).atZone(ZoneOffset.UTC).toLocalDate()
                        dateRangeEndDay = if (endMillis != null) Instant.ofEpochMilli(endMillis).atZone(ZoneOffset.UTC).toLocalDate() else dateRangeStartDay
                    }
                    showDatePicker = false; currentPage = 0
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DateRangePicker(state = datePickerState) }
    }

    if (editingTxId != null) {
        AlertDialog(onDismissRequest = { editingTxId = null }, containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Edit Transaction") },
            text = {
                Column {
                    OutlinedTextField(value = editSource, onValueChange = { editSource = it }, label = { Text("Source") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = editAmount, onValueChange = { editAmount = it }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = editDate, onValueChange = { editDate = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("e.g. 2025-03-15") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = editAmount.toIntOrNull()
                    if (amt != null && editSource.isNotBlank() && editDate.isNotBlank()) { onEdit(editingTxId!!, amt, editSource, editDate); editingTxId = null }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingTxId = null }) { Text("Cancel") } })
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { Spacer(Modifier.height(16.dp)); Text("History", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface); Spacer(Modifier.height(12.dp)) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Income", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)); Text("+$totalIncome", color = WebSuccess, fontWeight = FontWeight.Bold) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Expense", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)); Text("$totalExpense", color = WebDanger, fontWeight = FontWeight.Bold) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Net", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)); Text("$net", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(12.dp))
        }
        item {
            GlassCard {
                Column(modifier = Modifier.padding(8.dp)) {
                    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it; currentPage = 0 }, label = { Text("Search") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var expanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.weight(1f)) {
                            Button(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("Src: $filterSource") }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                sources.forEach { src -> DropdownMenuItem(text = { Text(src) }, onClick = { filterSource = src; currentPage = 0; expanded = false }) }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { showDatePicker = true }) {
                            if (dateRangeStartDay != null && dateRangeEndDay != null) Text("${dateRangeStartDay!!.format(displayFmt)}-${dateRangeEndDay!!.format(displayFmt)}", fontSize = 11.sp)
                            else Icon(Icons.Default.DateRange, contentDescription = "Date")
                        }
                        if (dateRangeStartDay != null) { IconButton(onClick = { dateRangeStartDay = null; dateRangeEndDay = null; currentPage = 0 }) { Icon(Icons.Default.Close, null) } }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (filteredList.isEmpty()) {
            item { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) { Text("No transactions found", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) } }
        }
        items(currentItems) { tx ->
            GlassCard {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tx.source, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(tx.date.take(10), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (tx.amount >= 0) "+${tx.amount}" else "${tx.amount}", color = if (tx.amount >= 0) WebSuccess else WebDanger, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Row {
                            FilledTonalIconButton(onClick = { editingTxId = tx.id; editAmount = tx.amount.toString(); editSource = tx.source; editDate = tx.date.take(10) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            FilledTonalIconButton(onClick = { onDelete(tx.id) }, modifier = Modifier.size(32.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = WebDanger.copy(alpha = 0.1f), contentColor = WebDanger)) {
                                Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        if (totalPages > 1) {
            item {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (safeCurrentPage > 0) currentPage-- }, enabled = safeCurrentPage > 0) { Icon(Icons.Default.ArrowBack, "Prev") }
                    Text("Page ${safeCurrentPage + 1} of $totalPages")
                    IconButton(onClick = { if (safeCurrentPage < totalPages - 1) currentPage++ }, enabled = safeCurrentPage < totalPages - 1) { Icon(Icons.Default.ArrowForward, "Next") }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}
