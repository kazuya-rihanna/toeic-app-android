package com.example.myapplication.data.model

import com.google.gson.annotations.SerializedName

data class Sentence(
    @SerializedName("id") val id: String? = null,
    @SerializedName("example") val example: String,
    @SerializedName("page") val page: Int? = null,
    @SerializedName("has_ocr_dataset") val hasOcrDataset: Boolean? = null,

    // TOEIC Master Corpus Metadata
    @SerializedName("expression") val expression: String? = null,
    @SerializedName("importance_rank") val importanceRank: String? = null,
    @SerializedName("part_label") val partLabel: String? = null,
    @SerializedName("appeared_tests") val appearedTests: String? = null,
    @SerializedName("test_count") val testCount: Int? = null,
    @SerializedName("total_count") val totalCount: Int? = null,
    @SerializedName("pos") val pos: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("category_jp") val categoryJp: String? = null,
    @SerializedName("meaning_japanese") val meaningJapanese: String? = null,

    // Spoken & Advanced Corpus Metadata (Justin Waller, etc.)
    @SerializedName("ipa") val ipa: String? = null,
    @SerializedName("selected_definition") val selectedDefinition: String? = null,
    @SerializedName("best_dictionary_example") val bestDictionaryExample: String? = null,
    @SerializedName("all_dictionary_examples") val allDictionaryExamples: List<String>? = null,
    @SerializedName("spoken_corpus_example") val spokenCorpusExample: SpokenCorpusExample? = null,
    @SerializedName("coca_rank") val cocaRank: Int? = null,
    @SerializedName("channel_occurrences") val channelOccurrences: Int? = null,
    @SerializedName("cefr_level") val cefrLevel: String? = null,
    @SerializedName("formality") val formality: String? = null
) {
    /**
     * 表示・練習対象の本文テキスト。
     * bestDictionaryExample が存在する場合は優先し、なければ従来の example を返す。
     */
    val displayExample: String
        get() = bestDictionaryExample?.takeIf { it.isNotBlank() } ?: example
}

data class SpokenCorpusExample(
    @SerializedName("source_channel") val sourceChannel: String? = null,
    @SerializedName("video_title") val videoTitle: String? = null,
    @SerializedName("video_url") val videoUrl: String? = null,
    @SerializedName("speaker_line") val speakerLine: String? = null
)
