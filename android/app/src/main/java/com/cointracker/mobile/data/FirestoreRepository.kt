package com.cointracker.mobile.data

import com.cointracker.mobile.domain.AchievementCalculator
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreRepository @Inject constructor() {

    private val auth = FirebaseAuth.getInstance()

    private val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance().also { firestore ->
            firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()
        }
    }
    private val usersRef    get() = db.collection("users")
    private val userDataRef get() = db.collection("user_data")

    // ── Auth helpers ─────────────────────────────────────────────────────────

    private fun toEmail(username: String) = "${username.trim().lowercase()}@cointracker.app"

    // ── LOGIN ────────────────────────────────────────────────────────────────
    // Flow:
    //   1. Try Firebase Email/Password  →  success = new-style user, done.
    //   2. Fail  →  look up legacy doc by username, verify Werkzeug hash.
    //   3. Hash OK  →  create Email/Password account, copy Firestore docs to
    //      new UID, delete old docs.  User is now migrated silently.

    suspend fun login(username: String, password: String): Result<UserSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                val email = toEmail(username)

                // ── 1. Try new-style login ────────────────────────────────
                val directResult = runCatching {
                    auth.signInWithEmailAndPassword(email, password).await()
                }

                if (directResult.isSuccess) {
                    val uid = auth.currentUser!!.uid
                    return@runCatching buildSession(uid, username)
                }

                // ── 2. Legacy path: find user doc by username ─────────────
                val querySnap = usersRef
                    .whereEqualTo("username_lower", username)
                    .limit(1)
                    .get()
                    .await()

                if (querySnap.isEmpty) throw Exception("Invalid username or password")

                val legacyDoc   = querySnap.documents[0]
                val storedHash  = legacyDoc.getString("password_hash")
                    ?: throw Exception("Invalid username or password")

                if (!WerkzeugPasswordHasher().verify(password, storedHash))
                    throw Exception("Invalid username or password")

                // ── 3. Password OK — migrate to Email/Password auth ───────
                val oldUid     = legacyDoc.id
                val role       = legacyDoc.getString("role") ?: "user"
                val createdAt  = legacyDoc.getString("created_at") ?: nowIso()

                runCatching {
                    auth.createUserWithEmailAndPassword(email, password).await()
                }.onFailure { ex ->
                    // If account already partially migrated, just sign in
                    if (ex is FirebaseAuthUserCollisionException) {
                        auth.signInWithEmailAndPassword(email, password).await()
                    } else throw ex
                }

                val newUid = auth.currentUser!!.uid

                if (newUid != oldUid) {
                    // Copy user_data
                    val oldData = userDataRef.document(oldUid).get().await()
                    if (oldData.exists()) {
                        userDataRef.document(newUid)
                            .set(oldData.data ?: emptyMap<String, Any>()).await()
                    } else {
                        // Bootstrap empty profile for migrated user
                        userDataRef.document(newUid).set(
                            mapOf(
                                "profiles" to mapOf(
                                    "Default" to mapOf(
                                        "transactions" to emptyList<Map<String, Any>>(),
                                        "settings"     to settingsToMap(defaultSettings()),
                                        "last_updated" to nowIso()
                                    )
                                ),
                                "last_active_profile" to "Default"
                            )
                        ).await()
                    }
                    // Create users doc for new UID (no password_hash stored)
                    usersRef.document(newUid).set(
                        mapOf(
                            "username"       to username,
                            "role"           to role,
                            "created_at"     to createdAt,
                            "migrated_from"  to oldUid,
                            "migrated_at"    to nowIso()
                        )
                    ).await()
                    // Clean up old docs (best-effort)
                    runCatching { usersRef.document(oldUid).delete().await() }
                    runCatching { userDataRef.document(oldUid).delete().await() }
                }

                buildSession(newUid, username)
            }
        }

    // ── REGISTER ─────────────────────────────────────────────────────────────

    suspend fun register(username: String, password: String): Result<UserSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                val email = toEmail(username)

                // Check username uniqueness first
                val existing = usersRef.whereEqualTo("username", username).limit(1).get().await()
                if (!existing.isEmpty) throw Exception("Username already taken")

                val result = try {
                    auth.createUserWithEmailAndPassword(email, password).await()
                } catch (e: FirebaseAuthUserCollisionException) {
                    throw Exception("Username already taken")
                } catch (e: FirebaseAuthWeakPasswordException) {
                    throw Exception("Password is too weak")
                } catch (e: Exception) {
                    throw Exception("Registration failed: ${e.message}")
                }

                val uid = result.user!!.uid
                usersRef.document(uid).set(
                    mapOf("username" to username, "role" to "user", "created_at" to nowIso())
                ).await()
                userDataRef.document(uid).set(
                    mapOf(
                        "profiles" to mapOf(
                            "Default" to mapOf(
                                "transactions" to emptyList<Map<String, Any>>(),
                                "settings"     to settingsToMap(defaultSettings()),
                                "last_updated" to nowIso()
                            )
                        ),
                        "last_active_profile" to "Default"
                    )
                ).await()

                UserSession(uid, username, "user", "Default")
            }
        }

    // ── DELETE ACCOUNT ───────────────────────────────────────────────────────
    // Firebase requires recent auth before deleting an account.
    // We re-authenticate with the user's password, then delete everything.

    suspend fun deleteAccount(session: UserSession, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val user = auth.currentUser ?: throw Exception("Not logged in")
                val credential = EmailAuthProvider.getCredential(toEmail(session.username), password)
                try {
                    user.reauthenticate(credential).await()
                } catch (e: FirebaseAuthInvalidCredentialsException) {
                    throw Exception("Incorrect password")
                }
                // Delete Firestore data
                runCatching { userDataRef.document(session.userId).delete().await() }
                runCatching { usersRef.document(session.userId).delete().await() }
                // Delete Firebase Auth account
                user.delete().await()
                Unit
            }
        }

    fun logout() { auth.signOut() }

    suspend fun isSessionValid(): Boolean = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext false
        runCatching { user.getIdToken(true).await(); true }.getOrElse { false }
    }

    // ── Private session builder ───────────────────────────────────────────────

    private suspend fun buildSession(uid: String, fallbackUsername: String): UserSession {
        val userDoc     = usersRef.document(uid).get().await()
        val role        = userDoc.getString("role") ?: "user"
        val username    = userDoc.getString("username") ?: fallbackUsername
        val userDataDoc = userDataRef.document(uid).get().await()
        val lastProfile = userDataDoc.getString("last_active_profile") ?: "Default"
        return UserSession(uid, username, role, lastProfile)
    }

    // ── DATA OPERATIONS (unchanged) ──────────────────────────────────────────

    suspend fun loadProfile(userId: String, profileName: String): Result<ProfileEnvelope> =
        runCatching {
            val (transactions, settings) = getData(userId, profileName)
            buildEnvelope(profileName, transactions, settings)
        }

    suspend fun listProfiles(session: UserSession): Result<List<String>> = runCatching {
        val doc  = userDataRef.document(session.userId).get().await()
        val data = doc.data ?: return@runCatching listOf("Default")
        val profiles = (data["profiles"] as? Map<*, *>)?.keys?.map { it.toString() } ?: emptyList()
        if (profiles.isNotEmpty()) profiles.sorted() else listOf("Default")
    }

    suspend fun switchProfile(session: UserSession, profile: String): Result<UserSession> =
        runCatching {
            userDataRef.document(session.userId).update("last_active_profile", profile).await()
            session.copy(currentProfile = profile)
        }

    suspend fun createProfile(session: UserSession, profile: String): Result<List<String>> =
        runCatching {
            val doc      = userDataRef.document(session.userId).get().await()
            val data     = doc.data?.toMutableMap() ?: mutableMapOf()
            val profiles = (data["profiles"] as? Map<*, *>)?.toMutableMap() ?: mutableMapOf()
            if (profiles.containsKey(profile)) throw IllegalStateException("Profile already exists")
            profiles[profile] = mapOf(
                "transactions" to emptyList<Map<String, Any>>(),
                "settings"     to settingsToMap(defaultSettings()),
                "last_updated" to nowIso()
            )
            data["profiles"] = profiles
            data["last_active_profile"] = profile
            userDataRef.document(session.userId).set(data).await()
            profiles.keys.map { it.toString() }
        }

    suspend fun deleteProfile(session: UserSession, profile: String): Result<List<String>> =
        runCatching {
            if (profile == "Default") throw IllegalStateException("Cannot delete Default profile")
            val doc      = userDataRef.document(session.userId).get().await()
            val data     = doc.data?.toMutableMap() ?: mutableMapOf()
            val profiles = (data["profiles"] as? Map<*, *>)?.toMutableMap() ?: mutableMapOf()
            if (!profiles.containsKey(profile)) throw IllegalStateException("Profile not found")
            profiles.remove(profile)
            data["profiles"] = profiles
            data["last_active_profile"] = "Default"
            userDataRef.document(session.userId).set(data).await()
            profiles.keys.map { it.toString() }
        }

    suspend fun deleteAllData(session: UserSession): Result<Unit> = runCatching {
        userDataRef.document(session.userId).set(
            mapOf(
                "profiles" to mapOf(
                    "Default" to mapOf(
                        "transactions" to emptyList<Map<String, Any>>(),
                        "settings"     to settingsToMap(defaultSettings()),
                        "last_updated" to nowIso()
                    )
                ),
                "last_active_profile" to "Default"
            )
        ).await()
    }

    suspend fun addTransaction(session: UserSession, amount: Int, source: String, dateIso: String?): Result<ProfileEnvelope> =
        runCatching {
            val pn = session.currentProfile
            val (txns, settings) = getData(session.userId, pn)
            val tx      = Transaction(id = UUID.randomUUID().toString(), date = dateIso ?: nowIso(), amount = amount, source = source)
            val updated = recalcBalances(txns + tx)
            saveProfile(session.userId, pn, updated, settings)
            buildEnvelope(pn, updated, settings)
        }

    suspend fun updateTransaction(session: UserSession, transactionId: String, amount: Int, source: String, dateIso: String): Result<ProfileEnvelope> =
        runCatching {
            val pn = session.currentProfile
            val (txns, settings) = getData(session.userId, pn)
            val normDate = normaliseDate(dateIso)
            val updated  = recalcBalances(txns.map {
                if (it.id == transactionId) it.copy(amount = amount, source = source, date = normDate) else it
            })
            saveProfile(session.userId, pn, updated, settings)
            buildEnvelope(pn, updated, settings)
        }

    suspend fun deleteTransaction(session: UserSession, transactionId: String): Result<ProfileEnvelope> =
        runCatching {
            val pn = session.currentProfile
            val (txns, settings) = getData(session.userId, pn)
            val updated = recalcBalances(txns.filterNot { it.id == transactionId })
            saveProfile(session.userId, pn, updated, settings)
            buildEnvelope(pn, updated, settings)
        }

    suspend fun updateSettings(session: UserSession, updatedSettings: Settings): Result<ProfileEnvelope> =
        runCatching {
            val pn = session.currentProfile
            val (txns, _) = getData(session.userId, pn)
            saveProfile(session.userId, pn, txns, updatedSettings)
            buildEnvelope(pn, txns, updatedSettings)
        }

    suspend fun addQuickAction(session: UserSession, action: QuickAction): Result<ProfileEnvelope> =
        runCatching {
            val pn = session.currentProfile
            val (txns, settings) = getData(session.userId, pn)
            val ns = settings.copy(quickActions = settings.quickActions + action)
            saveProfile(session.userId, pn, txns, ns); buildEnvelope(pn, txns, ns)
        }

    suspend fun updateQuickAction(session: UserSession, index: Int, action: QuickAction): Result<ProfileEnvelope> =
        runCatching {
            val pn = session.currentProfile
            val (txns, settings) = getData(session.userId, pn)
            if (index < 0 || index >= settings.quickActions.size) throw IndexOutOfBoundsException("Invalid index")
            val actions = settings.quickActions.toMutableList().also { it[index] = action }
            val ns = settings.copy(quickActions = actions)
            saveProfile(session.userId, pn, txns, ns); buildEnvelope(pn, txns, ns)
        }

    suspend fun deleteQuickAction(session: UserSession, index: Int): Result<ProfileEnvelope> =
        runCatching {
            val pn = session.currentProfile
            val (txns, settings) = getData(session.userId, pn)
            if (index < 0 || index >= settings.quickActions.size) throw IndexOutOfBoundsException("Invalid index")
            val ns = settings.copy(quickActions = settings.quickActions.filterIndexed { i, _ -> i != index })
            saveProfile(session.userId, pn, txns, ns); buildEnvelope(pn, txns, ns)
        }

    suspend fun importData(session: UserSession, transactions: List<Transaction>, settings: Settings): Result<ProfileEnvelope> =
        runCatching {
            val pn        = session.currentProfile
            val validated = recalcBalances(transactions)
            saveProfile(session.userId, pn, validated, settings)
            buildEnvelope(pn, validated, settings)
        }

    // ── ADMIN ────────────────────────────────────────────────────────────────

    suspend fun loadAdminStats(): Result<AdminStats> = runCatching {
        val snap  = usersRef.get().await()
        val total = snap.size()
        var totalCoins = 0; var totalTxns = 0
        val today = LocalDate.now(ZoneOffset.UTC)
        val dayMap = LinkedHashMap<String, Int>()
        for (i in 6 downTo 0) dayMap[today.minusDays(i.toLong()).toString()] = 0
        snap.documents.forEach { doc ->
            val ca = doc.getString("created_at") ?: return@forEach
            val k  = ca.take(10)
            if (dayMap.containsKey(k)) dayMap[k] = (dayMap[k] ?: 0) + 1
        }
        try {
            userDataRef.get().await().documents.forEach { doc ->
                val payload = doc.data ?: return@forEach
                val txns    = extractAllTransactions(payload)
                totalTxns  += txns.size
                totalCoins += txns.sumOf { it.amount }
            }
        } catch (_: Exception) {}
        val keys = dayMap.keys.toList()
        AdminStats(total, totalCoins, totalTxns, keys.map { it.takeLast(5) }, keys.map { dayMap[it] ?: 0 })
    }

    suspend fun loadAdminUsers(): Result<List<AdminUserRow>> = runCatching {
        val snap        = usersRef.get().await()
        val userDataMap = try { userDataRef.get().await().documents.associate { it.id to it.data } }
        catch (_: Exception) { emptyMap() }
        snap.documents.mapNotNull { doc ->
            val uData = doc.data ?: return@mapNotNull null
            val pData = userDataMap[doc.id]
            var balance = 0; var txCount = 0; var lastUpdated = "N/A"
            if (pData != null) {
                val txns = extractAllTransactions(pData)
                balance  = txns.sumOf { it.amount }; txCount = txns.size
                lastUpdated = (pData["profiles"] as? Map<*, *>)?.values
                    ?.mapNotNull { (it as? Map<*, *>)?.get("last_updated") as? String }
                    ?.maxOrNull() ?: "N/A"
            }
            AdminUserRow(doc.id, uData["username"] as? String ?: "N/A", balance, txCount,
                uData["created_at"] as? String ?: "N/A", lastUpdated)
        }.sortedByDescending { it.createdAt }
    }

    suspend fun deleteUser(userId: String): Result<Unit> = runCatching {
        usersRef.document(userId).delete().await()
        userDataRef.document(userId).delete().await()
    }

    private fun extractAllTransactions(payload: Map<String, Any>): List<Transaction> {
        val all      = mutableListOf<Transaction>()
        val profiles = payload["profiles"] as? Map<*, *>
        if (profiles != null) {
            profiles.values.forEach { p ->
                val pm = p as? Map<*, *> ?: return@forEach
                parseTransactions(pm["transactions"])?.let { all.addAll(it) }
            }
        } else parseTransactions(payload["transactions"])?.let { all.addAll(it) }
        return all
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun getData(userId: String, profile: String): Pair<List<Transaction>, Settings> {
        val doc      = userDataRef.document(userId).get().await()
        val data     = doc.data ?: emptyMap<String, Any>()
        val profiles = data["profiles"] as? Map<*, *>
        return if (profiles != null) {
            val pd = profiles[profile] as? Map<*, *> ?: emptyMap<String, Any>()
            recalcBalances(parseTransactions(pd["transactions"]) ?: emptyList()) to
                    (parseSettings(pd["settings"]) ?: defaultSettings())
        } else {
            recalcBalances(parseTransactions(data["transactions"]) ?: emptyList()) to
                    (parseSettings(data["settings"]) ?: defaultSettings())
        }
    }

    private suspend fun saveProfile(userId: String, profile: String, transactions: List<Transaction>, settings: Settings) {
        val doc      = userDataRef.document(userId).get().await()
        val existing = doc.data?.toMutableMap() ?: mutableMapOf()
        val profiles = (existing["profiles"] as? Map<*, *>)?.toMutableMap() ?: mutableMapOf()
        profiles[profile] = mapOf(
            "transactions" to transactions.map { transactionToMap(it) },
            "settings"     to settingsToMap(settings),
            "last_updated" to nowIso()
        )
        existing["profiles"] = profiles
        existing["last_active_profile"] = profile
        userDataRef.document(userId).set(existing).await()
    }

    private fun buildEnvelope(profileName: String, transactions: List<Transaction>, settings: Settings): ProfileEnvelope {
        val balance    = transactions.sumOf { it.amount }
        val goal       = settings.goal
        val today      = LocalDate.now(ZoneOffset.UTC)
        val weekStart  = today.minusDays(today.dayOfWeek.ordinal.toLong())
        val monthStart = today.withDayOfMonth(1)
        var todayEarn = 0; var weekEarn = 0; var monthEarn = 0
        var totalEarnings = 0; var firstEarningDate: Instant? = null
        transactions.forEach { t ->
            if (t.amount > 0) {
                totalEarnings += t.amount
                val inst = parseInstantSafe(t.date) ?: return@forEach
                if (firstEarningDate == null || inst.isBefore(firstEarningDate)) firstEarningDate = inst
                val d = inst.atZone(ZoneOffset.UTC).toLocalDate()
                if (d == today) todayEarn += t.amount
                if (!d.isBefore(weekStart)) weekEarn += t.amount
                if (!d.isBefore(monthStart)) monthEarn += t.amount
            }
        }
        val estimatedDays: Int? = if (totalEarnings > 0 && firstEarningDate != null) {
            val days = maxOf(1, (Instant.now().epochSecond - firstEarningDate!!.epochSecond).div(86400).toInt())
            val avg  = totalEarnings / days.toDouble()
            val rem  = goal - balance
            when { rem <= 0 -> 0; avg > 0 -> (rem / avg).toInt(); else -> null }
        } else null
        val earningsBreakdown = mutableMapOf<String, Int>()
        val spendingBreakdown = mutableMapOf<String, Int>()
        transactions.forEach { t ->
            if (t.amount > 0) earningsBreakdown[t.source] = (earningsBreakdown[t.source] ?: 0) + t.amount
            if (t.amount < 0) spendingBreakdown[t.source] = (spendingBreakdown[t.source] ?: 0) + -t.amount
        }
        return ProfileEnvelope(
            profile = profileName, transactions = transactions,
            settings = settings.copy(firebaseAvailable = true),
            balance = balance, goal = goal,
            progress = if (goal > 0) minOf(100, ((balance.toDouble() / goal) * 100).toInt()) else 0,
            estimatedDays = estimatedDays,
            dashboardStats = DashboardStats(todayEarn, weekEarn, monthEarn),
            analytics = AnalyticsSnapshot(
                totalEarnings = totalEarnings,
                totalSpending = -transactions.filter { it.amount < 0 }.sumOf { it.amount },
                netBalance    = balance,
                earningsBreakdown = earningsBreakdown,
                spendingBreakdown = spendingBreakdown,
                timeline = transactions.sortedBy { it.date }
                    .map { t -> TimelinePoint(t.date, t.previousBalance + t.amount) }
            ),
            achievements = AchievementCalculator().calculate(transactions, balance, goal)
        )
    }

    private fun parseTransactions(raw: Any?): List<Transaction>? =
        (raw as? List<*>)?.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            Transaction(
                id              = map["id"] as? String ?: UUID.randomUUID().toString(),
                date            = map["date"] as? String ?: nowIso(),
                amount          = (map["amount"] as? Number)?.toInt() ?: 0,
                source          = map["source"] as? String ?: "",
                previousBalance = (map["previous_balance"] as? Number)?.toInt() ?: 0
            )
        }

    @Suppress("UNCHECKED_CAST")
    private fun parseSettings(raw: Any?): Settings? {
        val map   = raw as? Map<*, *> ?: return null
        val qaRaw = map["quick_actions"] as? List<*> ?: defaultQuickActions()
        return Settings(
            goal = (map["goal"] as? Number)?.toInt() ?: 13500,
            darkMode = map["dark_mode"] as? Boolean ?: false,
            quickActions = qaRaw.mapNotNull { qa ->
                val m = qa as? Map<*, *> ?: return@mapNotNull null
                QuickAction(
                    text       = m["text"] as? String ?: return@mapNotNull null,
                    value      = (m["value"] as? Number)?.toInt() ?: return@mapNotNull null,
                    isPositive = m["is_positive"] as? Boolean ?: true
                )
            },
            firebaseAvailable   = true,
            incomeCategories    = (map["income_categories"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            expenseCategories   = (map["expense_categories"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        )
    }

    private fun defaultSettings()                         = Settings(13500, false, defaultQuickActions(), true)
    private fun settingsToMap(s: Settings): Map<String, Any> = mapOf(
        "goal"              to s.goal,
        "dark_mode"         to s.darkMode,
        "quick_actions"     to s.quickActions.map { mapOf("text" to it.text, "value" to it.value, "is_positive" to it.isPositive) },
        "income_categories" to s.incomeCategories,
        "expense_categories" to s.expenseCategories
    )
    private fun transactionToMap(tx: Transaction): Map<String, Any> = mapOf(
        "id" to tx.id, "date" to tx.date, "amount" to tx.amount,
        "source" to tx.source, "previous_balance" to tx.previousBalance
    )
    private fun recalcBalances(transactions: List<Transaction>): List<Transaction> {
        var bal = 0
        return transactions.sortedBy { it.date }.map { t -> t.copy(previousBalance = bal).also { bal += t.amount } }
    }
    private fun nowIso() = Instant.now().toString()
    private fun normaliseDate(s: String): String = when {
        s.endsWith("Z") && s.length > 10 -> s
        s.contains("T") && s.length > 10 ->
            runCatching { Instant.parse(s).toString() }
                .getOrElse { runCatching { java.time.OffsetDateTime.parse(s).toInstant().toString() }.getOrElse { nowIso() } }
        s.length == 10 -> "${s}T00:00:00Z"
        else -> nowIso()
    }
    private fun parseInstantSafe(s: String): Instant? =
        runCatching { Instant.parse(s) }
            .getOrElse { runCatching { java.time.OffsetDateTime.parse(s).toInstant() }.getOrNull() }

    private fun buildEnvelope(profileName: String, transactions: List<Transaction>, settings: Settings): ProfileEnvelope {
        val balance    = transactions.sumOf { it.amount }
        val goal       = settings.goal
        val today      = LocalDate.now(ZoneOffset.UTC)
        val weekStart  = today.minusDays(today.dayOfWeek.ordinal.toLong())
        val monthStart = today.withDayOfMonth(1)
        val sevenAgo   = today.minusDays(7)

        var todayEarn = 0; var weekEarn = 0; var monthEarn = 0
        var totalEarnings = 0; var firstEarningDate: Instant? = null
        var earnings7d = 0

        transactions.forEach { t ->
            if (t.amount > 0) {
                totalEarnings += t.amount
                val inst = parseInstantSafe(t.date) ?: return@forEach
                if (firstEarningDate == null || inst.isBefore(firstEarningDate)) firstEarningDate = inst
                val d = inst.atZone(ZoneOffset.UTC).toLocalDate()
                if (d == today)            todayEarn += t.amount
                if (!d.isBefore(weekStart)) weekEarn  += t.amount
                if (!d.isBefore(monthStart)) monthEarn += t.amount
                if (!d.isBefore(sevenAgo))  earnings7d += t.amount   // ← NEW: 7d window
            }
        }

        // ── Estimated days using 7-day rate (falls back to lifetime if <7d data) ──
        val dailyRate7d = earnings7d / 7.0
        val hasEnoughHistory = firstEarningDate != null &&
            (Instant.now().epochSecond - firstEarningDate!!.epochSecond) >= 7 * 86400

        val estimatedDays: Int? = when {
            balance >= goal -> 0
            hasEnoughHistory && dailyRate7d > 0 -> ((goal - balance) / dailyRate7d).toInt()
            !hasEnoughHistory && totalEarnings > 0 && firstEarningDate != null -> {
                val days = maxOf(1, (Instant.now().epochSecond - firstEarningDate!!.epochSecond).div(86400).toInt())
                val avg  = totalEarnings / days.toDouble()
                if (avg > 0) ((goal - balance) / avg).toInt() else null
            }
            else -> null
        }

        // ── Best earning week ─────────────────────────────────────────────────────
        val weekMap = mutableMapOf<LocalDate, Int>()  // week-start → total income
        transactions.filter { it.amount > 0 }.forEach { t ->
            val d = parseInstantSafe(t.date)?.atZone(ZoneOffset.UTC)?.toLocalDate() ?: return@forEach
            val ws = d.minusDays(d.dayOfWeek.ordinal.toLong())
            weekMap[ws] = (weekMap[ws] ?: 0) + t.amount
        }
        val bestWeekEntry  = weekMap.maxByOrNull { it.value }
        val bestWeekAmt    = bestWeekEntry?.value ?: 0
        val bestWeekLabel  = bestWeekEntry?.key?.let {
            val end = it.plusDays(6)
            "${it.monthValue}/${it.dayOfMonth} – ${end.monthValue}/${end.dayOfMonth}"
        } ?: "N/A"

        // ── Breakdowns ────────────────────────────────────────────────────────────
        val earningsBreakdown = mutableMapOf<String, Int>()
        val spendingBreakdown = mutableMapOf<String, Int>()
        transactions.forEach { t ->
            if (t.amount > 0) earningsBreakdown[t.source] = (earningsBreakdown[t.source] ?: 0) + t.amount
            if (t.amount < 0) spendingBreakdown[t.source] = (spendingBreakdown[t.source] ?: 0) + -t.amount
        }

        val achievements = AchievementCalculator().calculate(transactions, balance, goal)

        return ProfileEnvelope(
            profile             = profileName,
            transactions        = transactions,
            settings            = settings.copy(firebaseAvailable = true),
            balance             = balance,
            goal                = goal,
            progress            = if (goal > 0) minOf(100, ((balance.toDouble() / goal) * 100).toInt()) else 0,
            estimatedDays       = estimatedDays,
            dashboardStats      = DashboardStats(todayEarn, weekEarn, monthEarn),
            analytics           = AnalyticsSnapshot(
                totalEarnings     = totalEarnings,
                totalSpending     = -transactions.filter { it.amount < 0 }.sumOf { it.amount },
                netBalance        = balance,
                earningsBreakdown = earningsBreakdown,
                spendingBreakdown = spendingBreakdown,
                timeline          = transactions.sortedBy { it.date }
                    .map { t -> TimelinePoint(t.date, t.previousBalance + t.amount) },
                bestWeekEarnings  = bestWeekAmt,      // ← NEW
                bestWeekLabel     = bestWeekLabel,     // ← NEW
                dailyRate7d       = dailyRate7d        // ← NEW
            ),
            achievements        = achievements
        )
    }
}