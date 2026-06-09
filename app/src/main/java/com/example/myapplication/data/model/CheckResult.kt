package com.example.myapplication.data.model

import com.google.gson.annotations.SerializedName

data class WordMatch(
    @SerializedName("position") val position: Int,
    @SerializedName("correct_word") val correctWord: String?,
    @SerializedName("input_word") val inputWord: String?,
    @SerializedName("exact_match") val exactMatch: Boolean? = null,
    @SerializedName("missing_or_extra") val missingOrExtra: Boolean? = null,
    @SerializedName("is_match") val isMatch: Boolean? = null,
    @SerializedName("status") val status: String? = null
)

data class CheckResult(
    @SerializedName("is_correct") val isCorrect: Boolean? = null,
    @SerializedName("exact_match") val exactMatch: ExactMatch? = null,
    @SerializedName("word_level_match") val wordLevelMatch: WordLevelMatch? = null,
    @SerializedName("word_level_details") val wordLevelDetails: List<WordMatch>? = null,
    @SerializedName("details") val details: List<WordMatch>? = null,
    @SerializedName("result_details") val resultDetails: List<WordMatch>? = null,
    @SerializedName("word_matches") val directWordMatches: List<WordMatch>? = null,
    @SerializedName("overall_stats") val overallStats: OverallStats? = null,
    @SerializedName("processed") val processed: ProcessedText? = null,
    @SerializedName("similarity") val similarity: Double? = null
)

data class ExactMatch(
    @SerializedName("exact_match") val exactMatch: Boolean,
    @SerializedName("is_similar") val isSimilar: Boolean,
    @SerializedName("normalized_match") val normalizedMatch: Boolean,
    @SerializedName("similarity_ratio") val similarityRatio: Double,
    @SerializedName("normalized_correct") val normalizedCorrect: String?,
    @SerializedName("normalized_input") val normalizedInput: String?
)

data class WordLevelMatch(
    @SerializedName("all_words_exact_match") val allWordsExactMatch: Boolean,
    @SerializedName("word_matches") val wordMatches: List<WordMatch>
)

data class OverallStats(
    @SerializedName("is_match") val isMatch: Boolean,
    @SerializedName("similarity_ratio") val similarityRatio: Double,
    @SerializedName("processed_correct") val processedCorrect: String,
    @SerializedName("processed_input") val processedInput: String
)

data class ProcessedText(
    @SerializedName("correct") val correct: Any?, // Can be String or List<String>
    @SerializedName("input") val input: Any?
)
