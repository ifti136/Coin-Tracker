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

@HiltViewModel
class CoinTrackerViewModel @Inject constructor(
    private val repo: FirestoreRepository,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState   = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    private val prefs = application.getSharedPreferences("cointracker_prefs", Context.MODE_PRIVATE)

    init {
        _isDarkMode.value = prefs.getBoolean("is_dark_mode", false)
        checkSession()
    }

    // ── Session ───────────────────────────────────────────────────────────────

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
        // Persist last profile for worker + widget
        prefs.edit().putString("last_profile", session.currentProfile).apply()
    }

    fun logout() {
        DailyReminderWorker.cancel(getApplication())
        prefs.edit().remove("user_session").apply()
        _uiState.value = AppUiState()
        repo.logout()
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }

    fun toggleTheme() {
        val v = !_isDarkMode.value
        _isDarkMode.value = v
        prefs.edit().putBoolean("is_dark_mode", v).apply()
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

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
                refreshData()
                loadProfiles()
                DailyReminderWorker.schedule(getApplication())
            } else _uiState.update { it.copy(loading = false, error = result.exceptionOrNull()?.message) }
        }
    }

    // ── DELETE ACCOUNT ────────────────────────────────────────────────────────

    fun deleteAccount(password: String) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.deleteAccount(session, password)
            if (result.isSuccess) {
                DailyReminderWorker.cancel(getApplication())
                prefs.edit().remove("user_session").apply()
                _uiState.value = AppUiState()
            } else {
                _uiState.update { it.copy(loading = false, error = result.exceptionOrNull()?.message) }
            }
        }
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    fun refreshData() {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.loadProfile(session.userId, session.currentProfile)
            _uiState.update {
                if (result.isSuccess) {
                    val env = result.getOrThrow()
                    onDataUpdated(env)
                    it.copy(profileEnvelope = env, loading = false)
                } else it.copy(error = result.exceptionOrNull()?.message ?: "Failed to load data", loading = false)
            }
        }
    }

    fun switchProfile(profile: String) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.switchProfile(session, profile)
            if (result.isSuccess) {
                val updated = result.getOrThrow()
                saveSession(updated)
                _uiState.update { it.copy(session = updated) }
                refreshData(); loadProfiles()
            } else _uiState.update { it.copy(loading = false, error = result.exceptionOrNull()?.message) }
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
            val result = repo.createProfile(session, trimmed)
            if (result.isSuccess) { _uiState.update { it.copy(profiles = result.getOrThrow(), loading = false) }; switchProfile(trimmed) }
            else _uiState.update { it.copy(loading = false, error = result.exceptionOrNull()?.message) }
        }
    }

    fun deleteProfile(profile: String) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.deleteProfile(session, profile)
            if (result.isSuccess) { _uiState.update { it.copy(profiles = result.getOrThrow(), loading = false) }; switchProfile("Default") }
            else _uiState.update { it.copy(loading = false, error = result.exceptionOrNull()?.message) }
        }
    }

    fun deleteAllData() {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.deleteAllData(session)
            if (result.isSuccess) switchProfile("Default")
            else _uiState.update { it.copy(loading = false, error = result.exceptionOrNull()?.message) }
        }
    }

    fun addTransaction(amount: Int, source: String, dateIso: String?) {
        validateTransactionAmount(amount)?.let { _uiState.update { s -> s.copy(error = it) }; return }
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.addTransaction(session, amount, source, dateIso)
            _uiState.update { s ->
                if (result.isSuccess) {
                    val env = result.getOrThrow()
                    onDataUpdated(env)
                    s.copy(profileEnvelope = env, loading = false)
                } else s.copy(loading = false, error = result.exceptionOrNull()?.message)
            }
        }
    }

    fun updateTransaction(transactionId: String, amount: Int, source: String, dateIso: String) {
        validateTransactionAmount(amount)?.let { _uiState.update { s -> s.copy(error = it) }; return }
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.updateTransaction(session, transactionId, amount, source, dateIso)
            _uiState.update { s ->
                if (result.isSuccess) {
                    val env = result.getOrThrow()
                    onDataUpdated(env)
                    s.copy(profileEnvelope = env, loading = false)
                } else s.copy(loading = false, error = result.exceptionOrNull()?.message)
            }
        }
    }

    fun deleteTransaction(transactionId: String) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.deleteTransaction(session, transactionId)
            _uiState.update { s ->
                if (result.isSuccess) {
                    val env = result.getOrThrow()
                    onDataUpdated(env)
                    s.copy(profileEnvelope = env, loading = false)
                } else s.copy(loading = false, error = result.exceptionOrNull()?.message)
            }
        }
    }

    fun updateSettings(settings: Settings) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.updateSettings(session, settings)
            _uiState.update { s ->
                if (result.isSuccess) {
                    val env = result.getOrThrow()
                    onDataUpdated(env)
                    s.copy(profileEnvelope = env, loading = false)
                } else s.copy(loading = false, error = result.exceptionOrNull()?.message)
            }
        }
    }

    fun addQuickAction(action: QuickAction) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.addQuickAction(session, action)
            _uiState.update { s ->
                if (result.isSuccess) {
                    val env = result.getOrThrow()
                    onDataUpdated(env)
                    s.copy(profileEnvelope = env, loading = false)
                } else s.copy(loading = false, error = result.exceptionOrNull()?.message)
            }
        }
    }

    fun updateQuickAction(index: Int, action: QuickAction) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.updateQuickAction(session, index, action)
            _uiState.update { s ->
                if (result.isSuccess) {
                    val env = result.getOrThrow()
                    onDataUpdated(env)
                    s.copy(profileEnvelope = env, loading = false)
                } else s.copy(loading = false, error = result.exceptionOrNull()?.message)
            }
        }
    }

    fun deleteQuickAction(index: Int) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.deleteQuickAction(session, index)
            _uiState.update { s ->
                if (result.isSuccess) {
                    val env = result.getOrThrow()
                    onDataUpdated(env)
                    s.copy(profileEnvelope = env, loading = false)
                } else s.copy(loading = false, error = result.exceptionOrNull()?.message)
            }
        }
    }

    // ── JSON IMPORT ───────────────────────────────────────────────────────────

    fun importFromJson(jsonString: String) {
        val session  = _uiState.value.session ?: return
        val settings = _uiState.value.profileEnvelope?.settings ?: Settings()
        runCatching {
            val arr  = JSONArray(jsonString)
            val txns = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Transaction(
                    id              = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    date            = obj.optString("date").ifBlank { Instant.now().toString() },
                    amount          = obj.optInt("amount", 0),
                    source          = obj.optString("source", "Import"),
                    previousBalance = obj.optInt("previous_balance", 0)
                )
            }
            txns
        }.onFailure {
            _uiState.update { s -> s.copy(error = "Invalid backup file: ${it.message}") }
            return
        }.onSuccess { txns ->
            viewModelScope.launch {
                _uiState.update { it.copy(loading = true, error = null) }
                val result = repo.importData(session, txns, settings)
                _uiState.update { s ->
                    if (result.isSuccess) {
                        val env = result.getOrThrow()
                        onDataUpdated(env)
                        s.copy(profileEnvelope = env, loading = false)
                    } else s.copy(loading = false, error = result.exceptionOrNull()?.message)
                }
            }
        }
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    fun loadAdmin() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val stats = repo.loadAdminStats()
            val users = repo.loadAdminUsers()
            _uiState.update { s ->
                s.copy(
                    adminStats = stats.getOrNull(),
                    adminUsers = users.getOrDefault(emptyList()),
                    loading    = false,
                    error      = stats.exceptionOrNull()?.message ?: users.exceptionOrNull()?.message
                )
            }
        }
    }

    fun deleteUser(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.deleteUser(userId)
            if (result.isSuccess) loadAdmin()
            else _uiState.update { it.copy(loading = false, error = result.exceptionOrNull()?.message) }
        }
    }

    private fun loadProfiles() {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            val profiles = repo.listProfiles(session)
            if (profiles.isSuccess) _uiState.update { it.copy(profiles = profiles.getOrThrow(), loading = false) }
        }
    }

    // ── Post-update side effects ──────────────────────────────────────────────

    private fun onDataUpdated(env: ProfileEnvelope) {
        // 1. Update home screen widget
        viewModelScope.launch(Dispatchers.IO) {
            WidgetUpdater.update(getApplication())
        }

        // 2. Fire milestone notifications for newly unlocked achievements
        val ctx = getApplication<Application>()
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true

        if (!hasPermission) return

        val knownKey = "known_achievements_${env.profile}"
        val knownSet = prefs.getStringSet(knownKey, emptySet()) ?: emptySet()
        val newAchievements = env.achievements.filter { it.name !in knownSet }

        newAchievements.forEach { ach ->
            NotificationHelper.notifyMilestone(ctx, ach.icon, ach.name, ach.desc)
        }

        if (newAchievements.isNotEmpty()) {
            prefs.edit()
                .putStringSet(knownKey, env.achievements.map { it.name }.toSet())
                .apply()
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

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
    val session         : UserSession?       = null,
    val loading         : Boolean            = false,
    val error           : String?            = null,
    val profileEnvelope : ProfileEnvelope?   = null,
    val profiles        : List<String>       = emptyList(),
    val adminStats      : AdminStats?        = null,
    val adminUsers      : List<AdminUserRow> = emptyList()
)
