package com.cointracker.mobile.data

import com.cointracker.mobile.domain.AchievementCalculator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreRepository @Inject constructor() {

    private val auth = FirebaseAuth.getInstance()
    private val client = OkHttpClient()

    // ⚠️ CONFIRM THIS IS YOUR CORRECT GOOGLE CLOUD IP
    private val BASE_URL = "http://34.19.86.210"

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val usersRef get() = db.collection("users")
    private val userDataRef get() = db.collection("user_data")

    // ----------------------------------------------------------------
    // AUTHENTICATION
    // ----------------------------------------------------------------

    suspend fun login(username: String, password: String): Result<UserSession> = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject().apply {
                put("username", username)
                put("password", password)
            }

            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL/api/mobile-login")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) throw Exception("Login failed: ${response.code}")

            val jsonResponse = JSONObject(responseBody)
            if (!jsonResponse.getBoolean("success")) {
                throw Exception(jsonResponse.optString("error", "Unknown login error"))
            }

            val token = jsonResponse.getString("token")
            auth.signInWithCustomToken(token).await()

            val userId = auth.currentUser!!.uid
            val userDoc = usersRef.document(userId).get().await()
            val role = userDoc.getString("role") ?: "user"
            val userDataDoc = userDataRef.document(userId).get().await()
            val lastProfile = userDataDoc.getString("last_active_profile") ?: "Default"

            UserSession(userId, username, role, lastProfile)
        }
    }

    suspend fun register(username: String, password: String): Result<UserSession> = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject().apply {
                put("username", username)
                put("password", password)
            }

            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL/api/mobile-register")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) throw Exception("Registration failed: ${response.code}")

            val jsonResponse = JSONObject(responseBody)
            if (!jsonResponse.getBoolean("success")) throw Exception(jsonResponse.getString("error"))

            val token = jsonResponse.getString("token")
            auth.signInWithCustomToken(token).await()

            UserSession(auth.currentUser!!.uid, username, "user", "Default")
        }
    }

    fun logout() {
        auth.signOut()
    }

    // ----------------------------------------------------------------
    // DATA OPERATIONS
    // ----------------------------------------------------------------

    suspend fun loadProfile(userId: String, profileName: String): Result<ProfileEnvelope> = runCatching {
        val (transactions, settings) = getData(userId, profileName)
        buildEnvelope(profileName, transactions, settings)
    }

    suspend fun listProfiles(session: UserSession): Result<List<String>> = runCatching {
        val doc = userDataRef.document(session.userId).get().await()
        val data = doc.data ?: return@runCatching listOf("Default")
        val profiles = (data["profiles"] as? Map<*, *>)?.keys?.map { it.toString() } ?: emptyList()
        if (profiles.isNotEmpty()) profiles.sorted() else listOf("Default")
    }

    suspend fun switchProfile(session: UserSession, profile: String): Result<UserSession> = runCatching {
        userDataRef.document(session.userId).update("last_active_profile", profile).await()
        session.copy(currentProfile = profile)
    }

    suspend fun createProfile(session: UserSession, profile: String): Result<List<String>> = runCatching {
        val doc = userDataRef.document(session.userId).get().await()
        val data = doc.data?.toMutableMap() ?: mutableMapOf()
        val profiles = (data["profiles"] as? Map<*, *>)?.toMutableMap() ?: mutableMapOf()
        if (profiles.containsKey(profile)) throw IllegalStateException("Profile already exists")

        val newProfile = mapOf(
            "transactions" to emptyList<Map<String, Any>>(),
            "settings" to settingsToMap(defaultSettings()),
            "last_updated" to nowIso()
        )
        profiles[profile] = newProfile
        data["profiles"] = profiles
        data["last_active_profile"] = profile

        userDataRef.document(session.userId).set(data).await()
        profiles.keys.map { it.toString() }
    }

    suspend fun deleteProfile(session: UserSession, profile: String): Result<List<String>> = runCatching {
        if (profile == "Default") throw IllegalStateException("Cannot delete Default profile")

        val doc = userDataRef.document(session.userId).get().await()
        val data = doc.data?.toMutableMap() ?: mutableMapOf()
        val profiles = (data["profiles"] as? Map<*, *>)?.toMutableMap() ?: mutableMapOf()

        if (!profiles.containsKey(profile)) throw IllegalStateException("Profile not found")
        profiles.remove(profile)

        data["profiles"] = profiles
        // Reset to default if current was deleted
        val nextProfile = "Default"
        data["last_active_profile"] = nextProfile

        userDataRef.document(session.userId).set(data).await()
        profiles.keys.map { it.toString() }
    }

    suspend fun deleteAllData(session: UserSession): Result<Unit> = runCatching {
        val emptyData = mapOf(
            "profiles" to mapOf(
                "Default" to mapOf(
                    "transactions" to emptyList<Map<String, Any>>(),
                    "settings" to settingsToMap(defaultSettings()),
                    "last_updated" to nowIso()
                )
            ),
            "last_active_profile" to "Default"
        )
        userDataRef.document(session.userId).set(emptyData).await()
    }

    suspend fun addTransaction(session: UserSession, amount: Int, source: String, dateIso: String?): Result<ProfileEnvelope> = runCatching {
        val profileName = session.currentProfile
        val (transactions, settings) = getData(session.userId, profileName)
        val tx = Transaction(
            id = UUID.randomUUID().toString(),
            date = dateIso ?: nowIso(),
            amount = amount,
            source = source,
            previousBalance = 0
        )
        val updated = recalcBalances(transactions + tx)
        saveProfile(session.userId, profileName, updated, settings)
        buildEnvelope(profileName, updated, settings)
    }

    suspend fun updateTransaction(session: UserSession, transactionId: String, amount: Int, source: String, dateIso: String): Result<ProfileEnvelope> = runCatching {
        val profileName = session.currentProfile
        val (transactions, settings) = getData(session.userId, profileName)
        val updatedList = transactions.map {
            if (it.id == transactionId) it.copy(amount = amount, source = source, date = dateIso) else it
        }
        val updated = recalcBalances(updatedList)
        saveProfile(session.userId, profileName, updated, settings)
        buildEnvelope(profileName, updated, settings)
    }

    suspend fun deleteTransaction(session: UserSession, transactionId: String): Result<ProfileEnvelope> = runCatching {
        val profileName = session.currentProfile
        val (transactions, settings) = getData(session.userId, profileName)
        val updatedList = transactions.filterNot { it.id == transactionId }
        val updated = recalcBalances(updatedList)
        saveProfile(session.userId, profileName, updated, settings)
        buildEnvelope(profileName, updated, settings)
    }

    suspend fun updateSettings(session: UserSession, updatedSettings: Settings): Result<ProfileEnvelope> = runCatching {
        val profileName = session.currentProfile
        val (transactions, _) = getData(session.userId, profileName)
        saveProfile(session.userId, profileName, transactions, updatedSettings)
        buildEnvelope(profileName, transactions, updatedSettings)
    }

    suspend fun addQuickAction(session: UserSession, action: QuickAction): Result<ProfileEnvelope> = runCatching {
        val profileName = session.currentProfile
        val (transactions, settings) = getData(session.userId, profileName)
        val newSettings = settings.copy(quickActions = settings.quickActions + action)
        saveProfile(session.userId, profileName, transactions, newSettings)
        buildEnvelope(profileName, transactions, newSettings)
    }

    suspend fun updateQuickAction(session: UserSession, index: Int, action: QuickAction): Result<ProfileEnvelope> = runCatching {
        val profileName = session.currentProfile
        val (transactions, settings) = getData(session.userId, profileName)
        if (index < 0 || index >= settings.quickActions.size) throw IndexOutOfBoundsException("Invalid quick action index")

        val updatedActions = settings.quickActions.toMutableList()
        updatedActions[index] = action

        val newSettings = settings.copy(quickActions = updatedActions)
        saveProfile(session.userId, profileName, transactions, newSettings)
        buildEnvelope(profileName, transactions, newSettings)
    }

    suspend fun deleteQuickAction(session: UserSession, index: Int): Result<ProfileEnvelope> = runCatching {
        val profileName = session.currentProfile
        val (transactions, settings) = getData(session.userId, profileName)
        if (index < 0 || index >= settings.quickActions.size) throw IndexOutOfBoundsException("Invalid quick action index")
        val newSettings = settings.copy(quickActions = settings.quickActions.filterIndexed { i, _ -> i != index })
        saveProfile(session.userId, profileName, transactions, newSettings)
        buildEnvelope(profileName, transactions, newSettings)
    }

    suspend fun importData(session: UserSession, transactions: List<Transaction>, settings: Settings): Result<ProfileEnvelope> = runCatching {
        val profileName = session.currentProfile
        val validatedTx = recalcBalances(transactions)
        saveProfile(session.userId, profileName, validatedTx, settings)
        buildEnvelope(profileName, validatedTx, settings)
    }

    // ----------------------------------------------------------------
    // ADMIN & HELPERS
    // ----------------------------------------------------------------

    suspend fun loadAdminStats(): Result<AdminStats> = runCatching {
        val usersSnapshot = usersRef.get().await()
        val totalUsers = usersSnapshot.size()

        var totalCoins = 0
        var totalTransactions = 0

        try {
            val userData = userDataRef.get().await()
            userData.documents.forEach { doc ->
                val payload = doc.data
                if (payload != null) {
                    val txns = extractAllTransactions(payload)
                    totalTransactions += txns.size
                    totalCoins += txns.sumOf { it.amount }
                }
            }
        } catch (e: Exception) {
            // Log error
        }

        val labels = listOf("Day 1", "Day 2", "Day 3")
        val data = listOf(1, 2, 3)

        AdminStats(totalUsers, totalCoins, totalTransactions, labels, data)
    }

    suspend fun loadAdminUsers(): Result<List<AdminUserRow>> = runCatching {
        val usersSnapshot = usersRef.get().await()
        val rows = mutableListOf<AdminUserRow>()

        val userDataMap = try {
            val snap = userDataRef.get().await()
            snap.documents.associate { it.id to it.data }
        } catch(e: Exception) { emptyMap() }

        usersSnapshot.documents.forEach { doc ->
            val uData = doc.data ?: return@forEach
            val userId = doc.id
            val pData = userDataMap[userId]

            var balance = 0
            var txCount = 0

            if (pData != null) {
                val txns = extractAllTransactions(pData)
                balance = txns.sumOf { it.amount }
                txCount = txns.size
            }

            rows.add(AdminUserRow(
                userId = userId,
                username = uData["username"] as? String ?: "N/A",
                balance = balance,
                txnCount = txCount,
                createdAt = uData["created_at"] as? String ?: "N/A",
                lastUpdated = nowIso()
            ))
        }

        rows
    }

    suspend fun deleteUser(userId: String): Result<Unit> = runCatching {
        usersRef.document(userId).delete().await()
        userDataRef.document(userId).delete().await()
    }

    private fun extractAllTransactions(payload: Map<String, Any>): List<Transaction> {
        val allTx = mutableListOf<Transaction>()
        val profiles = payload["profiles"] as? Map<*, *>
        if (profiles != null) {
            profiles.values.forEach { p ->
                val pMap = p as? Map<*, *> ?: return@forEach
                parseTransactions(pMap["transactions"])?.let { allTx.addAll(it) }
            }
        } else {
            parseTransactions(payload["transactions"])?.let { allTx.addAll(it) }
        }
        return allTx
    }

    // ----------------------------------------------------------------
    // PRIVATE HELPERS
    // ----------------------------------------------------------------

    private suspend fun getData(userId: String, profile: String): Pair<List<Transaction>, Settings> {
        val doc = userDataRef.document(userId).get().await()
        val data = doc.data ?: emptyMap<String, Any>()
        val profiles = data["profiles"] as? Map<*, *>

        if (profiles != null) {
            val profileData = profiles[profile] as? Map<*, *> ?: emptyMap<String, Any>()
            val transactions = parseTransactions(profileData["transactions"]) ?: emptyList()
            val settings = parseSettings(profileData["settings"]) ?: defaultSettings()
            return recalcBalances(transactions) to settings
        }
        val transactions = parseTransactions(data["transactions"]) ?: emptyList()
        val settings = parseSettings(data["settings"]) ?: defaultSettings()
        return recalcBalances(transactions) to settings
    }

    private suspend fun saveProfile(userId: String, profile: String, transactions: List<Transaction>, settings: Settings) {
        val doc = userDataRef.document(userId).get().await()
        val existing = doc.data?.toMutableMap() ?: mutableMapOf()
        val profiles = (existing["profiles"] as? Map<*, *>)?.toMutableMap() ?: mutableMapOf()

        profiles[profile] = mapOf(
            "transactions" to transactions.map { transactionToMap(it) },
            "settings" to settingsToMap(settings),
            "last_updated" to nowIso()
        )
        existing["profiles"] = profiles
        existing["last_active_profile"] = profile
        userDataRef.document(userId).set(existing).await()
    }

    private fun buildEnvelope(profileName: String, transactions: List<Transaction>, settings: Settings): ProfileEnvelope {
        val balance = transactions.sumOf { it.amount }
        val goal = settings.goal

        val today = LocalDate.now(ZoneOffset.UTC)
        val weekStart = today.minusDays(today.dayOfWeek.ordinal.toLong())
        val monthStart = today.withDayOfMonth(1)

        var todayEarn = 0
        var weekEarn = 0
        var monthEarn = 0
        var totalEarnings = 0
        var firstEarningDate: Instant? = null

        transactions.forEach { t ->
            if (t.amount > 0) {
                totalEarnings += t.amount
                val tInstant = runCatching { Instant.parse(t.date) }.getOrNull()
                if (tInstant != null) {
                    if (firstEarningDate == null || tInstant.isBefore(firstEarningDate)) {
                        firstEarningDate = tInstant
                    }
                    val d = tInstant.atZone(ZoneOffset.UTC).toLocalDate()
                    if (d == today) todayEarn += t.amount
                    if (!d.isBefore(weekStart)) weekEarn += t.amount
                    if (!d.isBefore(monthStart)) monthEarn += t.amount
                }
            }
        }

        val estimatedDays: Int? = if (totalEarnings > 0 && firstEarningDate != null) {
            val daysSinceStart = maxOf(1, (Instant.now().epochSecond - firstEarningDate!!.epochSecond).div(86_400).toInt())
            val avgDaily = totalEarnings / daysSinceStart.toDouble()
            val remaining = goal - balance
            when {
                remaining <= 0 -> 0
                avgDaily > 0 -> (remaining / avgDaily).toInt()
                else -> null
            }
        } else null

        val earningsBreakdown = mutableMapOf<String, Int>()
        val spendingBreakdown = mutableMapOf<String, Int>()
        transactions.forEach { t ->
            if (t.amount > 0) earningsBreakdown[t.source] = (earningsBreakdown[t.source] ?: 0) + t.amount
            if (t.amount < 0) spendingBreakdown[t.source] = (spendingBreakdown[t.source] ?: 0) + -t.amount
        }

        val timeline = transactions.sortedBy { it.date }.map { t ->
            TimelinePoint(t.date, t.previousBalance + t.amount)
        }

        val achievements = AchievementCalculator().calculate(transactions, balance, goal)

        return ProfileEnvelope(
            profile = profileName,
            transactions = transactions,
            settings = settings.copy(firebaseAvailable = true),
            balance = balance,
            goal = goal,
            progress = if (goal > 0) minOf(100, ((balance.toDouble() / goal) * 100).toInt()) else 0,
            estimatedDays = estimatedDays,
            dashboardStats = DashboardStats(todayEarn, weekEarn, monthEarn),
            analytics = AnalyticsSnapshot(
                totalEarnings = totalEarnings,
                totalSpending = -transactions.filter { it.amount < 0 }.sumOf { it.amount },
                netBalance = balance,
                earningsBreakdown = earningsBreakdown,
                spendingBreakdown = spendingBreakdown,
                timeline = timeline
            ),
            achievements = achievements
        )
    }

    private fun parseTransactions(raw: Any?): List<Transaction>? {
        val list = raw as? List<*> ?: return null
        return list.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            Transaction(
                id = map["id"] as? String ?: UUID.randomUUID().toString(),
                date = map["date"] as? String ?: nowIso(),
                amount = (map["amount"] as? Number)?.toInt() ?: 0,
                source = map["source"] as? String ?: "",
                previousBalance = (map["previous_balance"] as? Number)?.toInt() ?: 0
            )
        }
    }

    private fun parseSettings(raw: Any?): Settings? {
        val map = raw as? Map<*, *> ?: return null
        val goal = (map["goal"] as? Number)?.toInt() ?: 13500
        val darkMode = map["dark_mode"] as? Boolean ?: false
        val qaRaw = map["quick_actions"] as? List<*> ?: defaultQuickActions()
        val quickActions = qaRaw.mapNotNull { qa ->
            val qaMap = qa as? Map<*, *> ?: return@mapNotNull null
            QuickAction(
                text = qaMap["text"] as? String ?: return@mapNotNull null,
                value = (qaMap["value"] as? Number)?.toInt() ?: return@mapNotNull null,
                isPositive = qaMap["is_positive"] as? Boolean ?: true
            )
        }
        return Settings(goal, darkMode, quickActions, firebaseAvailable = true)
    }

    private fun defaultSettings() = Settings(13500, false, defaultQuickActions(), true)

    private fun defaultQuickActions() = listOf(
        QuickAction("Salary", 5000, true),
        QuickAction("Food", 100, false),
        QuickAction("Transport", 50, false)
    )

    private fun settingsToMap(settings: Settings): Map<String, Any> = mapOf(
        "goal" to settings.goal,
        "dark_mode" to settings.darkMode,
        "quick_actions" to settings.quickActions.map {
            mapOf(
                "text" to it.text,
                "value" to it.value,
                "is_positive" to it.isPositive
            )
        }
    )

    private fun transactionToMap(tx: Transaction): Map<String, Any> = mapOf(
        "id" to tx.id,
        "date" to tx.date,
        "amount" to tx.amount,
        "source" to tx.source,
        "previous_balance" to tx.previousBalance
    )

    private fun recalcBalances(transactions: List<Transaction>): List<Transaction> {
        var balance = 0
        return transactions.sortedBy { it.date }.map { t ->
            val updated = t.copy(previousBalance = balance)
            balance += t.amount
            updated
        }
    }

    private fun nowIso(): String = Instant.now().atOffset(ZoneOffset.UTC).toString()
}