package com.cointracker.mobile.domain

import com.cointracker.mobile.data.Achievement
import com.cointracker.mobile.data.Transaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class AchievementCalculator {
    fun calculate(transactions: List<Transaction>, balance: Int, goal: Int): List<Achievement> {
        val achievements = mutableListOf<Achievement>()
        val today = LocalDate.now(ZoneOffset.UTC)

        // Balance milestones
        if (balance >= 1000) achievements += Achievement("💰", "Getting Started", "Reach a balance of 1,000 coins")
        if (balance >= 5000) achievements += Achievement("📈", "Serious Saver", "Reach a balance of 5,000 coins")
        if (balance >= 10000) achievements += Achievement("🏦", "Coin Hoarder", "Reach a balance of 10,000 coins")
        if (balance >= 25000) achievements += Achievement("👑", "Master Hoarder", "Reach a balance of 25,000 coins")
        if (goal > 0 && balance >= goal) achievements += Achievement("👑", "Epic Box Secured!", "Reach your goal balance")

        // Transaction stats
        val incomes = transactions.filter { it.amount > 0 }
        val incomeCount = incomes.size
        val txnCount = transactions.size
        val maxSingleIncome = if (incomes.isNotEmpty()) incomes.maxOf { it.amount } else 0

        if (incomeCount >= 1) achievements += Achievement("💵", "First Payday", "Record your first earning transaction")
        if (txnCount >= 25) achievements += Achievement("🎯", "Active Tracker", "Record 25 total transactions")
        if (txnCount >= 100) achievements += Achievement("⚡", "Century Club", "Record 100 total transactions")
        if (maxSingleIncome >= 1000) achievements += Achievement("🏆", "High Roller", "Earn 1,000+ coins in a single transaction")

        // Spending discipline
        val spends = transactions.filter { it.amount < 0 }
            .mapNotNull { runCatching { Instant.parse(it.date).atZone(ZoneOffset.UTC).toLocalDate() }.getOrNull() }
        val lastSpendDate = spends.maxOrNull()
        val daysSinceSpend = if (lastSpendDate != null) (today.toEpochDay() - lastSpendDate.toEpochDay()).toInt() else -1
        if (daysSinceSpend >= 7) achievements += Achievement("🛡", "Disciplined", "No spending for 7+ days")

        // Login streaks (max consecutive days)
        val loginDates = transactions
            .filter { it.amount > 0 && it.source.equals("login", ignoreCase = true) }
            .mapNotNull { runCatching { Instant.parse(it.date).atZone(ZoneOffset.UTC).toLocalDate() }.getOrNull() }
            .distinct()
            .sorted()

        var currentStreak = 0
        var maxStreak = 0
        var prevDate: LocalDate? = null

        for (d in loginDates) {
            if (prevDate != null) {
                val diff = d.toEpochDay() - prevDate.toEpochDay()
                currentStreak = if (diff == 1L) currentStreak + 1 else 1
            } else {
                currentStreak = 1
            }
            if (currentStreak > maxStreak) maxStreak = currentStreak
            prevDate = d
        }

        if (maxStreak >= 3) achievements += Achievement("🔥", "3-Day Login Streak", "3 consecutive Login days")
        if (maxStreak >= 7) achievements += Achievement("🔥", "7-Day Login Streak", "7 consecutive Login days")
        if (maxStreak >= 14) achievements += Achievement("🔥", "14-Day Login Streak", "14 consecutive Login days")

        return achievements
    }
}
