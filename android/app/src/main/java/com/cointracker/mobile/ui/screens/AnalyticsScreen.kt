package com.cointracker.mobile.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cointracker.mobile.data.ProfileEnvelope
import com.cointracker.mobile.data.Transaction
import com.cointracker.mobile.ui.components.GlassCard
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private enum class Period { LIFETIME, MONTHLY, WEEKLY, CUSTOM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(envelope: ProfileEnvelope?) {
    val allTransactions = envelope?.transactions ?: emptyList()
    val textColor       = MaterialTheme.colorScheme.onSurface

    var period       by remember { mutableStateOf(Period.LIFETIME) }
    var customStart  by remember { mutableStateOf<LocalDate?>(null) }
    var customEnd    by remember { mutableStateOf<LocalDate?>(null) }
    var showPicker   by remember { mutableStateOf(false) }
    val datePickerState = rememberDateRangePickerState()
    val displayFmt   = DateTimeFormatter.ofPattern("MM/dd")

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = datePickerState.selectedStartDateMillis
                    val e = datePickerState.selectedEndDateMillis
                    if (s != null) {
                        customStart = Instant.ofEpochMilli(s).atZone(ZoneOffset.UTC).toLocalDate()
                        customEnd   = if (e != null) Instant.ofEpochMilli(e).atZone(ZoneOffset.UTC).toLocalDate() else customStart
                        period = Period.CUSTOM
                    }
                    showPicker = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) { DateRangePicker(state = datePickerState) }
    }

    val today = LocalDate.now(ZoneOffset.UTC)
    val filtered: List<Transaction> = remember(allTransactions, period, customStart, customEnd) {
        val (winStart, winEnd) = when (period) {
            Period.LIFETIME -> null to null
            Period.WEEKLY   -> today.minusDays(today.dayOfWeek.ordinal.toLong()) to today
            Period.MONTHLY  -> today.with(TemporalAdjusters.firstDayOfMonth()) to today
            Period.CUSTOM   -> customStart to customEnd
        }
        if (winStart == null) allTransactions
        else allTransactions.filter { tx ->
            val d = runCatching { Instant.parse(tx.date).atZone(ZoneOffset.UTC).toLocalDate() }.getOrNull()
                ?: return@filter false
            !d.isBefore(winStart) && (winEnd == null || !d.isAfter(winEnd))
        }
    }

    val totalEarnings = filtered.filter { it.amount > 0 }.sumOf { it.amount }
    val totalSpending = filtered.filter { it.amount < 0 }.sumOf { -it.amount }
    val net           = totalEarnings - totalSpending

    val earningsBreakdown = mutableMapOf<String, Int>().also { m ->
        filtered.filter { it.amount > 0 }.forEach { m[it.source] = (m[it.source] ?: 0) + it.amount }
    }
    val spendingBreakdown = mutableMapOf<String, Int>().also { m ->
        filtered.filter { it.amount < 0 }.forEach { m[it.source] = (m[it.source] ?: 0) + (-it.amount) }
    }

    // Pull pre-computed values from analytics snapshot (LIFETIME only)
    val bestWeekEarnings = envelope?.analytics?.bestWeekEarnings ?: 0
    val bestWeekLabel    = envelope?.analytics?.bestWeekLabel ?: "N/A"
    val dailyRate7d      = envelope?.analytics?.dailyRate7d ?: 0.0

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(16.dp)) }

        item { Text("Analytics", style = MaterialTheme.typography.headlineMedium, color = textColor) }

        // Period chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(Period.LIFETIME to "Lifetime", Period.MONTHLY to "Monthly", Period.WEEKLY to "Weekly")
                        .forEach { (p, label) ->
                            FilterChip(selected = period == p, onClick = { period = p },
                                label = { Text(label, fontSize = 13.sp) }, modifier = Modifier.weight(1f))
                        }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = period == Period.CUSTOM,
                        onClick = { showPicker = true },
                        label = {
                            if (period == Period.CUSTOM && customStart != null && customEnd != null)
                                Text("${customStart!!.format(displayFmt)} – ${customEnd!!.format(displayFmt)}", fontSize = 12.sp)
                            else Text("Custom Range", fontSize = 13.sp)
                        },
                        leadingIcon = { Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                    if (period == Period.CUSTOM) {
                        IconButton(onClick = { period = Period.LIFETIME; customStart = null; customEnd = null },
                            modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Stat boxes
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatBox("Earnings", "+$totalEarnings", Color(0xFF10B981))
                StatBox("Spending", "$totalSpending",  Color(0xFFEF4444))
                StatBox("Net",      "$net",             Color(0xFF3B82F6))
            }
        }

        // Timeline
        item {
            Text("Balance Timeline", style = MaterialTheme.typography.titleMedium, color = textColor)
            GlassCard(modifier = Modifier.height(200.dp)) {
                if (filtered.isNotEmpty()) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        val points = filtered.sortedBy { it.date }
                            .runningFold(0f) { sum, tx -> sum + tx.amount.toFloat() }
                        val max = points.maxOrNull() ?: 1f
                        val min = points.minOrNull() ?: 0f
                        val range = (max - min).coerceAtLeast(1f)
                        val wpp = size.width / (points.size - 1).coerceAtLeast(1)
                        for (i in 0 until points.size - 1) {
                            drawLine(
                                Color(0xFF3B82F6),
                                Offset(i * wpp, size.height - ((points[i] - min) / range * size.height)),
                                Offset((i + 1) * wpp, size.height - ((points[i + 1] - min) / range * size.height)),
                                strokeWidth = 5f
                            )
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No data for this period", color = textColor)
                    }
                }
            }
        }

        // Earnings breakdown
        item {
            Text("Earnings Breakdown", style = MaterialTheme.typography.titleMedium, color = textColor)
            if (earningsBreakdown.isNotEmpty()) PieChartWithLegend(earningsBreakdown, textColor)
            else Text("No earnings for this period", color = Color.Gray)
        }

        // Spending breakdown
        item {
            Text("Spending Breakdown", style = MaterialTheme.typography.titleMedium, color = textColor)
            if (spendingBreakdown.isNotEmpty()) PieChartWithLegend(spendingBreakdown, textColor)
            else Text("No spending for this period", color = Color.Gray)
        }

        // ── NEW: 7-day rate card ──────────────────────────────────────────────
        item {
            GlassCard {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("7-Day Earning Rate", style = MaterialTheme.typography.titleSmall,
                            color = textColor.copy(alpha = 0.7f))
                        Text("${dailyRate7d.toInt()} coins/day",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3B82F6))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Last 7 days total", style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.5f))
                        Text("${(dailyRate7d * 7).toInt()} coins",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF10B981))
                    }
                }
            }
        }

        // ── NEW: Best earning week card ───────────────────────────────────────
        if (bestWeekEarnings > 0) {
            item {
                GlassCard {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("🏆 Best Earning Week", style = MaterialTheme.typography.titleSmall,
                                color = textColor.copy(alpha = 0.7f))
                            Text(bestWeekLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.5f))
                        }
                        Text("+$bestWeekEarnings coins",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF59E0B))  // amber/gold
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun StatBox(label: String, value: String, color: Color) {
    GlassCard(modifier = Modifier.width(100.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Text(value, style = MaterialTheme.typography.titleMedium, color = color)
        }
    }
}

@Composable
fun PieChartWithLegend(data: Map<String, Int>, textColor: Color) {
    GlassCard {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val total = data.values.sum().toFloat()
            val colors = listOf(
                Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B),
                Color(0xFFEF4444), Color(0xFF8B5CF6)
            )
            Canvas(modifier = Modifier.size(100.dp)) {
                var startAngle = -90f
                data.values.forEachIndexed { index, value ->
                    val sweep = (value / total) * 360f
                    drawArc(colors[index % colors.size], startAngle, sweep, useCenter = true)
                    startAngle += sweep
                }
            }
            Spacer(Modifier.width(24.dp))
            Column {
                data.entries.forEachIndexed { index, entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.size(12.dp), color = colors[index % colors.size],
                            shape = MaterialTheme.shapes.small) {}
                        Spacer(Modifier.width(8.dp))
                        Text("${entry.key}: ${entry.value}",
                            style = MaterialTheme.typography.bodySmall, color = textColor)
                    }
                }
            }
        }
    }
}
