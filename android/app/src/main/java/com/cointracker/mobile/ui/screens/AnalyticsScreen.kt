package com.cointracker.mobile.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cointracker.mobile.data.ProfileEnvelope
import com.cointracker.mobile.data.Transaction
import com.cointracker.mobile.ui.components.GlassCard
import com.cointracker.mobile.ui.theme.expense
import com.cointracker.mobile.ui.theme.income
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
    val textColor = MaterialTheme.colorScheme.onSurface

    var period by remember { mutableStateOf(Period.LIFETIME) }
    var customStart by remember { mutableStateOf<LocalDate?>(null) }
    var customEnd by remember { mutableStateOf<LocalDate?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDateRangePickerState()
    val displayFmt = DateTimeFormatter.ofPattern("MM/dd")

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = datePickerState.selectedStartDateMillis
                    val e = datePickerState.selectedEndDateMillis
                    if (s != null) {
                        customStart = Instant.ofEpochMilli(s).atZone(ZoneOffset.UTC).toLocalDate()
                        customEnd = if (e != null) Instant.ofEpochMilli(e).atZone(ZoneOffset.UTC).toLocalDate() else customStart
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
            Period.WEEKLY -> today.minusDays(today.dayOfWeek.ordinal.toLong()) to today
            Period.MONTHLY -> today.with(TemporalAdjusters.firstDayOfMonth()) to today
            Period.CUSTOM -> customStart to customEnd
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
    val net = totalEarnings - totalSpending

    val earningsBreakdown = mutableMapOf<String, Int>().also { m ->
        filtered.filter { it.amount > 0 }.forEach { m[it.source] = (m[it.source] ?: 0) + it.amount }
    }
    val spendingBreakdown = mutableMapOf<String, Int>().also { m ->
        filtered.filter { it.amount < 0 }.forEach { m[it.source] = (m[it.source] ?: 0) + (-it.amount) }
    }

    val bestWeekEarnings = envelope?.analytics?.bestWeekEarnings ?: 0
    val bestWeekLabel = envelope?.analytics?.bestWeekLabel ?: "N/A"
    val dailyRate7d = envelope?.analytics?.dailyRate7d ?: 0.0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(12.dp)) }

        item {
            Column {
                Text(
                    text = "Analytics",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = "Insights and breakdowns from your activity",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── 1. Period Selector Chips ──────────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Period.LIFETIME to "Lifetime",
                        Period.MONTHLY to "Monthly",
                        Period.WEEKLY to "Weekly"
                    ).forEach { (p, label) ->
                        FilterChip(
                            selected = period == p,
                            onClick = { period = p },
                            label = { Text(label, fontSize = 12.sp, fontWeight = if (period == p) FontWeight.Bold else FontWeight.Normal) },
                            shape = RoundedCornerShape(20.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = period == Period.CUSTOM,
                        onClick = { showPicker = true },
                        label = {
                            if (period == Period.CUSTOM && customStart != null && customEnd != null)
                                Text("${customStart!!.format(displayFmt)} – ${customEnd!!.format(displayFmt)}", fontSize = 12.sp)
                            else Text("Custom Date Range", fontSize = 12.sp)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.DateRange, contentDescription = "Select date range", modifier = Modifier.size(16.dp))
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    if (period == Period.CUSTOM) {
                        IconButton(
                            onClick = { period = Period.LIFETIME; customStart = null; customEnd = null },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear custom date range", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // ── 2. Summary Metric Boxes (Earnings, Spending, Net) ───────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatBox(
                    label = "Total Earnings",
                    value = "+${totalEarnings.formatCoins()}",
                    color = MaterialTheme.colorScheme.income,
                    modifier = Modifier.weight(1f)
                )
                StatBox(
                    label = "Total Spending",
                    value = "-${totalSpending.formatCoins()}",
                    color = MaterialTheme.colorScheme.expense,
                    modifier = Modifier.weight(1f)
                )
                StatBox(
                    label = "Net Balance",
                    value = "${if (net >= 0) "+" else ""}${net.formatCoins()}",
                    color = if (net >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.expense,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── 3. Balance Timeline Chart (Canvas with Gold Accent & Gradient Fill) ─
        item {
            Column {
                Text(
                    text = "Balance over time",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Spacer(Modifier.height(8.dp))

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (filtered.isNotEmpty()) {
                        val brandGold = MaterialTheme.colorScheme.primary
                        val subtleGrid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 20.dp)
                        ) {
                            val sortedTx = filtered.sortedBy { it.date }
                            val points = sortedTx.runningFold(0f) { sum, tx -> sum + tx.amount.toFloat() }
                            val max = points.maxOrNull() ?: 1f
                            val min = points.minOrNull() ?: 0f
                            val range = (max - min).coerceAtLeast(1f)

                            val stepX = size.width / (points.size - 1).coerceAtLeast(1)
                            val padBottom = 8f

                            // Draw subtle horizontal grid lines
                            val gridLines = 3
                            for (g in 0..gridLines) {
                                val gy = size.height * (g / gridLines.toFloat())
                                drawLine(
                                    color = subtleGrid,
                                    start = Offset(0f, gy),
                                    end = Offset(size.width, gy),
                                    strokeWidth = 1f
                                )
                            }

                            // Compute curve points
                            val graphPoints = points.mapIndexed { i, p ->
                                val x = i * stepX
                                val y = size.height - padBottom - ((p - min) / range * (size.height - padBottom * 2))
                                Offset(x, y)
                            }

                            // Fill path under graph
                            val fillPath = Path().apply {
                                moveTo(graphPoints.first().x, size.height)
                                graphPoints.forEach { lineTo(it.x, it.y) }
                                lineTo(graphPoints.last().x, size.height)
                                close()
                            }

                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(brandGold.copy(alpha = 0.28f), Color.Transparent),
                                    startY = 0f,
                                    endY = size.height
                                )
                            )

                            // Stroke line
                            val linePath = Path().apply {
                                moveTo(graphPoints.first().x, graphPoints.first().y)
                                for (i in 1 until graphPoints.size) {
                                    lineTo(graphPoints[i].x, graphPoints[i].y)
                                }
                            }

                            drawPath(
                                path = linePath,
                                color = brandGold,
                                style = Stroke(
                                    width = 3.5.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )

                            // Draw dot at latest point
                            val lastPt = graphPoints.last()
                            drawCircle(color = brandGold, radius = 5.dp.toPx(), center = lastPt)
                            drawCircle(color = Color.White, radius = 2.dp.toPx(), center = lastPt)
                        }
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No data for this period", style = MaterialTheme.typography.bodyMedium, color = textColor)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Add transactions to see analytics",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 4. Earnings Breakdown (Doughnut Chart) ─────────────────────────────
        item {
            Column {
                Text(
                    text = "Earnings by source",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Spacer(Modifier.height(8.dp))

                if (earningsBreakdown.isNotEmpty()) {
                    DoughnutChartWithLegend(data = earningsBreakdown, textColor = textColor)
                } else {
                    GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            Text("No earnings for this period", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // ── 5. Spending Breakdown (Doughnut Chart) ─────────────────────────────
        item {
            Column {
                Text(
                    text = "Spending by category",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Spacer(Modifier.height(8.dp))

                if (spendingBreakdown.isNotEmpty()) {
                    DoughnutChartWithLegend(data = spendingBreakdown, textColor = textColor)
                } else {
                    GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            Text("No spending for this period", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // ── 6. 7-Day Earning Rate Card ────────────────────────────────────────
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "7-Day Earning Rate",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${dailyRate7d.toInt().formatCoins()} coins/day",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Last 7 days total",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "+${(dailyRate7d * 7).toInt().formatCoins()} coins",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.income
                        )
                    }
                }
            }
        }

        // ── 7. Best Earning Week Card ──────────────────────────────────────────
        if (bestWeekEarnings > 0) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Best Earning Week",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = bestWeekLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.EmojiEvents,
                                contentDescription = "Best week",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "+${bestWeekEarnings.formatCoins()} coins",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun StatBox(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = color
            )
        }
    }
}

@Composable
fun DoughnutChartWithLegend(data: Map<String, Int>, textColor: Color) {
    GlassCard(shape = RoundedCornerShape(14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val total = data.values.sum().toFloat()

            // 5 Token Palette colors matching design-tokens.json
            val palette = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.income,
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.expense
            )

            // Doughnut chart via drawArc with stroke
            Canvas(modifier = Modifier.size(110.dp)) {
                var startAngle = -90f
                val strokeW = 16.dp.toPx()
                data.values.forEachIndexed { index, value ->
                    val sweep = if (total > 0) (value / total) * 360f else 0f
                    drawArc(
                        color = palette[index % palette.size],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = strokeW)
                    )
                    startAngle += sweep
                }
            }

            Spacer(Modifier.width(20.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                data.entries.take(5).forEachIndexed { index, entry ->
                    val pct = if (total > 0) ((entry.value / total) * 100).toInt() else 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(palette[index % palette.size])
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = entry.key,
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = "${entry.value.formatCoins()} ($pct%)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun Int.formatCoins(): String =
    toString().reversed().chunked(3).joinToString(",").reversed()
