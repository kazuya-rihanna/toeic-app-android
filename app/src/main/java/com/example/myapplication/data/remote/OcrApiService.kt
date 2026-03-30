package com.example.myapplication.data.remote

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface OcrApiService {
    @Multipart
    @POST("/api/from-dropbox")
    suspend fun uploadDrawing(
        @Part file: MultipartBody.Part
    ): Response<OcrResponse>
}

data class OcrResponse(
    val text: String,
    val status: String? = null
)
