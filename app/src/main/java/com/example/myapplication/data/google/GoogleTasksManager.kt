package com.example.myapplication.data.google

import android.content.Context
import com.example.myapplication.data.dictation.DailyDictationSummary
import com.example.myapplication.data.dictation.DictationLogManager
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleTasksManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dictationLogManager: DictationLogManager
) {
    companion object {
        const val TASKS_SCOPE = "https://www.googleapis.com/auth/tasks"
        private const val PREFS_NAME = "google_tasks_prefs"
        private const val KEY_SYNC_ENABLED = "sync_enabled"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_CONNECTED_EMAIL = "connected_email"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val okHttpClient = OkHttpClient()
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _isSyncEnabled = MutableStateFlow(prefs.getBoolean(KEY_SYNC_ENABLED, true))
    val isSyncEnabled = _isSyncEnabled.asStateFlow()

    private val _connectedAccountEmail = MutableStateFlow<String?>(prefs.getString(KEY_CONNECTED_EMAIL, null))
    val connectedAccountEmail = _connectedAccountEmail.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<String?>(prefs.getString(KEY_LAST_SYNC_TIME, null))
    val lastSyncTime = _lastSyncTime.asStateFlow()

    init {
        checkCurrentSignInState()
    }

    fun getGoogleSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(TASKS_SCOPE))
            .build()
    }

    fun checkCurrentSignInState(): GoogleSignInAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        val hasScope = account != null && GoogleSignIn.hasPermissions(account, Scope(TASKS_SCOPE))
        if (hasScope && account?.email != null) {
            _connectedAccountEmail.value = account.email
            prefs.edit().putString(KEY_CONNECTED_EMAIL, account.email).apply()
            return account
        } else {
            _connectedAccountEmail.value = null
            prefs.edit().remove(KEY_CONNECTED_EMAIL).apply()
            return null
        }
    }

    fun onSignInSuccess(account: GoogleSignInAccount) {
        _connectedAccountEmail.value = account.email
        prefs.edit().putString(KEY_CONNECTED_EMAIL, account.email).apply()
        _isSyncEnabled.value = true
        prefs.edit().putBoolean(KEY_SYNC_ENABLED, true).apply()
    }

    fun disconnect() {
        val client = GoogleSignIn.getClient(context, getGoogleSignInOptions())
        client.signOut()
        _connectedAccountEmail.value = null
        _isSyncEnabled.value = false
        prefs.edit()
            .remove(KEY_CONNECTED_EMAIL)
            .putBoolean(KEY_SYNC_ENABLED, false)
            .apply()
    }

    fun setSyncEnabled(enabled: Boolean) {
        _isSyncEnabled.value = enabled
        prefs.edit().putBoolean(KEY_SYNC_ENABLED, enabled).apply()
    }

    private suspend fun getAccessToken(account: GoogleSignInAccount): String? = withContext(Dispatchers.IO) {
        try {
            val scopeStr = "oauth2:$TASKS_SCOPE"
            GoogleAuthUtil.getToken(context, account.account!!, scopeStr)
        } catch (e: Exception) {
            android.util.Log.e("GoogleTasksManager", "Failed to retrieve access token", e)
            null
        }
    }

    suspend fun syncTodaySummary(summary: DailyDictationSummary = dictationLogManager.getTodaySummary()): Result<String> = withContext(Dispatchers.IO) {
        if (!_isSyncEnabled.value) {
            return@withContext Result.failure(IllegalStateException("Google Tasks sync is disabled"))
        }

        val account = checkCurrentSignInState()
            ?: return@withContext Result.failure(IllegalStateException("Google Account not connected or missing Tasks scope"))

        if (summary.count <= 0) {
            return@withContext Result.failure(IllegalStateException("No completed dictation items to sync today"))
        }

        _isSyncing.value = true
        try {
            val token = getAccessToken(account)
                ?: return@withContext Result.failure(IllegalStateException("Failed to get Google OAuth token"))

            val title = dictationLogManager.formatTaskTitle(summary)
            val notes = dictationLogManager.formatTaskNotes(summary)
            val todayDate = summary.date // "YYYY-MM-DD"
            val dueRfc3339 = "${todayDate}T00:00:00.000Z"

            val todayTaskIdKey = "task_id_$todayDate"
            val cachedTaskId = prefs.getString(todayTaskIdKey, null)

            var targetTaskId = cachedTaskId

            // 1. If we have a cached task ID for today, verify if it still exists
            if (targetTaskId != null) {
                val checkReq = Request.Builder()
                    .url("https://tasks.googleapis.com/tasks/v1/users/@me/lists/@default/tasks/$targetTaskId")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                val resp = okHttpClient.newCall(checkReq).execute()
                if (!resp.isSuccessful) {
                    targetTaskId = null
                }
                resp.close()
            }

            // 2. If not cached, search for existing today task
            if (targetTaskId == null) {
                val listReq = Request.Builder()
                    .url("https://tasks.googleapis.com/tasks/v1/users/@me/lists/@default/tasks?showCompleted=true&showHidden=true")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                val listResp = okHttpClient.newCall(listReq).execute()
                if (listResp.isSuccessful) {
                    val bodyStr = listResp.body?.string() ?: ""
                    val json = gson.fromJson(bodyStr, JsonObject::class.java)
                    val items = json.getAsJsonArray("items")
                    if (items != null) {
                        for (item in items) {
                            val obj = item.asJsonObject
                            val itemTitle = obj.get("title")?.asString ?: ""
                            val itemDue = obj.get("due")?.asString ?: ""
                            if (itemTitle.contains("TOEIC Dictation") && (itemDue.startsWith(todayDate) || obj.get("notes")?.asString?.contains(todayDate) == true)) {
                                targetTaskId = obj.get("id")?.asString
                                break
                            }
                        }
                    }
                }
                listResp.close()
            }

            val payload = JsonObject().apply {
                addProperty("title", title)
                addProperty("notes", notes)
                addProperty("due", dueRfc3339)
                addProperty("status", "completed")
            }

            val resultTaskId: String
            if (targetTaskId != null) {
                // PATCH update existing task
                payload.addProperty("id", targetTaskId)
                val patchReq = Request.Builder()
                    .url("https://tasks.googleapis.com/tasks/v1/users/@me/lists/@default/tasks/$targetTaskId")
                    .addHeader("Authorization", "Bearer $token")
                    .patch(payload.toString().toRequestBody(jsonMediaType))
                    .build()

                val patchResp = okHttpClient.newCall(patchReq).execute()
                if (!patchResp.isSuccessful) {
                    val err = patchResp.body?.string()
                    patchResp.close()
                    return@withContext Result.failure(Exception("PATCH failed: code=${patchResp.code}, err=$err"))
                }
                resultTaskId = targetTaskId
                patchResp.close()
            } else {
                // POST create new completed task
                val postReq = Request.Builder()
                    .url("https://tasks.googleapis.com/tasks/v1/users/@me/lists/@default/tasks")
                    .addHeader("Authorization", "Bearer $token")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()

                val postResp = okHttpClient.newCall(postReq).execute()
                if (!postResp.isSuccessful) {
                    val err = postResp.body?.string()
                    postResp.close()
                    return@withContext Result.failure(Exception("POST failed: code=${postResp.code}, err=$err"))
                }
                val bodyStr = postResp.body?.string() ?: ""
                val resJson = gson.fromJson(bodyStr, JsonObject::class.java)
                resultTaskId = resJson.get("id")?.asString ?: ""
                postResp.close()
            }

            // Cache task ID for today
            if (resultTaskId.isNotEmpty()) {
                prefs.edit().putString(todayTaskIdKey, resultTaskId).apply()
            }

            val nowFormatted = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            _lastSyncTime.value = nowFormatted
            prefs.edit().putString(KEY_LAST_SYNC_TIME, nowFormatted).apply()

            android.util.Log.d("GoogleTasksManager", "Successfully synced dictation task: $title (ID: $resultTaskId)")
            return@withContext Result.success(resultTaskId)
        } catch (e: Exception) {
            android.util.Log.e("GoogleTasksManager", "Sync error", e)
            return@withContext Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }
}
