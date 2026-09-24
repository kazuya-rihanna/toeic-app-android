package com.example.myapplication.data.dictation

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

data class DictationItemRecord(
    val page: Int,
    val sentence: String,
    val timeSec: Float,
    val timestamp: Long = System.currentTimeMillis()
)

data class DailyDictationSummary(
    val date: String,
    val count: Int,
    val totalTimeSec: Float,
    val avgTimeSec: Float,
    val fastestTimeSec: Float,
    val items: List<DictationItemRecord>
)

@Singleton
class DictationLogManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("dictation_logs_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private fun getTodayDateString(): String = dateFormat.format(Date())

    @Synchronized
    fun getTodaySummary(): DailyDictationSummary {
        val today = getTodayDateString()
        val key = "summary_$today"
        val json = prefs.getString(key, null)
        if (json != null) {
            try {
                return gson.fromJson(json, DailyDictationSummary::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return DailyDictationSummary(
            date = today,
            count = 0,
            totalTimeSec = 0f,
            avgTimeSec = 0f,
            fastestTimeSec = 0f,
            items = emptyList()
        )
    }

    @Synchronized
    fun recordAnswer(page: Int, sentence: String, timeSec: Float): DailyDictationSummary {
        val today = getTodayDateString()
        val current = getTodaySummary()

        val cleanTime = (Math.round(timeSec * 10f) / 10f).coerceAtLeast(0.1f)
        val newItem = DictationItemRecord(
            page = page,
            sentence = sentence.trim(),
            timeSec = cleanTime
        )

        val updatedItems = current.items + newItem
        val newCount = updatedItems.size
        val newTotalSec = updatedItems.map { it.timeSec }.sum()
        val roundedTotalSec = Math.round(newTotalSec * 10f) / 10f
        val newAvgSec = Math.round((newTotalSec / newCount) * 10f) / 10f
        val newFastestSec = updatedItems.minOf { it.timeSec }

        val newSummary = DailyDictationSummary(
            date = today,
            count = newCount,
            totalTimeSec = roundedTotalSec,
            avgTimeSec = newAvgSec,
            fastestTimeSec = newFastestSec,
            items = updatedItems
        )

        val json = gson.toJson(newSummary)
        prefs.edit().putString("summary_$today", json).apply()
        return newSummary
    }

    fun formatDuration(totalSeconds: Float): String {
        val secInt = totalSeconds.toInt()
        val minutes = secInt / 60
        val remainingSec = secInt % 60
        return if (minutes > 0) {
            "${minutes}m ${remainingSec}s"
        } else {
            "${totalSeconds}s"
        }
    }

    fun formatTaskTitle(summary: DailyDictationSummary): String {
        val countText = if (summary.count == 1) "1 item" else "${summary.count} items"
        val totalTimeFormatted = formatDuration(summary.totalTimeSec)
        return if (summary.count > 1) {
            "✅ TOEIC Dictation: $countText • Total $totalTimeFormatted (Avg: ${summary.avgTimeSec}s)"
        } else {
            "✅ TOEIC Dictation: $countText • Total $totalTimeFormatted"
        }
    }

    fun formatTaskNotes(summary: DailyDictationSummary): String {
        val totalTimeFormatted = formatDuration(summary.totalTimeSec)
        val fastestPage = summary.items.minByOrNull { it.timeSec }?.page

        val sb = StringBuilder()
        sb.append("【Today's TOEIC Dictation Summary】\n")
        sb.append("• Date: ${summary.date}\n")
        sb.append("• Completed: ${summary.count} questions\n")
        sb.append("• Total Study Time: $totalTimeFormatted (${summary.totalTimeSec}s)\n")
        sb.append("• Average Speed: ${summary.avgTimeSec}s\n")
        if (fastestPage != null) {
            sb.append("• Fastest Speed: ${summary.fastestTimeSec}s (Page $fastestPage)\n")
        }
        sb.append("\n[Question Details]\n")
        summary.items.forEachIndexed { index, item ->
            val timeStr = timeFormat.format(Date(item.timestamp))
            sb.append("${index + 1}. Page ${item.page} (${item.timeSec}s) at $timeStr\n")
            sb.append("   \"${item.sentence}\"\n")
        }
        return sb.toString()
    }
}
