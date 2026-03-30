package com.example.myapplication.data.model

import com.google.gson.annotations.SerializedName

data class Progress(
    @SerializedName("score") val score: Map<String, Int> = emptyMap(),
    @SerializedName("correct") val correct: Map<String, Map<String, Boolean>> = emptyMap(),
    @SerializedName("current_page") val currentPage: Int = 1,
    @SerializedName("total_pages") val totalPages: Int? = null,
    @SerializedName("daily_stats") val dailyStats: Map<String, Map<String, Map<String, Int>>>? = null
)
