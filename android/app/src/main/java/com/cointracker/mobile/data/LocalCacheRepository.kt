package com.cointracker.mobile.data

import android.content.Context
import com.cointracker.mobile.domain.AchievementCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

// ── Cached profile snapshot ───────────────────────────────────────────────────

data class CachedProfile(
    val profile          : String,
    val savedAt          : Long,               // epoch millis — when we wrote this cache
    val balance          : Int,                // for quick conflict detection
    val transactionCount : Int,
    val transactions     : List<Transaction>,
    val settings         : Settings
)

// ── Repository ────────────────────────────────────────────────────────────────

@Singleton
class LocalCacheRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // Stored at: filesDir/safety_cache/{userId}/{profile}.json
    private fun cacheDir(userId: String): File =
        File(context.filesDir, "safety_cache/$userId").also { it.mkdirs() }

    private fun cacheFile(userId: String, profile: String): File =
        File(cacheDir(userId), "${sanitize(profile)}.json")

    private fun sanitize(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")

    // ── Public API ────────────────────────────────────────────────────────────

    fun save(userId: String, profile: String, envelope: ProfileEnvelope) {
        runCatching {
            val json = serialize(envelope)
            cacheFile(userId, profile).writeText(json)
        }
    }

    fun load(userId: String, profile: String): CachedProfile? =
        runCatching {
            val file = cacheFile(userId, profile)
            if (!file.exists()) return null
            deserialize(file.readText())
        }.getOrNull()

    fun delete(userId: String, profile: String) {
        runCatching { cacheFile(userId, profile).delete() }
    }

    fun listProfiles(userId: String): List<String> =
        runCatching {
            cacheDir(userId).listFiles()
                ?.filter { it.extension == "json" }
                ?.map { it.nameWithoutExtension }
                ?: emptyList()
        }.getOrDefault(emptyList())

    /** Build a usable offline ProfileEnvelope from cached data. */
    fun buildOfflineEnvelope(cached: CachedProfile): ProfileEnvelope {
        val txns    = cached.transactions
        val balance = txns.sumOf { it.amount }
        val goal    = cached.settings.goal
        val pct     = if (goal > 0) minOf(100, ((balance.toDouble() / goal) * 100).toInt()) else 0
        val achievements = AchievementCalculator().calculate(txns, balance, goal)
        return ProfileEnvelope(
            profile          = cached.profile,
            transactions     = txns,
            settings         = cached.settings.copy(firebaseAvailable = false),
            balance          = balance,
            goal             = goal,
            progress         = pct,
            estimatedDays    = null,
            dashboardStats   = DashboardStats(0, 0, 0),
            analytics        = AnalyticsSnapshot(),
            achievements     = achievements
        )
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    private fun serialize(envelope: ProfileEnvelope): String {
        val root = JSONObject()
        root.put("profile",          envelope.profile)
        root.put("savedAt",          Instant.now().toEpochMilli())
        root.put("balance",          envelope.balance)
        root.put("transactionCount", envelope.transactions.size)

        // Transactions
        val txArr = JSONArray()
        envelope.transactions.forEach { tx ->
            txArr.put(JSONObject().apply {
                put("id",               tx.id)
                put("date",             tx.date)
                put("amount",           tx.amount)
                put("source",           tx.source)
                put("previousBalance",  tx.previousBalance)
            })
        }
        root.put("transactions", txArr)

        // Settings
        val s = envelope.settings
        val settingsObj = JSONObject().apply {
            put("goal",     s.goal)
            put("darkMode", s.darkMode)

            val qaArr = JSONArray()
            s.quickActions.forEach { qa ->
                qaArr.put(JSONObject().apply {
                    put("text",       qa.text)
                    put("value",      qa.value)
                    put("isPositive", qa.isPositive)
                })
            }
            put("quickActions", qaArr)

            val incArr = JSONArray(); s.incomeCategories.forEach { incArr.put(it) }
            put("incomeCategories", incArr)

            val expArr = JSONArray(); s.expenseCategories.forEach { expArr.put(it) }
            put("expenseCategories", expArr)
        }
        root.put("settings", settingsObj)

        return root.toString(2)
    }

    private fun deserialize(json: String): CachedProfile {
        val root = JSONObject(json)
        val profile          = root.getString("profile")
        val savedAt          = root.getLong("savedAt")
        val balance          = root.getInt("balance")
        val transactionCount = root.getInt("transactionCount")

        // Transactions
        val txArr = root.getJSONArray("transactions")
        val transactions = (0 until txArr.length()).map { i ->
            val o = txArr.getJSONObject(i)
            Transaction(
                id              = o.getString("id"),
                date            = o.getString("date"),
                amount          = o.getInt("amount"),
                source          = o.getString("source"),
                previousBalance = o.optInt("previousBalance", 0)
            )
        }

        // Settings
        val so = root.getJSONObject("settings")
        val qaArr = so.optJSONArray("quickActions")
        val quickActions = if (qaArr != null) {
            (0 until qaArr.length()).mapNotNull { i ->
                val q = qaArr.optJSONObject(i) ?: return@mapNotNull null
                QuickAction(
                    text       = q.optString("text", ""),
                    value      = q.optInt("value", 0),
                    isPositive = q.optBoolean("isPositive", true)
                )
            }
        } else defaultQuickActions()

        fun jsonArrayToList(arr: JSONArray?): List<String> =
            if (arr == null) emptyList()
            else (0 until arr.length()).map { arr.getString(it) }

        val settings = Settings(
            goal              = so.optInt("goal", 13500),
            darkMode          = so.optBoolean("darkMode", false),
            quickActions      = quickActions,
            firebaseAvailable = false,
            incomeCategories  = jsonArrayToList(so.optJSONArray("incomeCategories")),
            expenseCategories = jsonArrayToList(so.optJSONArray("expenseCategories"))
        )

        return CachedProfile(
            profile          = profile,
            savedAt          = savedAt,
            balance          = balance,
            transactionCount = transactionCount,
            transactions     = transactions,
            settings         = settings
        )
    }
}
