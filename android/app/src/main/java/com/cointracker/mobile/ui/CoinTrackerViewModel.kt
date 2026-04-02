package com.cointracker.mobile.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cointracker.mobile.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class CoinTrackerViewModel @Inject constructor(
    private val repo: FirestoreRepository,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AppUiState())
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
        try {
            val json = JSONObject(sessionJson)
            val session = UserSession(
                userId = json.getString("userId"),
                username = json.getString("username"),
                role = json.getString("role"),
                currentProfile = json.optString("currentProfile", "Default")
            )
            _uiState.update { it.copy(session = session) }
            viewModelScope.launch {
                val valid = repo.isSessionValid()
                if (valid) { refreshData(); loadProfiles() }
                else { logout(); _uiState.update { it.copy(error = "Session expired. Please log in again.") } }
            }
        } catch (e: Exception) { logout() }
    }

    fun toggleTheme() {
        val newValue = !_isDarkMode.value
        _isDarkMode.value = newValue
        prefs.edit().putBoolean("is_dark_mode", newValue).apply()
    }

    fun register(username: String, password: String) {
        val err = validateCredentials(username, password)
        if (err != null) { _uiState.update { it.copy(error = err) }; return }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.register(username, password)
            if (result.isSuccess) login(username, password)
            else _uiState.update { it.copy(loading = false, error = result.exceptionOrNull()?.message) }
        }
    }

    fun login(username: String, password: String) {
        val err = validateCredentials(username, password)
        if (err != null) { _uiState.update { it.copy(error = err) }; return }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.login(username, password)
            if (result.isSuccess) {
                val session = result.getOrThrow()
                saveSession(session)
                _uiState.update { it.copy(session = session) }
                refreshData(); loadProfiles()
            } else _uiState.update { it.copy(loading = false, error = result.exceptionOrNull()?.message) }
        }
    }

    private fun saveSession(session: UserSession) {
        val json = JSONObject().apply {
            put("userId", session.userId); put("username", session.username)
            put("role", session.role); put("currentProfile", session.currentProfile)
        }
        prefs.edit().putString("user_session", json.toString()).apply()
    }

    fun logout() {
        prefs.edit().remove("user_session").apply()
        _uiState.value = AppUiState()
        repo.logout()
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }

    fun refreshData() {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.loadProfile(session.userId, session.currentProfile)
            _uiState.update {
                if (result.isSuccess) it.copy(profileEnvelope = result.getOrThrow(), loading = false)
                else it.copy(error = result.exceptionOrNull()?.message ?: "Failed to load data", loading = false)
            }
        }
    }

    fun switchProfile(profile: String) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.switchProfile(session, profile)
            if (result.isSuccess) {
                val updatedSession = result.getOrThrow()
                saveSession(updatedSession)
                _uiState.update { it.copy(session = updatedSession) }
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
        val err = validateTransactionAmount(amount); if (err != null) { _uiState.update { it.copy(error = err) }; return }
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.addTransaction(session, amount, source, dateIso)
            _uiState.update { state -> if (result.isSuccess) state.copy(profileEnvelope = result.getOrThrow(), loading = false) else state.copy(loading = false, error = result.exceptionOrNull()?.message) }
        }
    }

    fun updateTransaction(transactionId: String, amount: Int, source: String, dateIso: String) {
        val err = validateTransactionAmount(amount); if (err != null) { _uiState.update { it.copy(error = err) }; return }
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.updateTransaction(session, transactionId, amount, source, dateIso)
            _uiState.update { state -> if (result.isSuccess) state.copy(profileEnvelope = result.getOrThrow(), loading = false) else state.copy(loading = false, error = result.exceptionOrNull()?.message) }
        }
    }

    fun deleteTransaction(transactionId: String) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.deleteTransaction(session, transactionId)
            _uiState.update { state -> if (result.isSuccess) state.copy(profileEnvelope = result.getOrThrow(), loading = false) else state.copy(loading = false, error = result.exceptionOrNull()?.message) }
        }
    }

    fun updateSettings(settings: Settings) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.updateSettings(session, settings)
            _uiState.update { state -> if (result.isSuccess) state.copy(profileEnvelope = result.getOrThrow(), loading = false) else state.copy(loading = false, error = result.exceptionOrNull()?.message) }
        }
    }

    fun addQuickAction(action: QuickAction) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.addQuickAction(session, action)
            _uiState.update { state -> if (result.isSuccess) state.copy(profileEnvelope = result.getOrThrow(), loading = false) else state.copy(loading = false, error = result.exceptionOrNull()?.message) }
        }
    }

    fun updateQuickAction(index: Int, action: QuickAction) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.updateQuickAction(session, index, action)
            _uiState.update { state -> if (result.isSuccess) state.copy(profileEnvelope = result.getOrThrow(), loading = false) else state.copy(loading = false, error = result.exceptionOrNull()?.message) }
        }
    }

    fun deleteQuickAction(index: Int) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.deleteQuickAction(session, index)
            _uiState.update { state -> if (result.isSuccess) state.copy(profileEnvelope = result.getOrThrow(), loading = false) else state.copy(loading = false, error = result.exceptionOrNull()?.message) }
        }
    }

    fun importData(transactions: List<Transaction>, settings: Settings) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.importData(session, transactions, settings)
            _uiState.update { state -> if (result.isSuccess) state.copy(profileEnvelope = result.getOrThrow(), loading = false) else state.copy(loading = false, error = result.exceptionOrNull()?.message) }
        }
    }

    fun loadAdmin() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val stats = repo.loadAdminStats()
            val users = repo.loadAdminUsers()
            _uiState.update { state ->
                state.copy(adminStats = stats.getOrNull(), adminUsers = users.getOrDefault(emptyList()),
                    loading = false, error = stats.exceptionOrNull()?.message ?: users.exceptionOrNull()?.message)
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

    private fun validateCredentials(username: String, password: String): String? = when {
        username.isBlank() -> "Username cannot be empty"
        username.length < 3 -> "Username must be at least 3 characters"
        username.contains(" ") -> "Username cannot contain spaces"
        password.isBlank() -> "Password cannot be empty"
        password.length < 4 -> "Password must be at least 4 characters"
        else -> null
    }

    private fun validateTransactionAmount(amount: Int): String? = when {
        amount == 0 -> "Amount cannot be zero"
        kotlin.math.abs(amount) > 999_999 -> "Amount is too large (max 999,999)"
        else -> null
    }
}

data class AppUiState(
    val session: UserSession? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val profileEnvelope: ProfileEnvelope? = null,
    val profiles: List<String> = emptyList(),
    val adminStats: AdminStats? = null,
    val adminUsers: List<AdminUserRow> = emptyList()
)
