package com.example.myapplication.domain

import com.example.myapplication.data.model.*
import com.example.myapplication.data.remote.ToeicApiService
import com.example.myapplication.data.remote.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToeicRepository @Inject constructor(
    private val apiService: ToeicApiService,
    private val ocrApiService: OcrApiService
) {
    suspend fun uploadDrawing(file: okhttp3.RequestBody): Response<OcrResponse> {
        val body = okhttp3.MultipartBody.Part.createFormData("file", "drawing.png", file)
        return ocrApiService.uploadDrawing(body)
    }

    suspend fun getVocabularyPage(collection: String, page: Int) = apiService.getVocabularyPage(collection, page)
    suspend fun getFirstVocabulary(collection: String, userId: String) = apiService.getFirstVocabulary(collection, userId)
    suspend fun getNextVocabulary(collection: String, lastId: String) = apiService.getNextVocabulary(collection, lastId)
    suspend fun getPrevVocabulary(collection: String, firstId: String) = apiService.getPrevVocabulary(collection, firstId)
    
    suspend fun checkText(userId: String, correctText: String, inputText: String, options: Map<String, Boolean>? = null) =
        apiService.checkText(userId, CheckRequest(correctText, inputText, userId, options))

    suspend fun getProgress(userId: String) = apiService.getProgress(userId)
    
    suspend fun getChartData(userId: String, startDate: String? = null, endDate: String? = null) = 
        apiService.getChartData(userId, startDate, endDate)
    
    suspend fun updateProgress(userId: String, collectionId: String, page: Int, inputMethod: String) =
        apiService.updateProgress(ProgressUpdateRequest(userId, collectionId, page, inputMethod))

    suspend fun getTTS(text: String): Response<ResponseBody> = apiService.getTTS(TTSRequest(text))
}
