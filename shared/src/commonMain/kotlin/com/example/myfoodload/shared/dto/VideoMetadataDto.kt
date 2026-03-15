package com.example.myfoodload.shared.dto

/**
 * YouTube 영상 메타데이터 공유 DTO
 *
 * Gemini 피드백 반영 (Phase 5 — 공유 분석 캐시):
 * - isAnalyzed: 이미 다른 사용자에 의해 LLM 분석이 완료된 영상 여부
 *   → true이면 YouTube API & LLM 호출을 건너뛰어 Quota 절약
 */
data class VideoMetadataDto(
    val videoId: String = "",
    val title: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val channelId: String = "",
    val channelTitle: String = "",
    val categoryId: String = "",
    val publishedAt: String = "",
    val thumbnailUrl: String? = null,
    val isAnalyzed: Boolean = false,     // 공유 분석 캐시 플래그
)

data class ChannelDto(
    val channelId: String = "",
    val title: String = "",
    val description: String = "",
    val thumbnailUrl: String? = null,
)
