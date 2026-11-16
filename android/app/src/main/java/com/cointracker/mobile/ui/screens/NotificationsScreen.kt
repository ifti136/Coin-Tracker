package com.cointracker.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrackChanges
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
import com.cointracker.mobile.ui.theme.brand
import com.cointracker.mobile.ui.theme.expense
import com.cointracker.mobile.ui.theme.income
import com.cointracker.mobile.ui.theme.incomeContainer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Alert model

private data class Alert(
    val icon: ImageVector,
    val iconTint: Color,
    val title: String,
    val body: String,
    val timestamp: String
)

@Composable
private fun buildAlerts(envelope: ProfileEnvelope): List<Alert> {
    val alerts = mutableListOf<Alert>()
    val today = LocalDate.now(ZoneOffset.UTC)
    val fmt = DateTimeFormatter.ofPattern("MMM d")
    val pct = envelope.progress

    if (pct >= 100) alerts += Alert(
        Icons.Default.EmojiEvents,
        MaterialTheme.colorScheme.brand,
        "Goal Reached!",
        "You hit ${envelope.goal.formatCoins()} coins!",
        "Now"
    )
    else if (pct >= 75) alerts += Alert(
        Icons.Default.ArrowUpward,
        MaterialTheme.colorScheme.primary,
        "75% milestone",
        "Crossed 75% of your ${envelope.goal.formatCoins()} goal.",
        ""
    )
    else if (pct >= 50) alerts += Alert(
        Icons.Default.ArrowUpward,
        MaterialTheme.colorScheme.primary,
        "Halfway there!",
        "50% of goal reached. Keep going!",
        ""
    )

    val todayTotal = envelope.transactions.filter { tx ->
        runCatching { Instant.parse(tx.date).atZone(ZoneOffset.UTC).toLocalDate() == today }
            .getOrElse { false } && tx.amount > 0
    }.sumOf { it.amount }
    if (todayTotal >= 300) alerts += Alert(
        Icons.Default.EmojiEvents,
        MaterialTheme.colorScheme.income,
        "Great day! +${todayTotal.formatCoins()} coins",
        "Today's earnings looking strong.",
        today.format(fmt)
    )

    if (envelope.analytics.bestWeekEarnings > 0)
        alerts += Alert(
            Icons.Default.ArrowUpward,
            MaterialTheme.colorScheme.brand,
            "Best week: ${envelope.analytics.bestWeekLabel}",
            "+${envelope.analytics.bestWeekEarnings.formatCoins()} coins that week.",
            ""
        )

    val est = envelope.estimatedDays
    if (est != null && est in 1..7)
        alerts += Alert(
            Icons.Default.Notifications,
            MaterialTheme.colorScheme.primary,
            "Goal within reach!",
            "~$est day${if (est == 1) "" else "s"} away at current rate.",
            ""
        )

    return alerts
}

private fun Int.formatCoins(): String =
    toString().reversed().chunked(3).joinToString(",").reversed()

// Map achievement emoji icons to Material icons
private fun achievementIcon(emoji: String): ImageVector = when (emoji) {
    "💰" -> Icons.Default.Savings
    "📈" -> Icons.AutoMirrored.Filled.TrendingUp
    "🏦" -> Icons.Default.AccountBalance
    "👑" -> Icons.Default.EmojiEvents
    "🔥" -> Icons.Default.LocalFireDepartment
    "🛡" -> Icons.Default.Shield
    "💵" -> Icons.Default.AttachMoney
    "🎯" -> Icons.Default.TrackChanges
    "⚡" -> Icons.Default.Bolt
    "🏆" -> Icons.Default.EmojiEvents
    else -> Icons.Default.EmojiEvents
}

private fun achievementContentDescription(emoji: String): String = when (emoji) {
    "💰" -> "Getting started achievement"
    "📈" -> "Serious saver achievement"
    "🏦" -> "Coin hoarder achievement"
    "👑" -> "Master hoarder or goal reached achievement"
    "🔥" -> "Login streak achievement"
    "🛡" -> "Disciplined achievement"
    "💵" -> "First payday achievement"
    "🎯" -> "Active tracker achievement"
    "⚡" -> "Century club achievement"
    "🏆" -> "High roller achievement"
    else -> "Achievement unlocked"
}

// Screen

@Composable
fun NotificationsScreen(
    envelope: ProfileEnvelope?,
    isActionLoading: (String) -> Boolean,
    onReadAll: () -> Unit,
    onBack: () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val achievements = envelope?.achievements ?: emptyList()
    val alerts = if (envelope != null) buildAlerts(envelope) else emptyList()
    val hasContent = achievements.isNotEmpty() || alerts.isNotEmpty()

    // Mark all as seen when screen is opened
    LaunchedEffect(Unit) { onReadAll() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        // Top bar row: back + title + Read All
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = textColor
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Notifications",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Text(
                        "Alerts, milestones, and achievements",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (hasContent) {
                    val isReading = isActionLoading("markNotificationsSeen")
                    FilledTonalButton(
                        onClick = onReadAll,
                        enabled = !isReading,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isReading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                "Read All",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Empty state
        if (!hasContent) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.NotificationsOff,
                            contentDescription = "No notifications",
                            modifier = Modifier.size(48.dp),
                            tint = textColor.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "All clear!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Add transactions to unlock achievements and milestone alerts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        // Activity alerts
        if (alerts.isNotEmpty()) {
            item {
                Text(
                    "Activity Alerts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(alerts) { alert ->
                GlassCard(shape = RoundedCornerShape(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = alert.iconTint.copy(alpha = 0.12f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = alert.icon,
                                    contentDescription = alert.title,
                                    tint = alert.iconTint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = alert.title,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = alert.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.65f)
                            )
                        }
                        if (alert.timestamp.isNotBlank()) {
                            Text(
                                text = alert.timestamp,
                                fontSize = 11.sp,
                                color = textColor.copy(alpha = 0.45f)
                            )
                        }
                    }
                }
            }
        }

        // Achievements
        if (achievements.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Achievements Unlocked",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.brand
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
                    color = MaterialTheme.colorScheme.brand
                )
            }
            item {
                GlassCard(shape = RoundedCornerShape(14.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = "No achievements unlocked",
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.brand
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No achievements yet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Reach 1,000 coins to earn your first one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// Achievement card

@Composable
private fun AchievementCard(ach: Achievement, textColor: Color) {
    GlassCard(shape = RoundedCornerShape(14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.brand.copy(alpha = 0.12f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = achievementIcon(ach.icon),
                        contentDescription = achievementContentDescription(ach.icon),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.brand
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ach.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = ach.desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.65f)
                )
            }
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Achievement unlocked",
                tint = MaterialTheme.colorScheme.income,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
