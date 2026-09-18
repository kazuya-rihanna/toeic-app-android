package com.example.myapplication.data.model

import com.google.gson.annotations.SerializedName

data class Sentence(
    @SerializedName("id") val id: String,
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
    @SerializedName("meaning_japanese") val meaningJapanese: String? = null
)
