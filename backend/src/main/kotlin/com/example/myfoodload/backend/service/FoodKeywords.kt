package com.example.myfoodload.backend.service

/**
 * 음식 관련 키워드 공통 정의.
 *
 * RestaurantExtractionService, LlmAnalysisService에서 공유.
 * 자막 윈도우잉(Context Windowing)에서도 사용.
 */
object FoodKeywords {
    /** 음식 종류/메뉴 키워드 (lowercase) */
    val FOOD_KEYWORDS = setOf(
        "맛집", "음식", "먹방", "식당", "카페", "디저트",
        "오마카세", "파인다이닝", "라멘", "초밥", "치킨",
        "냉면", "피자", "햄버거", "국밥", "갈비", "삼겹",
        "쌀국수", "커피", "야키토리", "스시", "버거",
        "파스타", "스테이크", "맥주", "이자카야", "족발",
        "보쌈", "찜닭", "꼬치", "전골", "솥밥", "부대찌개",
        "타코", "샤브", "리뷰", "mukbang",
        "restaurant", "food", "eating", "foodie",
    )

    /** 장소/위치 키워드 — 자막 윈도우잉에서 식당명 문맥 추출용 */
    val LOCATION_KEYWORDS = setOf(
        "역", "동", "구", "로", "길", "여기", "이곳",
        "맛집", "식당", "카페", "가게", "매장", "본점", "지점",
    )

    /** 음식 + 장소 키워드 통합 (윈도우잉용) */
    val WINDOWING_KEYWORDS = FOOD_KEYWORDS + LOCATION_KEYWORDS
}
