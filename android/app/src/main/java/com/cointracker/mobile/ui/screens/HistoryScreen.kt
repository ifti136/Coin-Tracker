package com.cointracker.mobile.ui.screens

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cointracker.mobile.data.ProfileEnvelope
import com.cointracker.mobile.ui.components.GlassCard
import java.util.Calendar

@Composable
fun HistoryScreen(
    envelope: ProfileEnvelope?,
    onDelete: (String) -> Unit,
    onEdit: (String, Int, String, String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterSource by remember { mutableStateOf("All") }
    var filterDate by remember { mutableStateOf("") }
    var currentPage by remember { mutableIntStateOf(0) }

    // Edit Dialog State
    var editingTxId by remember { mutableStateOf<String?>(null) }
    var editAmount by remember { mutableStateOf("") }
    var editSource by remember { mutableStateOf("") }
    var editDate by remember { mutableStateOf("") }

    val itemsPerPage = 10
    val allTransactions = envelope?.transactions ?: emptyList()
    val textColor = MaterialTheme.colorScheme.onSurface

    // Date Picker
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
            // Format date to match standard ISO format roughly (YYYY-MM-DD) for filtering
            filterDate = String.format("%d-%02d-%02d", year, month + 1, dayOfMonth)
            currentPage = 0
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    // Filtering Logic
    val filteredList = allTransactions.filter {
        (filterSource == "All" || it.source == filterSource) &&
                (searchQuery.isBlank() || it.source.contains(searchQuery, ignoreCase = true) || it.amount.toString().contains(searchQuery)) &&
                (filterDate.isBlank() || it.date.startsWith(filterDate))
    }.sortedByDescending { it.date }

    // Stats for Filtered View
    val totalIncome = filteredList.filter { it.amount > 0 }.sumOf { it.amount }
    val totalExpense = filteredList.filter { it.amount < 0 }.sumOf { it.amount }
    val net = totalIncome + totalExpense

    val totalPages = maxOf(1, (filteredList.size + itemsPerPage - 1) / itemsPerPage)
    val currentItems = filteredList.drop(currentPage * itemsPerPage).take(itemsPerPage)
    val sources = listOf("All") + allTransactions.map { it.source }.distinct().sorted()

    if (editingTxId != null) {
        AlertDialog(
            onDismissRequest = { editingTxId = null },
            title = { Text("Edit Transaction") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editSource,
                        onValueChange = { editSource = it },
                        label = { Text("Source") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editAmount,
                        onValueChange = { editAmount = it },
                        label = { Text("Amount") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editDate,
                        onValueChange = { editDate = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = editAmount.toIntOrNull()
                    if (amt != null && editSource.isNotBlank()) {
                        onEdit(editingTxId!!, amt, editSource, editDate)
                        editingTxId = null
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingTxId = null }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("History", style = MaterialTheme.typography.headlineMedium, color = textColor)
        Spacer(Modifier.height(12.dp))

        // Stats Row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Income", fontSize = 12.sp, color = Color.Gray)
                Text("+$totalIncome", color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Expense", fontSize = 12.sp, color = Color.Gray)
                Text("$totalExpense", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Net", fontSize = 12.sp, color = Color.Gray)
                Text("$net", color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold)
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
                    Button(onClick = { datePickerDialog.show() }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Date")
                    }
                    if (filterDate.isNotEmpty()) {
                        IconButton(onClick = { filterDate = "" }) { Icon(Icons.Default.Close, null, tint = textColor) }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // List
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(currentItems) { tx ->
                GlassCard {
                    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tx.source, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
                            Text(tx.date.take(10), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                if (tx.amount >= 0) "+${tx.amount}" else "${tx.amount}",
                                color = if (tx.amount >= 0) Color(0xFF10B981) else Color(0xFFEF4444),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Row {
                                TextButton(onClick = {
                                    editingTxId = tx.id
                                    editAmount = tx.amount.toString()
                                    editSource = tx.source
                                    editDate = tx.date
                                }) { Text("Edit", fontSize = 12.sp) }
                                TextButton(onClick = { onDelete(tx.id) }) { Text("Del", color = Color(0xFFEF4444), fontSize = 12.sp) }
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
                    Icon(Icons.Default.ArrowBack, "Prev", tint = textColor)
                }
                Text("Page ${currentPage + 1} of $totalPages", color = textColor)
                IconButton(onClick = { if (currentPage < totalPages - 1) currentPage++ }, enabled = currentPage < totalPages - 1) {
                    Icon(Icons.Default.ArrowForward, "Next", tint = textColor)
                }
            }
        }
    }
}