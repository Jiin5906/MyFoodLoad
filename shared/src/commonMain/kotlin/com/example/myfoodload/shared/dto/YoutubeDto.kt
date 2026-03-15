package com.example.myfoodload.shared.dto

/**
 * YouTube 좋아요 수집 요청 DTO.
 * Android → Backend: 사용자의 YouTube OAuth access token 전달.
 */
data class YoutubeIngestRequest(
    val youtubeAccessToken: String = "",
)

/**
 * YouTube 수집 결과 DTO.
 * Backend → Android: 수집 완료 통계 반환.
 *
 * Gemini 피드백 반영 (공유 분석 캐시):
 * - cachedVideos: 다른 사용자가 이미 분석 완료 → YouTube API·LLM 호출 건너뜀
 */
data class IngestionResultDto(
    val totalFetched: Int = 0,
    val newVideos: Int = 0,
    val cachedVideos: Int = 0,
)
