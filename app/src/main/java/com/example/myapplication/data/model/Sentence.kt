package com.example.myapplication.data.model

import com.google.gson.annotations.SerializedName

data class Sentence(
    @SerializedName("id") val id: String,
    @SerializedName("example") val example: String,
    @SerializedName("page") val page: Int? = null,
    @SerializedName("has_ocr_dataset") val hasOcrDataset: Boolean? = null
)
