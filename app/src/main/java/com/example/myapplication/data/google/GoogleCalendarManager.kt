package com.example.myapplication.data.google

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.myapplication.data.dictation.DailyDictationSummary
import com.example.myapplication.data.dictation.DictationLogManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleCalendarManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dictationLogManager: DictationLogManager
) {
    companion object {
        private const val TAG = "GoogleCalendarManager"
        private const val TARGET_ACCOUNT = "kazuya-rihanna@outlook.jp"
        private const val TARGET_CALENDAR_NAME = "Dictation"
        private const val TITLE_PREFIX = "TOEIC Dictation"
    }

    /**
     * Checks if Calendar permissions are granted.
     */
    fun hasCalendarPermission(): Boolean {
        val readGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        return readGranted && writeGranted
    }

    /**
     * Finds the best calendar ID to write to:
     * 1. Calendar named "Dictation" for TARGET_ACCOUNT
     * 2. Primary calendar for TARGET_ACCOUNT
     * 3. Any calendar named "Dictation"
     * 4. Any primary calendar
     */
    private fun findTargetCalendarId(): Long? {
        if (!hasCalendarPermission()) return null

        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.NAME,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.IS_PRIMARY
        )

        var dictationAccountId: Long? = null
        var primaryAccountId: Long? = null
        var anyDictationId: Long? = null
        var anyPrimaryId: Long? = null

        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            null
        ) ?: return null

        cursor.use {
            val idCol = it.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val nameCol = it.getColumnIndexOrThrow(CalendarContract.Calendars.NAME)
            val dispNameCol = it.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accCol = it.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            val primCol = it.getColumnIndexOrThrow(CalendarContract.Calendars.IS_PRIMARY)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val name = it.getString(nameCol) ?: ""
                val dispName = it.getString(dispNameCol) ?: ""
                val acc = it.getString(accCol) ?: ""
                val isPrim = it.getInt(primCol) == 1

                val isDictation = name.equals(TARGET_CALENDAR_NAME, ignoreCase = true) ||
                        dispName.equals(TARGET_CALENDAR_NAME, ignoreCase = true)

                if (acc.equals(TARGET_ACCOUNT, ignoreCase = true)) {
                    if (isDictation) {
                        dictationAccountId = id
                        break // Highest priority match found!
                    }
                    if (isPrim && primaryAccountId == null) {
                        primaryAccountId = id
                    }
                }

                if (isDictation && anyDictationId == null) {
                    anyDictationId = id
                }
                if (isPrim && anyPrimaryId == null) {
                    anyPrimaryId = id
                }
            }
        }

        return dictationAccountId ?: primaryAccountId ?: anyDictationId ?: anyPrimaryId
    }

    /**
     * Syncs today's dictation summary directly into Google Calendar.
     * Updates an existing event for today or creates a new one.
     */
    suspend fun syncTodayEvent(
        summary: DailyDictationSummary = dictationLogManager.getTodaySummary()
    ): Result<Long> = withContext(Dispatchers.IO) {
        if (!hasCalendarPermission()) {
            return@withContext Result.failure(SecurityException("Calendar permissions not granted"))
        }

        if (summary.count <= 0) {
            return@withContext Result.failure(IllegalStateException("No completed dictation items to sync today"))
        }

        val calendarId = findTargetCalendarId()
            ?: return@withContext Result.failure(IllegalStateException("No suitable Google Calendar found"))

        try {
            // Determine start and end of today in local timezone
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val todayStart = cal.timeInMillis

            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val todayEnd = cal.timeInMillis

            // Format event title & description
            val totalMin = (summary.totalTimeSec / 60).toInt()
            val totalSec = (summary.totalTimeSec % 60).toInt()
            val totalFormatted = if (totalMin > 0) "${totalMin}m ${totalSec}s" else "${summary.totalTimeSec}s"
            val title = "✅ $TITLE_PREFIX: ${summary.count} items ($totalFormatted)"

            val timeFmt = SimpleDateFormat("HH:mm", Locale.US)
            val itemsHistory = summary.items.joinToString("\n") {
                val timeStr = timeFmt.format(Date(it.timestamp))
                "• [$timeStr] ${it.timeSec}s (P.${it.page}): ${it.sentence}"
            }

            val description = buildString {
                appendLine("📊 TOEIC Dictation Log (${summary.date})")
                appendLine("• Solved: ${summary.count} items")
                appendLine("• Total Time: $totalFormatted (${summary.totalTimeSec}s)")
                appendLine("• Average Speed: ${summary.avgTimeSec}s / item")
                appendLine("• Fastest Speed: ${summary.fastestTimeSec}s")
                appendLine()
                appendLine("📝 Question History:")
                appendLine(itemsHistory)
            }

            // All-Day Event (appears in top all-day / task bar without hourly time slot):
            // Android Calendar Provider requires:
            // 1. ALL_DAY = 1
            // 2. DTSTART = midnight UTC of the date
            // 3. DTEND = midnight UTC of the next day
            // 4. EVENT_TIMEZONE = "UTC"
            val dateParts = summary.date.split("-").map { it.toInt() }
            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(dateParts[0], dateParts[1] - 1, dateParts[2], 0, 0, 0)
            }
            val startUtc = utcCal.timeInMillis
            utcCal.add(Calendar.DAY_OF_MONTH, 1)
            val endUtc = utcCal.timeInMillis

            // Check if today's event already exists (either all-day or timed)
            var existingEventId: Long? = null
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events._ID, CalendarContract.Events.TITLE),
                "${CalendarContract.Events.CALENDAR_ID} = ? AND ((${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?) OR (${CalendarContract.Events.DTSTART} = ?))",
                arrayOf(calendarId.toString(), todayStart.toString(), todayEnd.toString(), startUtc.toString()),
                null
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val titleCol = it.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                while (it.moveToNext()) {
                    val evTitle = it.getString(titleCol) ?: ""
                    if (evTitle.contains(TITLE_PREFIX)) {
                        existingEventId = it.getLong(idCol)
                        break
                    }
                }
            }

            val values = ContentValues().apply {
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, description)
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                put(CalendarContract.Events.DTSTART, startUtc)
                put(CalendarContract.Events.DTEND, endUtc)
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
            }

            val resultEventId: Long
            if (existingEventId != null) {
                // Update existing event
                val updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingEventId!!)
                context.contentResolver.update(updateUri, values, null, null)
                resultEventId = existingEventId!!
                android.util.Log.d(TAG, "Updated Google Calendar event: $title (ID: $resultEventId)")
            } else {
                // Insert new event
                val insertUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                    ?: return@withContext Result.failure(IllegalStateException("Failed to insert event into Calendar"))
                resultEventId = ContentUris.parseId(insertUri)
                android.util.Log.d(TAG, "Inserted new Google Calendar event: $title (ID: $resultEventId)")
            }

            Result.success(resultEventId)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error syncing to Google Calendar", e)
            Result.failure(e)
        }
    }
}
