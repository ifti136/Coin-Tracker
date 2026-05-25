package com.cointracker.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cointracker.mobile.data.Achievement
import com.cointracker.mobile.data.ProfileEnvelope
import com.cointracker.mobile.ui.components.GlassCard
import com.cointracker.mobile.ui.theme.WebSuccess
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// ── Alert model ───────────────────────────────────────────────────────────────

private data class Alert(
    val icon      : ImageVector,
    val iconTint  : Color,
    val title     : String,
    val body      : String,
    val timestamp : String
)

private fun buildAlerts(envelope: ProfileEnvelope): List<Alert> {
    val alerts = mutableListOf<Alert>()
    val today  = LocalDate.now(ZoneOffset.UTC)
    val fmt    = DateTimeFormatter.ofPattern("MMM d")
    val pct    = envelope.progress

    if (pct >= 100) alerts += Alert(Icons.Default.Star, Color(0xFFF59E0B),
        "🎉 Goal Reached!", "You hit ${envelope.goal.formatCoins()} coins!", "Now")
    else if (pct >= 75) alerts += Alert(Icons.Default.ArrowUpward, WebSuccess,
        "75% milestone", "Crossed 75% of your ${envelope.goal.formatCoins()} goal.", "")
    else if (pct >= 50) alerts += Alert(Icons.Default.ArrowUpward, Color(0xFF3B82F6),
        "Halfway there!", "50% of goal reached. Keep going!", "")

    val todayTotal = envelope.transactions.filter { tx ->
        runCatching { Instant.parse(tx.date).atZone(ZoneOffset.UTC).toLocalDate() == today }
            .getOrElse { false } && tx.amount > 0
    }.sumOf { it.amount }
    if (todayTotal >= 300) alerts += Alert(Icons.Default.Star, Color(0xFFF59E0B),
        "Great day! +${todayTotal.formatCoins()} coins", "Today's earnings looking strong.", today.format(fmt))

    if (envelope.analytics.bestWeekEarnings > 0)
        alerts += Alert(Icons.Default.ArrowUpward, Color(0xFFF59E0B),
            "Best week: ${envelope.analytics.bestWeekLabel}",
            "+${envelope.analytics.bestWeekEarnings.formatCoins()} coins that week.", "")

    val est = envelope.estimatedDays
    if (est != null && est in 1..7)
        alerts += Alert(Icons.Default.Notifications, WebSuccess,
            "Goal within reach!", "~$est day${if (est == 1) "" else "s"} away at current rate.", "")

    return alerts
}

private fun Int.formatCoins(): String =
    toString().reversed().chunked(3).joinToString(",").reversed()

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun NotificationsScreen(
    envelope  : ProfileEnvelope?,
    onReadAll : () -> Unit,
    onBack    : () -> Unit
) {
    val textColor    = MaterialTheme.colorScheme.onSurface
    val achievements = envelope?.achievements ?: emptyList()
    val alerts       = if (envelope != null) buildAlerts(envelope) else emptyList()
    val hasContent   = achievements.isNotEmpty() || alerts.isNotEmpty()

    // Mark all as seen when screen is opened
    LaunchedEffect(Unit) { onReadAll() }

    LazyColumn(
        modifier                = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement     = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        // ── Top bar row: back + title + Read All ──────────────────────────────
        item {
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector        = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint               = textColor
                    )
                }
                Text(
                    "Notifications",
                    style     = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color      = textColor,
                    modifier   = Modifier.weight(1f)
                )
                if (hasContent) {
                    TextButton(onClick = onReadAll) {
                        Text(
                            "Read All",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // ── Empty state ───────────────────────────────────────────────────────
        if (!hasContent) {
            item {
                Box(
                    modifier          = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                    contentAlignment  = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔔", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("All clear!", style = MaterialTheme.typography.titleMedium, color = textColor)
                        Text(
                            "Add transactions to unlock achievements.",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        // ── Activity alerts ───────────────────────────────────────────────────
        if (alerts.isNotEmpty()) {
            item {
                Text(
                    "Activity Alerts",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary
                )
            }
            items(alerts) { alert ->
                GlassCard {
                    Row(
                        modifier          = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(alert.icon, null, tint = alert.iconTint, modifier = Modifier.size(28.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(alert.title, fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium, color = textColor)
                            Text(alert.body, style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.6f))
                        }
                        if (alert.timestamp.isNotBlank()) {
                            Text(alert.timestamp, fontSize = 11.sp, color = textColor.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }

        // ── Achievements ──────────────────────────────────────────────────────
        if (achievements.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Achievements Unlocked",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF59E0B)
                        )
                        Text(
                            "${achievements.size} achievement${if (achievements.size == 1) "" else "s"} earned",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            items(achievements) { ach -> AchievementCard(ach, textColor) }
        } else {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Achievements",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF59E0B)
                )
            }
            item {
                GlassCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🏆", fontSize = 32.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No achievements yet", style = MaterialTheme.typography.bodyMedium,
                            color = textColor.copy(alpha = 0.5f))
                        Text("Reach 1,000 coins to earn your first one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.4f))
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── Achievement card ──────────────────────────────────────────────────────────

@Composable
private fun AchievementCard(ach: Achievement, textColor: Color) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape    = MaterialTheme.shapes.medium,
                color    = Color(0xFFF59E0B).copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(ach.icon, fontSize = 24.sp)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(ach.name, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge, color = textColor)
                Text(ach.desc, style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.6f))
            }
            Icon(Icons.Default.CheckCircle, "Unlocked",
                tint = WebSuccess, modifier = Modifier.size(20.dp))
        }
    }
}
