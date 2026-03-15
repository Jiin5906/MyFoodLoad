package com.example.myfoodload.data.remote

import com.example.myfoodload.shared.response.ApiResponse
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface VisitApiService {

    @POST("api/visits/{restaurantId}")
    suspend fun markVisited(@Path("restaurantId") restaurantId: Long): ApiResponse<Boolean>

    @DELETE("api/visits/{restaurantId}")
    suspend fun unmarkVisited(@Path("restaurantId") restaurantId: Long): ApiResponse<Boolean>

    @GET("api/visits/ids")
    suspend fun getVisitedIds(): ApiResponse<Set<Long>>
}
