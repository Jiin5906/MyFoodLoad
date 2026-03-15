package com.example.myfoodload.shared.dto

/**
 * 사용자 음식 선호도 프로파일 공유 DTO
 *
 * LLM JSON Schema (Function Calling) 응답 포맷과 1:1 대응.
 * 환각 방지: 모든 선택 필드는 nullable로 선언.
 */
data class UserPreferenceDto(
    val userId: String = "",
    val foodTags: List<FoodTagDto> = emptyList(),   // tag + score (Phase 7 스코어링 활용)
    val ambianceTags: List<String> = emptyList(),   // 예: ["조용한", "혼밥가능"]
    val priceRange: PriceRange? = null,             // null = LLM이 판단 불가
    val confidence: Double? = null,                 // 0.0 ~ 1.0
)

data class FoodTagDto(
    val tag: String = "",
    val score: Double = 0.0,   // 해당 태그의 선호 강도
)
