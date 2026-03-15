package com.example.myfoodload.shared.dto

/**
 * 맛집 추출 결과 DTO — POST /api/geocoding/extract 응답
 *
 * @param videosProcessed 이번 요청에서 처리된 영상 수
 * @param restaurantsFound Gemini가 추출한 맛집 후보 수 (confidence 0.7 이상)
 * @param restaurantsAdded DB에 신규 저장된 맛집 수 (중복 제외)
 */
data class ExtractionResultDto(
    val videosProcessed: Int = 0,
    val restaurantsFound: Int = 0,
    val restaurantsAdded: Int = 0,
    val geminiBatchFailures: Int = 0,
)

/**
 * 맛집 추출 작업 상태 DTO — GET /api/geocoding/status 응답 (Fire-and-Forget 폴링용)
 */
data class ExtractionJobStatusDto(
    val status: String = "IDLE", // IDLE, PROCESSING, COMPLETED, FAILED
    val message: String? = null,
    val result: ExtractionResultDto? = null,
)
