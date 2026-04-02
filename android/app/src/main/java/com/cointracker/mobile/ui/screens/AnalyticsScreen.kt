package com.cointracker.mobile.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cointracker.mobile.data.ProfileEnvelope
import com.cointracker.mobile.ui.components.GlassCard

@Composable
fun AnalyticsScreen(envelope: ProfileEnvelope?) {
    val earnings = envelope?.analytics?.earningsBreakdown ?: emptyMap()
    val spending = envelope?.analytics?.spendingBreakdown ?: emptyMap()
    val textColor = MaterialTheme.colorScheme.onSurface
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Spacer(Modifier.height(16.dp)); Text("Analytics", style = MaterialTheme.typography.headlineMedium, color = textColor) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatBox("Earnings", "+${envelope?.analytics?.totalEarnings ?: 0}", Color(0xFF10B981))
                StatBox("Spending", "${envelope?.analytics?.totalSpending ?: 0}", Color(0xFFEF4444))
                StatBox("Net", "${envelope?.analytics?.netBalance ?: 0}", Color(0xFF3B82F6))
            }
        }
        item {
            Text("Balance Timeline", style = MaterialTheme.typography.titleMedium, color = textColor)
            GlassCard(modifier = Modifier.height(200.dp)) {
                val txns = envelope?.transactions ?: emptyList()
                if (txns.isNotEmpty()) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        val points = txns.sortedBy { it.date }.runningFold(0f) { sum, tx -> sum + tx.amount.toFloat() }
                        val max = points.maxOrNull() ?: 1f; val min = points.minOrNull() ?: 0f
                        val range = (max - min).coerceAtLeast(1f)
                        val widthPerPoint = size.width / (points.size - 1).coerceAtLeast(1)
                        for (i in 0 until points.size - 1) {
                            drawLine(Color(0xFF3B82F6), Offset(i * widthPerPoint, size.height - ((points[i] - min) / range * size.height)),
                                Offset((i + 1) * widthPerPoint, size.height - ((points[i + 1] - min) / range * size.height)), strokeWidth = 5f)
                        }
                    }
                } else { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No Data", color = textColor) } }
            }
        }
        item {
            Text("Earnings Breakdown", style = MaterialTheme.typography.titleMedium, color = textColor)
            if (earnings.isNotEmpty()) PieChartWithLegend(data = earnings, textColor = textColor)
            else Text("No earnings data", color = Color.Gray)
        }
        item {
            Text("Spending Breakdown", style = MaterialTheme.typography.titleMedium, color = textColor)
            if (spending.isNotEmpty()) PieChartWithLegend(data = spending, textColor = textColor)
            else Text("No spending data", color = Color.Gray)
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun StatBox(label: String, value: String, color: Color) {
    GlassCard(modifier = Modifier.width(100.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Text(value, style = MaterialTheme.typography.titleMedium, color = color)
        }
    }
}

@Composable
fun PieChartWithLegend(data: Map<String, Int>, textColor: Color) {
    GlassCard {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val total = data.values.sum().toFloat()
            val colors = listOf(Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFF8B5CF6))
            Canvas(modifier = Modifier.size(100.dp)) {
                var startAngle = -90f
                data.values.forEachIndexed { index, value ->
                    val sweepAngle = (value / total) * 360f
                    drawArc(color = colors[index % colors.size], startAngle = startAngle, sweepAngle = sweepAngle, useCenter = true)
                    startAngle += sweepAngle
                }
            }
            Spacer(Modifier.width(24.dp))
            Column {
                data.entries.forEachIndexed { index, entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.size(12.dp), color = colors[index % colors.size], shape = MaterialTheme.shapes.small) {}
                        Spacer(Modifier.width(8.dp))
                        Text("${entry.key}: ${entry.value}", style = MaterialTheme.typography.bodySmall, color = textColor)
                    }
                }
            }
        }
    }
}
