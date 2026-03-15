package com.example.myfoodload.shared.dto

/**
 * 위치 기반 맛집 추천 결과 DTO — GET /api/recommendations 응답
 *
 * @param restaurant       맛집 기본 정보
 * @param matchScore       사용자 선호도 태그 매칭 점수 (0.0~1.0, 높을수록 취향에 맞음)
 * @param distanceMeters   사용자 현재 위치로부터의 직선 거리 (미터)
 * @param mode             추천 모드: "PERSONALIZED"(취향 기반) | "TRENDING"(조회수 기반)
 * @param sourceVideoTitle 맛집이 소개된 YouTube 쇼츠 제목 (TRENDING 모드에서 사용)
 */
data class RecommendedRestaurantDto(
    val restaurant: RestaurantDto = RestaurantDto(),
    val matchScore: Double = 0.0,
    val distanceMeters: Double = 0.0,
    val mode: String = "PERSONALIZED",
    val sourceVideoTitle: String? = null,
)
