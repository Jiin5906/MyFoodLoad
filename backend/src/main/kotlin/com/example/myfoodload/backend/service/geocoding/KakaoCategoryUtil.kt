package com.example.myfoodload.backend.service.geocoding

import com.example.myfoodload.shared.dto.CategoryType

/** Kakao Local API 카테고리 매핑 유틸리티. */
object KakaoCategoryUtil {

    private val FOOD_CATEGORY_CODES = setOf("FD6", "CE7")

    fun isFoodCategory(place: KakaoPlace): Boolean =
        place.categoryGroupCode in FOOD_CATEGORY_CODES

    fun mapCategory(place: KakaoPlace): CategoryType {
        if (place.categoryGroupCode == "CE7") return CategoryType.CAFE
        val name = place.categoryName.lowercase()
        return when {
            name.contains("한식") || name.contains("한정식") || name.contains("백반") || name.contains("국밥") -> CategoryType.KOREAN
            name.contains("일식") || name.contains("일본") || name.contains("스시") || name.contains("라멘") || name.contains("오마카세") || name.contains("이자카야") || name.contains("돈카츠") || name.contains("우동") || name.contains("소바") -> CategoryType.JAPANESE
            name.contains("중식") || name.contains("중국") || name.contains("중화") -> CategoryType.CHINESE
            name.contains("양식") || name.contains("이탈리아") || name.contains("피자") || name.contains("파스타") || name.contains("프랑스") || name.contains("스테이크") || name.contains("브런치") || name.contains("버거") || name.contains("햄버거") -> CategoryType.WESTERN
            name.contains("카페") || name.contains("디저트") || name.contains("베이커리") || name.contains("빵") || name.contains("제과") -> CategoryType.CAFE
            else -> CategoryType.UNKNOWN
        }
    }

    /**
     * Kakao 카테고리명에서 의미 있는 태그 추출.
     * 예) "음식점 > 한식 > 순대국밥" → ["한식", "순대국밥"]
     */
    fun extractTagsFromCategory(categoryName: String): List<String> {
        val excluded = setOf("음식점", "음식", "식음료")
        return categoryName
            .split(">")
            .map { it.trim() }
            .filter { it.isNotBlank() && it !in excluded }
            .take(3)
    }
}
