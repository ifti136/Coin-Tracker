package com.cointracker.mobile.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

data class Transaction(
    val id: String = "",
    val date: String = Instant.now().toString(),
    val amount: Int = 0,
    val source: String = "",
    val previousBalance: Int = 0
)

data class Settings(
    val goal: Int = 13500,
    val darkMode: Boolean = false,
    val quickActions: List<QuickAction> = defaultQuickActions(),
    val firebaseAvailable: Boolean = true,
    val allSources: List<String> = emptyList(),
    // Custom category lists — fall back to hardcoded defaults if empty
    val incomeCategories: List<String> = emptyList(),
    val expenseCategories: List<String> = emptyList()
)

data class QuickAction(
    val text: String = "",
    val value: Int = 0,
    val isPositive: Boolean = true
)

data class DashboardStats(
    val today: Int = 0,
    val week: Int = 0,
    val month: Int = 0
)

data class AnalyticsSnapshot(
    val totalEarnings: Int = 0,
    val totalSpending: Int = 0,
    val netBalance: Int = 0,
    val earningsBreakdown: Map<String, Int> = emptyMap(),
    val spendingBreakdown: Map<String, Int> = emptyMap(),
    val timeline: List<TimelinePoint> = emptyList()
)

data class TimelinePoint(val date: String, val balance: Int)

data class Achievement(val icon: String, val name: String, val desc: String)

data class ProfileEnvelope(
    val profile: String,
    val transactions: List<Transaction>,
    val settings: Settings,
    val balance: Int,
    val goal: Int,
    val progress: Int,
    val estimatedDays: Int?,
    val dashboardStats: DashboardStats,
    val analytics: AnalyticsSnapshot,
    val achievements: List<Achievement>
)

data class UserSession(
    val userId: String,
    val username: String,
    val role: String,
    val currentProfile: String
)

data class AdminStats(
    val totalUsers: Int,
    val totalCoins: Int,
    val totalTransactions: Int,
    val labels: List<String>,
    val newUsersData: List<Int>
)

data class AdminUserRow(
    val userId: String,
    val username: String,
    val balance: Int,
    val txnCount: Int,
    val createdAt: String,
    val lastUpdated: String
)

// ---- Hardcoded fallback category lists ----

val DEFAULT_INCOME_CATEGORIES = listOf(
    "Event Reward", "Login", "Daily Games", "Campaign Reward",
    "Ads", "Achievements", "Other"
)

val DEFAULT_EXPENSE_CATEGORIES = listOf(
    "Box Draw", "Manager Purchase", "Pack Purchase", "Store Purchase", "Other"
)

fun defaultQuickActions(): List<QuickAction> = listOf(
    QuickAction("Event Reward", 50, true),
    QuickAction("Ads", 10, true),
    QuickAction("Daily Games", 100, true),
    QuickAction("Login", 50, true),
    QuickAction("Campaign Reward", 50, true),
    QuickAction("Box Draw (Single)", 100, false),
    QuickAction("Box Draw (10)", 900, false)
)

fun defaultSettings(): Settings = Settings()

fun LocalDate.toIsoString(): String =
    this.atStartOfDay().toInstant(ZoneOffset.UTC).toString()

/** Returns the effective income category list — custom if set, otherwise hardcoded fallback. */
fun Settings.effectiveIncomeCategories(): List<String> =
    if (incomeCategories.isNotEmpty()) incomeCategories else DEFAULT_INCOME_CATEGORIES

/** Returns the effective expense category list — custom if set, otherwise hardcoded fallback. */
fun Settings.effectiveExpenseCategories(): List<String> =
    if (expenseCategories.isNotEmpty()) expenseCategories else DEFAULT_EXPENSE_CATEGORIES
