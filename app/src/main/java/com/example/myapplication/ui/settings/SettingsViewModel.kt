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
    private val dictationLogManager: DictationLogManager
) : ViewModel() {

    val connectedEmail = googleTasksManager.connectedAccountEmail
    val isSyncEnabled = googleTasksManager.isSyncEnabled
    val isSyncing = googleTasksManager.isSyncing
    val lastSyncTime = googleTasksManager.lastSyncTime

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage = _syncMessage.asStateFlow()

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
                // Perform initial sync of today's summary if available
                syncNow()
            }
        } catch (e: ApiException) {
            _syncMessage.value = "Google Sign-In failed: status code ${e.statusCode}"
            android.util.Log.e("SettingsViewModel", "Sign-in error", e)
        }
    }

    fun setSyncEnabled(enabled: Boolean) {
        googleTasksManager.setSyncEnabled(enabled)
    }

    fun disconnect() {
        googleTasksManager.disconnect()
        _syncMessage.value = "Disconnected from Google Tasks"
    }

    fun syncNow() {
        viewModelScope.launch {
            val summary = dictationLogManager.getTodaySummary()
            if (summary.count <= 0) {
                _syncMessage.value = "No dictation items solved today yet"
                return@launch
            }
            val result = googleTasksManager.syncTodaySummary(summary)
            if (result.isSuccess) {
                _syncMessage.value = "Synced successfully to Google Tasks!"
            } else {
                _syncMessage.value = "Sync failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun clearMessage() {
        _syncMessage.value = null
    }
}
