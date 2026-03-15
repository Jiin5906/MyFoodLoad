package com.example.myfoodload.backend.service.youtube

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * YouTube Data API v3 응답 모델.
 * videos.list?myRating=like&part=id,snippet 응답 구조 매핑.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class YouTubeLikedVideosResponse(
    val items: List<YouTubeVideoItem> = emptyList(),
    val nextPageToken: String? = null,
    val pageInfo: YouTubePageInfo? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class YouTubeVideoItem(
    val id: String = "",
    val snippet: YouTubeSnippet? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class YouTubeSnippet(
    val title: String = "",
    val description: String? = null,
    val tags: List<String>? = null,
    val channelId: String = "",
    val channelTitle: String? = null,
    val categoryId: String? = null,
    val publishedAt: String? = null,
    val thumbnails: YouTubeThumbnails? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class YouTubeThumbnails(
    val high: YouTubeThumbnail? = null,
    val medium: YouTubeThumbnail? = null,
    @JsonProperty("default")
    val defaultThumb: YouTubeThumbnail? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class YouTubeThumbnail(
    val url: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class YouTubePageInfo(
    val totalResults: Int = 0,
    val resultsPerPage: Int = 0,
)

// ─────────────────────────────────────────────────────────────────────────────
// YouTube search.list API 응답 모델 (폴백 추천용)
// GET /youtube/v3/search?part=id,snippet&type=video&videoDuration=short&order=viewCount
// ─────────────────────────────────────────────────────────────────────────────

@JsonIgnoreProperties(ignoreUnknown = true)
data class YouTubeSearchResponse(
    val items: List<YouTubeSearchItem> = emptyList(),
    val nextPageToken: String? = null,
    val pageInfo: YouTubePageInfo? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class YouTubeSearchItem(
    val id: YouTubeSearchVideoId? = null,
    val snippet: YouTubeSnippet? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class YouTubeSearchVideoId(
    val videoId: String? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// YouTube videos.list?part=statistics API 응답 모델 (조회수 조회용)
// ─────────────────────────────────────────────────────────────────────────────

@JsonIgnoreProperties(ignoreUnknown = true)
data class YouTubeVideosResponse(
    val items: List<YouTubeVideoStatItem> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class YouTubeVideoStatItem(
    val id: String = "",
    val statistics: YouTubeVideoStatistics? = null,
)

/** YouTube API는 숫자도 문자열로 반환 (Long 오버플로 방지) */
@JsonIgnoreProperties(ignoreUnknown = true)
data class YouTubeVideoStatistics(
    val viewCount: String? = null,
    val likeCount: String? = null,
    val commentCount: String? = null,
)
