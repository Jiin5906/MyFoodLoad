package com.example.myfoodload.data.remote

import com.example.myfoodload.shared.dto.FavoriteStatusDto
import com.example.myfoodload.shared.dto.RestaurantDto
import com.example.myfoodload.shared.response.ApiResponse
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface FavoritesApiService {
    @POST("api/favorites/{restaurantId}")
    suspend fun addFavorite(@Path("restaurantId") restaurantId: Long): ApiResponse<FavoriteStatusDto>

    @DELETE("api/favorites/{restaurantId}")
    suspend fun removeFavorite(@Path("restaurantId") restaurantId: Long): ApiResponse<FavoriteStatusDto>

    @GET("api/favorites")
    suspend fun getMyFavorites(): ApiResponse<List<RestaurantDto>>

    @GET("api/favorites/ids")
    suspend fun getMyFavoriteIds(): ApiResponse<Set<Long>>
}
