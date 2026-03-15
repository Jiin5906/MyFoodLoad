package com.example.myfoodload.shared.dto

/**
 * 맛집 정보 공유 DTO — :app(Android) ↔ :backend(Spring Boot) 간 타입 공유
 *
 * Phase 6.5 Gemini 피드백 반영:
 * - latitude/longitude 필드 포함 (Geocoding 결과 저장용)
 */
data class RestaurantDto(
    val id: Long = 0,
    val name: String = "",
    val address: String = "",
    val latitude: Double? = null,   // Kakao Geocoding API 결과 (Y값)
    val longitude: Double? = null,  // Kakao Geocoding API 결과 (X값)
    val category: CategoryType = CategoryType.UNKNOWN,
    val priceRange: PriceRange = PriceRange.UNKNOWN,
    val tags: List<String> = emptyList(),
    val rating: Double? = null,
    val thumbnailUrl: String? = null,
    val sourceVideoId: String? = null, // Phase 9: 관련 YouTube Shorts 재생용
    val viewCount: Long? = null,       // YouTube 조회수 (핫한 맛집 표시용)
    val phone: String? = null,                   // 카카오 로컬 API 전화번호
    val kakaoPlaceUrl: String? = null,           // 카카오 장소 상세 URL
    val recommendationReason: String? = null,    // Gemini 추천 이유 (B-4 스토리텔링)
)

enum class CategoryType {
    KOREAN,       // 한식
    JAPANESE,     // 일식
    CHINESE,      // 중식
    WESTERN,      // 양식
    CAFE,         // 카페/디저트
    STREET_FOOD,  // 분식/길거리
    UNKNOWN,
}

enum class PriceRange {
    LOW,      // 1만원 미만
    MEDIUM,   // 1~3만원
    HIGH,     // 3만원 이상
    UNKNOWN,
}
