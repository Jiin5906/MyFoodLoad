package com.example.myfoodload.backend.controller

import com.example.myfoodload.backend.service.FallbackRecommendationService
import com.example.myfoodload.backend.service.RecommendationService
import com.example.myfoodload.shared.dto.RecommendedRestaurantDto
import com.example.myfoodload.shared.response.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/recommendations")
class RecommendationController(
    private val recommendationService: RecommendationService,
    private val fallbackRecommendationService: FallbackRecommendationService,
) {
    /**
     * 내 취향 맛집 — 좋아요한 YouTube 영상 기반 개인화 추천.
     *
     * 1차: 사용자의 좋아요 기반 선호도 매칭 추천.
     * 결과가 없으면 빈 리스트 반환 (앱에서 "취향 분석 필요" 메시지 표시).
     * ※ 폴백 없음 — 핫한 맛집 탭(GET /trending)을 별도로 제공.
     *
     * GET /api/recommendations?lat={}&lon={}&radius={}&limit={}
     * Authorization: Bearer {jwtToken}
     */
    @GetMapping
    suspend fun getRecommendations(
        @AuthenticationPrincipal userId: String,
        @RequestParam lat: Double,
        @RequestParam lon: Double,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "false") excludeVisited: Boolean,
    ): ResponseEntity<ApiResponse<List<RecommendedRestaurantDto>>> {
        val result =
            recommendationService.getRecommendations(
                userId = userId.toLong(),
                latitude = lat,
                longitude = lon,
                limit = limit,
                excludeVisited = excludeVisited,
            )

        return ResponseEntity.ok(ApiResponse(success = true, data = result))
    }

    /**
     * 핫한 맛집 — 근처 YouTube 쇼츠 조회수 상위 맛집 추천.
     *
     * YouTube Data API search.list(order=viewCount) → 카카오 로컬 매핑 → DB 캐시(7일).
     * 사용자 선호도와 무관하게 근처에서 가장 인기 있는 맛집을 반환.
     *
     * GET /api/recommendations/trending?lat={}&lon={}&limit={}
     * Authorization: Bearer {jwtToken}
     */
    @GetMapping("/trending")
    suspend fun getTrendingRecommendations(
        @AuthenticationPrincipal userId: String,
        @RequestParam lat: Double,
        @RequestParam lon: Double,
        @RequestParam(defaultValue = "15") limit: Int,
    ): ResponseEntity<ApiResponse<List<RecommendedRestaurantDto>>> {
        val result =
            fallbackRecommendationService.getFallbackRecommendations(
                latitude = lat,
                longitude = lon,
                limit = limit.coerceIn(1, 15),
            )

        return ResponseEntity.ok(ApiResponse(success = true, data = result))
    }
}
