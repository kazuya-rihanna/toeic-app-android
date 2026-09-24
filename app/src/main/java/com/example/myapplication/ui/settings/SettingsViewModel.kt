package com.example.myapplication.ui.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.dictation.DictationLogManager
import com.example.myapplication.data.google.GoogleTasksManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val googleTasksManager: GoogleTasksManager,
    private val googleCalendarManager: com.example.myapplication.data.google.GoogleCalendarManager,
    private val dictationLogManager: DictationLogManager
) : ViewModel() {

    val connectedEmail = googleTasksManager.connectedAccountEmail
    val isSyncEnabled = googleTasksManager.isSyncEnabled
    val isSyncing = googleTasksManager.isSyncing
    val lastSyncTime = googleTasksManager.lastSyncTime

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage = _syncMessage.asStateFlow()

    private val _syncStatusDetail = MutableStateFlow<String?>(null)
    val syncStatusDetail = _syncStatusDetail.asStateFlow()

    fun getTodaySummary() = dictationLogManager.getTodaySummary()

    fun getGoogleSignInIntent(context: android.content.Context): Intent {
        val client = GoogleSignIn.getClient(context, googleTasksManager.getGoogleSignInOptions())
        return client.signInIntent
    }

    fun handleSignInResult(data: Intent?) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                googleTasksManager.onSignInSuccess(account)
                _syncMessage.value = "Connected as ${account.email}"
                _syncStatusDetail.value = "Connected to ${account.email}"
                // Perform initial sync of today's summary if available
                syncNow()
            }
        } catch (e: ApiException) {
            _syncMessage.value = "Google Sign-In failed: status code ${e.statusCode}"
            _syncStatusDetail.value = "Sign-In error: ${e.statusCode}"
            android.util.Log.e("SettingsViewModel", "Sign-in error", e)
        }
    }

    fun setSyncEnabled(enabled: Boolean) {
        googleTasksManager.setSyncEnabled(enabled)
    }

    fun disconnect() {
        googleTasksManager.disconnect()
        _syncMessage.value = "Disconnected"
        _syncStatusDetail.value = "Disconnected"
    }

    fun syncNow() {
        viewModelScope.launch {
            val summary = dictationLogManager.getTodaySummary()
            if (summary.count <= 0) {
                _syncMessage.value = "No dictation items solved today yet"
                _syncStatusDetail.value = "No items solved today yet"
                return@launch
            }
            val calResult = googleCalendarManager.syncTodayEvent(summary)
            val tasksResult = googleTasksManager.syncTodaySummary(summary)

            if (calResult.isSuccess || tasksResult.isSuccess) {
                val calId = calResult.getOrNull()
                val tasksId = tasksResult.getOrNull()
                val msg = "Synced to Google Calendar (Event ID: $calId)"
                _syncMessage.value = msg
                _syncStatusDetail.value = "✅ $msg"
            } else {
                val err = calResult.exceptionOrNull()?.message ?: tasksResult.exceptionOrNull()?.message
                _syncMessage.value = "Sync failed: $err"
                _syncStatusDetail.value = "❌ Sync failed: $err"
            }
        }
    }

    fun clearMessage() {
        _syncMessage.value = null
    }
}
