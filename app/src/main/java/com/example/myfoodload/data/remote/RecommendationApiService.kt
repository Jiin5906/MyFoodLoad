package com.example.myfoodload.data.remote

import com.example.myfoodload.shared.dto.RecommendedRestaurantDto
import com.example.myfoodload.shared.response.ApiResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface RecommendationApiService {

    /** 내 취향 맛집 — 좋아요 영상 기반 전국 단위 추천 */
    @GET("api/recommendations")
    suspend fun getRecommendations(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("limit") limit: Int,
        @Query("excludeVisited") excludeVisited: Boolean = false,
    ): ApiResponse<List<RecommendedRestaurantDto>>

    /** 핫한 맛집 — 주변 YouTube 쇼츠 조회수 상위 맛집 */
    @GET("api/recommendations/trending")
    suspend fun getTrendingRecommendations(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("limit") limit: Int,
    ): ApiResponse<List<RecommendedRestaurantDto>>
}
