package com.cointracker.mobile.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cointracker.mobile.data.*
import com.cointracker.mobile.notifications.DailyReminderWorker
import com.cointracker.mobile.notifications.NotificationHelper
import com.cointracker.mobile.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

sealed class SyncState {
    object Idle                                                               : SyncState()
    object Offline                                                            : SyncState()
    object RestoredFromCache                                                  : SyncState()
    object PushedCacheToDb                                                    : SyncState()
    data class Conflict(val cached: CachedProfile, val db: ProfileEnvelope)  : SyncState()
}

@HiltViewModel
class CoinTrackerViewModel @Inject constructor(
    private val repo       : FirestoreRepository,
    private val localCache : LocalCacheRepository,
    application            : Application
) : AndroidViewModel(application) {

    private val _uiState    = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    private val prefs = application.getSharedPreferences("cointracker_prefs", Context.MODE_PRIVATE)

    init {
        _isDarkMode.value = prefs.getBoolean("is_dark_mode", false)
        checkSession()
    }

    private fun checkSession() {
        val sessionJson = prefs.getString("user_session", null) ?: return
        runCatching {
            val json    = JSONObject(sessionJson)
            val session = UserSession(
                userId         = json.getString("userId"),
                username       = json.getString("username"),
                role           = json.getString("role"),
                currentProfile = json.optString("currentProfile", "Default")
            )
            _uiState.update { it.copy(session = session) }
            viewModelScope.launch {
                val valid = repo.isSessionValid()
                if (valid) { refreshData(); loadProfiles() }
                else { logout(); _uiState.update { it.copy(error = "Session expired. Please log in again.") } }
            }
        }.onFailure { logout() }
    }

    private fun saveSession(session: UserSession) {
        val json = JSONObject().apply {
            put("userId",         session.userId)
            put("username",       session.username)
            put("role",           session.role)
            put("currentProfile", session.currentProfile)
        }
        prefs.edit().putString("user_session", json.toString()).apply()
        prefs.edit().putString("last_profile", session.currentProfile).apply()
    }

    fun logout() {
        DailyReminderWorker.cancel(getApplication())
        prefs.edit().remove("user_session").apply()
        _uiState.value = AppUiState()
        repo.logout()
    }

    fun clearError()        { _uiState.update { it.copy(error = null) } }
    fun dismissSyncBanner() { _uiState.update { it.copy(syncState = SyncState.Idle) } }

    fun toggleTheme() {
        val v = !_isDarkMode.value
        _isDarkMode.value = v
        prefs.edit().putBoolean("is_dark_mode", v).apply()
    }

    fun markNotificationsSeen() {
        val session      = _uiState.value.session ?: return
        val achievements = _uiState.value.profileEnvelope?.achievements ?: return
        val key          = "seen_achievements_${session.currentProfile}"
        prefs.edit().putStringSet(key, achievements.map { it.name }.toSet()).apply()
        _uiState.update { it.copy(unreadNotifCount = 0) }
    }

    private fun computeUnreadCount(env: ProfileEnvelope, session: UserSession): Int {
        val key  = "seen_achievements_${session.currentProfile}"
        val seen = prefs.getStringSet(key, emptySet()) ?: emptySet()
        return env.achievements.count { it.name !in seen }
    }

    fun register(username: String, password: String) {
        validateCredentials(username, password)?.let { _uiState.update { s -> s.copy(error = it) }; return }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.register(username, password)
            if (result.isSuccess) login(username, password)
            else _uiState.update { it.copy(loading = false, error = result.exceptionOrNull()?.message) }
        }
    }

    fun login(username: String, password: String) {
        validateCredentials(username, password)?.let { _uiState.update { s -> s.copy(error = it) }; return }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.login(username, password)
            if (result.isSuccess) {
                val session = result.getOrThrow()
                saveSession(session)
                _uiState.update { it.copy(session = session) }
                refreshData(); loadProfiles()
                DailyReminderWorker.schedule(getApplication())
            } else _uiState.update { it.copy(loading = false, error = result.exceptionOrNull()?.message) }
        }
    }

    fun deleteAccount(password: String) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.deleteAccount(session, password)
            if (result.isSuccess) {
                DailyReminderWorker.cancel(getApplication())
                prefs.edit().remove("user_session").apply()
                _uiState.value = AppUiState()
            } else _uiState.update { it.copy(loading = false, error = result.exceptionOrNull()?.message) }
        }
    }

    fun refreshData() {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }

            val cached   = localCache.load(session.userId, session.currentProfile)
            val dbResult = repo.loadProfile(session.userId, session.currentProfile)
            val dbOk     = dbResult.isSuccess
            val dbEnv    = dbResult.getOrNull()

            when (val sync = SyncManager.compare(cached, dbOk, dbEnv)) {

                is SyncResult.Synced, is SyncResult.DbNewer, is SyncResult.DbOnly -> {
                    val env = when (sync) {
                        is SyncResult.Synced  -> sync.env
                        is SyncResult.DbNewer -> sync.env
                        is SyncResult.DbOnly  -> sync.env
                        else -> dbEnv!!
                    }
                    updateCacheBg(session, env)
                    applyEnvelope(env, session, SyncState.Idle)
                }

                is SyncResult.RestoreFromCache -> {
                    val offlineEnv = localCache.buildOfflineEnvelope(sync.cached)
                    applyEnvelope(offlineEnv, session, SyncState.RestoredFromCache)
                    launch(Dispatchers.IO) {
                        val r = repo.importData(session, sync.cached.transactions, sync.cached.settings)
                        if (r.isSuccess) {
                            val fresh = r.getOrThrow()
                            updateCacheBg(session, fresh)
                            _uiState.update { it.copy(profileEnvelope = fresh) }
                        }
                    }
                }

                is SyncResult.UseCache -> {
                    val offlineEnv = localCache.buildOfflineEnvelope(sync.cached)
                    applyEnvelope(offlineEnv, session, SyncState.Offline)
                }

                is SyncResult.CacheNewer -> {
                    val offlineEnv = localCache.buildOfflineEnvelope(sync.cached)
                    applyEnvelope(offlineEnv, session, SyncState.PushedCacheToDb)
                    launch(Dispatchers.IO) {
                        val r = repo.importData(session, sync.cached.transactions, sync.cached.settings)
                        if (r.isSuccess) {
                            val fresh = r.getOrThrow()
                            updateCacheBg(session, fresh)
                            _uiState.update { it.copy(profileEnvelope = fresh, syncState = SyncState.Idle) }
                        }
                    }
                }

                is SyncResult.ConflictDetected ->
                    applyEnvelope(sync.db, session, SyncState.Conflict(sync.cached, sync.db))

                SyncResult.BothEmpty ->
                    _uiState.update { it.copy(loading = false, syncState = SyncState.Idle) }
            }
        }
    }

    fun resolveConflictUseCache() {
        val session  = _uiState.value.session ?: return
        val conflict = _uiState.value.syncState as? SyncState.Conflict ?: return
        _uiState.update { it.copy(syncState = SyncState.Idle, loading = true) }
        viewModelScope.launch {
            val r = repo.importData(session, conflict.cached.transactions, conflict.cached.settings)
            if (r.isSuccess) {
                val env = r.getOrThrow()
                updateCacheBg(session, env)
                applyEnvelope(env, session, SyncState.Idle)
            } else _uiState.update { it.copy(loading = false, error = r.exceptionOrNull()?.message) }
        }
    }

    fun resolveConflictUseDatabase() {
        val session  = _uiState.value.session ?: return
        val conflict = _uiState.value.syncState as? SyncState.Conflict ?: return
        updateCacheBg(session, conflict.db)
        _uiState.update { it.copy(syncState = SyncState.Idle) }
    }

    fun switchProfile(profile: String) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val r = repo.switchProfile(session, profile)
            if (r.isSuccess) {
                val updated = r.getOrThrow()
                saveSession(updated)
                _uiState.update { it.copy(session = updated) }
                refreshData(); loadProfiles()
            } else _uiState.update { it.copy(loading = false, error = r.exceptionOrNull()?.message) }
        }
    }

    fun createProfile(profile: String) {
        val trimmed = profile.trim()
        if (trimmed.isBlank()) { _uiState.update { it.copy(error = "Profile name cannot be empty") }; return }
        if (trimmed.any { it in listOf('/', '.', '#', '$', '[', ']') }) {
            _uiState.update { it.copy(error = "Profile name contains invalid characters") }; return
        }
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val r = repo.createProfile(session, trimmed)
            if (r.isSuccess) { _uiState.update { it.copy(profiles = r.getOrThrow(), loading = false) }; switchProfile(trimmed) }
            else _uiState.update { it.copy(loading = false, error = r.exceptionOrNull()?.message) }
        }
    }

    fun deleteProfile(profile: String) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val r = repo.deleteProfile(session, profile)
            if (r.isSuccess) {
                localCache.delete(session.userId, profile)
                _uiState.update { it.copy(profiles = r.getOrThrow(), loading = false) }
                switchProfile("Default")
            } else _uiState.update { it.copy(loading = false, error = r.exceptionOrNull()?.message) }
        }
    }

    fun deleteAllData() {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val r = repo.deleteAllData(session)
            if (r.isSuccess) { localCache.delete(session.userId, session.currentProfile); switchProfile("Default") }
            else _uiState.update { it.copy(loading = false, error = r.exceptionOrNull()?.message) }
        }
    }

    fun addTransaction(amount: Int, source: String, dateIso: String?) {
        validateTransactionAmount(amount)?.let { _uiState.update { s -> s.copy(error = it) }; return }
        val session = _uiState.value.session ?: return
        viewModelScope.launch { _uiState.update { it.copy(loading = true, error = null) }; postSave(repo.addTransaction(session, amount, source, dateIso), session) }
    }

    fun updateTransaction(id: String, amount: Int, source: String, dateIso: String) {
        validateTransactionAmount(amount)?.let { _uiState.update { s -> s.copy(error = it) }; return }
        val session = _uiState.value.session ?: return
        viewModelScope.launch { _uiState.update { it.copy(loading = true, error = null) }; postSave(repo.updateTransaction(session, id, amount, source, dateIso), session) }
    }

    fun deleteTransaction(transactionId: String) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch { _uiState.update { it.copy(loading = true, error = null) }; postSave(repo.deleteTransaction(session, transactionId), session) }
    }

    fun updateSettings(settings: Settings) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch { _uiState.update { it.copy(loading = true, error = null) }; postSave(repo.updateSettings(session, settings), session) }
    }

    fun addQuickAction(action: QuickAction) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch { _uiState.update { it.copy(loading = true, error = null) }; postSave(repo.addQuickAction(session, action), session) }
    }

    fun updateQuickAction(index: Int, action: QuickAction) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch { _uiState.update { it.copy(loading = true, error = null) }; postSave(repo.updateQuickAction(session, index, action), session) }
    }

    fun deleteQuickAction(index: Int) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch { _uiState.update { it.copy(loading = true, error = null) }; postSave(repo.deleteQuickAction(session, index), session) }
    }

    fun importFromJson(jsonString: String) {
        val session  = _uiState.value.session ?: return
        val settings = _uiState.value.profileEnvelope?.settings ?: Settings()
        runCatching {
            val arr = JSONArray(jsonString)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Transaction(
                    id              = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    date            = obj.optString("date").ifBlank { Instant.now().toString() },
                    amount          = obj.optInt("amount", 0),
                    source          = obj.optString("source", "Import"),
                    previousBalance = obj.optInt("previous_balance", 0)
                )
            }
        }.onFailure { _uiState.update { s -> s.copy(error = "Invalid backup file: ${it.message}") }; return }
         .onSuccess { txns -> viewModelScope.launch { _uiState.update { it.copy(loading = true, error = null) }; postSave(repo.importData(session, txns, settings), session) } }
    }

    fun loadAdmin() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val stats = repo.loadAdminStats(); val users = repo.loadAdminUsers()
            _uiState.update { s -> s.copy(adminStats = stats.getOrNull(), adminUsers = users.getOrDefault(emptyList()), loading = false,
                error = stats.exceptionOrNull()?.message ?: users.exceptionOrNull()?.message) }
        }
    }

    fun deleteUser(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val r = repo.deleteUser(userId)
            if (r.isSuccess) loadAdmin() else _uiState.update { it.copy(loading = false, error = r.exceptionOrNull()?.message) }
        }
    }

    private fun loadProfiles() {
        val session = _uiState.value.session ?: return
        viewModelScope.launch { val p = repo.listProfiles(session); if (p.isSuccess) _uiState.update { it.copy(profiles = p.getOrThrow(), loading = false) } }
    }

    private fun postSave(result: Result<ProfileEnvelope>, session: UserSession) {
        _uiState.update { s ->
            if (result.isSuccess) {
                val env = result.getOrThrow()
                onDataUpdated(env); updateCacheBg(session, env)
                s.copy(profileEnvelope = env, loading = false, unreadNotifCount = computeUnreadCount(env, session))
            } else s.copy(loading = false, error = result.exceptionOrNull()?.message)
        }
    }

    private fun applyEnvelope(env: ProfileEnvelope, session: UserSession, syncState: SyncState) {
        onDataUpdated(env)
        _uiState.update { it.copy(profileEnvelope = env, loading = false, syncState = syncState, unreadNotifCount = computeUnreadCount(env, session)) }
    }

    private fun updateCacheBg(session: UserSession, env: ProfileEnvelope) {
        viewModelScope.launch(Dispatchers.IO) { localCache.save(session.userId, session.currentProfile, env) }
    }

    private fun onDataUpdated(env: ProfileEnvelope) {
        viewModelScope.launch(Dispatchers.IO) { WidgetUpdater.update(getApplication()) }
        val ctx = getApplication<Application>()
        val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        else true
        if (!hasPerm) return
        val key = "known_achievements_${env.profile}"
        val known = prefs.getStringSet(key, emptySet()) ?: emptySet()
        val newAchs = env.achievements.filter { it.name !in known }
        newAchs.forEach { NotificationHelper.notifyMilestone(ctx, it.icon, it.name, it.desc) }
        if (newAchs.isNotEmpty()) prefs.edit().putStringSet(key, env.achievements.map { it.name }.toSet()).apply()
    }

    private fun validateCredentials(username: String, password: String): String? = when {
        username.isBlank()     -> "Username cannot be empty"
        username.length < 3    -> "Username must be at least 3 characters"
        username.contains(" ") -> "Username cannot contain spaces"
        password.isBlank()     -> "Password cannot be empty"
        password.length < 4    -> "Password must be at least 4 characters"
        else                   -> null
    }

    private fun validateTransactionAmount(amount: Int): String? = when {
        amount == 0                       -> "Amount cannot be zero"
        kotlin.math.abs(amount) > 999_999 -> "Amount is too large (max 999,999)"
        else                              -> null
    }
}

data class AppUiState(
    val session          : UserSession?       = null,
    val loading          : Boolean            = false,
    val error            : String?            = null,
    val profileEnvelope  : ProfileEnvelope?   = null,
    val profiles         : List<String>       = emptyList(),
    val adminStats       : AdminStats?        = null,
    val adminUsers       : List<AdminUserRow> = emptyList(),
    val unreadNotifCount : Int                = 0,
    val syncState        : SyncState          = SyncState.Idle
)
