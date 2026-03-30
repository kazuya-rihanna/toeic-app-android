package com.example.myapplication.data.remote

import com.example.myapplication.data.model.*
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ToeicApiService {

    @GET("/vocabulary/page")
    suspend fun getVocabularyPage(
        @Query("collection") collection: String,
        @Query("page") page: Int
    ): Response<VocabularyResponse>

    @GET("/vocabulary/first")
    suspend fun getFirstVocabulary(
        @Query("collection") collection: String,
        @Query("userId") userId: String
    ): Response<VocabularyResponse>

    @GET("/vocabulary/next")
    suspend fun getNextVocabulary(
        @Query("collection") collection: String,
        @Query("lastId") lastId: String
    ): Response<VocabularyResponse>

    @GET("/vocabulary/prev")
    suspend fun getPrevVocabulary(
        @Query("collection") collection: String,
        @Query("firstId") firstId: String
    ): Response<VocabularyResponse>

    @POST("/check")
    suspend fun checkText(
        @Query("userId") userId: String,
        @Body request: CheckRequest
    ): Response<CheckResult>

    @GET("/progress/{userId}")
    suspend fun getProgress(
        @Path("userId") userId: String
    ): Response<ProgressResponse>

    @POST("/progress")
    suspend fun updateProgress(
        @Body request: ProgressUpdateRequest
    ): Response<ProgressUpdateResponse>

    @POST("/tts")
    suspend fun getTTS(
        @Body request: TTSRequest
    ): Response<ResponseBody>

    @GET("/chart-data/{userId}")
    suspend fun getChartData(
        @Path("userId") userId: String,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null
    ): Response<ChartDataResponse>
}

data class VocabularyResponse(
    @SerializedName("docs") val docs: List<Sentence>? = null,
    @SerializedName("firstId") val firstId: String? = null,
    @SerializedName("lastId") val lastId: String? = null,
    @SerializedName("total_pages") val totalPages: Int? = null,
    @SerializedName("current_page") val currentPage: Int? = null
)

data class CheckRequest(
    @SerializedName("correct_text") val correctText: String,
    @SerializedName("input_text") val inputText: String,
    @SerializedName("userId") val userId: String,
    @SerializedName("options") val options: Map<String, Boolean>? = null
)

data class ProgressResponse(
    @SerializedName("progress") val progress: Map<String, Progress>? = null,
    @SerializedName("settings") val settings: Map<String, Any>? = null,
    @SerializedName("daily_stats") val dailyStats: Map<String, Any>? = null
)

data class ProgressUpdateRequest(
    @SerializedName("userId") val userId: String,
    @SerializedName("collectionId") val collectionId: String,
    @SerializedName("page") val page: Int,
    @SerializedName("inputMethod") val inputMethod: String
)

data class ProgressUpdateResponse(
    @SerializedName("status") val status: String,
    @SerializedName("progress") val progress: Progress?,
    @SerializedName("score_updated") val scoreUpdated: Boolean?
)

data class TTSRequest(
    @SerializedName("text") val text: String,
    @SerializedName("voice") val voice: String = "en-US-Standard-C"
)

data class ChartDataResponse(
    @SerializedName("chart_data") val chartData: List<ChartEntry>? = null
)

data class ChartEntry(
    @SerializedName("date") val date: String,
    @SerializedName("stt") val stt: Int,
    @SerializedName("livescribe") val livescribe: Int,
    @SerializedName("typing") val typing: Int
)
