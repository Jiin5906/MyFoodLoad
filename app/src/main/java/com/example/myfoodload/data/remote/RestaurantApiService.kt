package com.example.myfoodload.data.remote

import com.example.myfoodload.shared.dto.RestaurantDto
import com.example.myfoodload.shared.response.ApiResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface RestaurantApiService {

    @GET("api/restaurants/{id}")
    suspend fun getRestaurant(@Path("id") id: Long): ApiResponse<RestaurantDto>

    /** GET /api/restaurants?q=&category= — 검색 화면용 */
    @GET("api/restaurants")
    suspend fun searchRestaurants(
        @Query("q") keyword: String? = null,
        @Query("category") category: String? = null,
    ): ApiResponse<List<RestaurantDto>>
}
