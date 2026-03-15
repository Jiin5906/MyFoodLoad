package com.example.myfoodload.shared.validation

/**
 * Frontend(Android)와 Backend(Spring Boot)가 동일하게 적용하는 검증 규칙.
 * 서버-클라이언트 간 검증 중복을 방지하기 위해 :shared에 정의.
 */
object ValidationRules {
    const val MAX_TAGS_PER_VIDEO = 10
    const val MAX_FOOD_TAGS = 20
    const val MAX_AMBIANCE_TAGS = 10
    const val SEARCH_RADIUS_MIN_METERS = 100.0
    const val SEARCH_RADIUS_MAX_METERS = 5_000.0   // 최대 5km
    const val SEARCH_RADIUS_DEFAULT_METERS = 1_000.0

    /** YouTube API 하루 Quota 소진을 막기 위한 사용자당 최대 수집 영상 수 */
    const val MAX_LIKED_VIDEOS_PER_USER = 200

    fun isValidSearchRadius(meters: Double): Boolean =
        meters in SEARCH_RADIUS_MIN_METERS..SEARCH_RADIUS_MAX_METERS

    fun isValidConfidence(value: Double): Boolean =
        value in 0.0..1.0
}
