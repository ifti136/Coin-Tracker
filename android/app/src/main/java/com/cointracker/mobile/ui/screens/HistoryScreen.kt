package com.cointracker.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cointracker.mobile.data.ProfileEnvelope
import com.cointracker.mobile.ui.components.GlassCard
import com.cointracker.mobile.ui.theme.WebSuccess
import com.cointracker.mobile.ui.theme.WebDanger
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    envelope: ProfileEnvelope?,
    onDelete: (String) -> Unit,
    onEdit: (String, Int, String, String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterSource by remember { mutableStateOf("All") }
    var currentPage by remember { mutableIntStateOf(0) }

    // Date Range Logic
    var showDatePicker by remember { mutableStateOf(false) }
    var dateRangeStart by remember { mutableStateOf<Long?>(null) }
    var dateRangeEnd by remember { mutableStateOf<Long?>(null) }
    val datePickerState = rememberDateRangePickerState()

    // Edit Dialog State
    var editingTxId by remember { mutableStateOf<String?>(null) }
    var editAmount by remember { mutableStateOf("") }
    var editSource by remember { mutableStateOf("") }
    var editDate by remember { mutableStateOf("") }

    val itemsPerPage = 10
    val allTransactions = envelope?.transactions ?: emptyList()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // Filtering Logic
    val filteredList = allTransactions.filter { tx ->
        val matchesSource = filterSource == "All" || tx.source == filterSource
        val matchesSearch = searchQuery.isBlank() || tx.source.contains(searchQuery, ignoreCase = true) || tx.amount.toString().contains(searchQuery)

        val txDateMillis = try { dateFormat.parse(tx.date.take(10))?.time } catch(e: Exception) { null }
        val matchesDate = if (dateRangeStart != null && dateRangeEnd != null && txDateMillis != null) {
            txDateMillis >= dateRangeStart!! && txDateMillis <= dateRangeEnd!!
        } else true

        matchesSource && matchesSearch && matchesDate
    }.sortedByDescending { it.date }

    val totalIncome = filteredList.filter { it.amount > 0 }.sumOf { it.amount }
    val totalExpense = filteredList.filter { it.amount < 0 }.sumOf { it.amount }
    val net = totalIncome + totalExpense

    val totalPages = maxOf(1, (filteredList.size + itemsPerPage - 1) / itemsPerPage)
    val currentItems = filteredList.drop(currentPage * itemsPerPage).take(itemsPerPage)
    val sources = listOf("All") + allTransactions.map { it.source }.distinct().sorted()

    // Date Picker Dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateRangeStart = datePickerState.selectedStartDateMillis
                    dateRangeEnd = datePickerState.selectedEndDateMillis
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

    // Edit Dialog
    if (editingTxId != null) {
        AlertDialog(
            onDismissRequest = { editingTxId = null },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Edit Transaction") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editSource, onValueChange = { editSource = it },
                        label = { Text("Source") }, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editAmount, onValueChange = { editAmount = it },
                        label = { Text("Amount") }, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editDate, onValueChange = { editDate = it },
                        label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    editAmount.toIntOrNull()?.let { amt ->
                        if (editSource.isNotBlank()) {
                            onEdit(editingTxId!!, amt, editSource, editDate)
                            editingTxId = null
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingTxId = null }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("History", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))

        // Stats Row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Income", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f))
                Text("+$totalIncome", color = WebSuccess, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Expense", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f))
                Text("$totalExpense", color = WebDanger, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Net", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f))
                Text("$net", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))

        // Filters
        GlassCard {
            Column(modifier = Modifier.padding(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it; currentPage = 0 },
                    label = { Text("Search") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
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
                                DropdownMenuItem(
                                    text = { Text(src) },
                                    onClick = { filterSource = src; currentPage = 0; expanded = false }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Date")
                    }
                    if (dateRangeStart != null) {
                        IconButton(onClick = { dateRangeStart = null; dateRangeEnd = null }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // List
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(currentItems) { tx ->
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tx.source, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(tx.date.take(10), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                if (tx.amount >= 0) "+${tx.amount}" else "${tx.amount}",
                                color = if (tx.amount >= 0) WebSuccess else WebDanger,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Row {
                                FilledTonalIconButton(
                                    onClick = {
                                        editingTxId = tx.id
                                        editAmount = tx.amount.toString()
                                        editSource = tx.source
                                        editDate = tx.date
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(4.dp))
                                FilledTonalIconButton(
                                    onClick = { onDelete(tx.id) },
                                    modifier = Modifier.size(32.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = WebDanger.copy(alpha = 0.1f),
                                        contentColor = WebDanger
                                    )
                                ) {
                                    Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (totalPages > 1) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (currentPage > 0) currentPage-- }, enabled = currentPage > 0) {
                    Icon(Icons.Default.ArrowBack, "Prev")
                }
                Text("Page ${currentPage + 1} of $totalPages")
                IconButton(onClick = { if (currentPage < totalPages - 1) currentPage++ }, enabled = currentPage < totalPages - 1) {
                    Icon(Icons.Default.ArrowForward, "Next")
                }
            }
        }
    }
}