package com.example.myfoodload.data.remote

import com.example.myfoodload.shared.dto.ExtractionJobStatusDto
import com.example.myfoodload.shared.response.ApiResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST

interface GeocodingApiService {

    @POST("api/geocoding/extract")
    suspend fun extractRestaurants(): Response<ApiResponse<String>>

    @GET("api/geocoding/status")
    suspend fun getExtractionStatus(): ApiResponse<ExtractionJobStatusDto>
}
