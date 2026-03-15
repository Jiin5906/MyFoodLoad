package com.example.myfoodload.data.remote

import com.example.myfoodload.shared.dto.UserPreferenceDto
import com.example.myfoodload.shared.response.ApiResponse
import retrofit2.http.GET
import retrofit2.http.POST

interface LlmAnalysisApiService {

    @POST("api/llm/analyze")
    suspend fun analyze(): ApiResponse<UserPreferenceDto>

    @GET("api/llm/preferences")
    suspend fun getPreferences(): ApiResponse<UserPreferenceDto?>
}
