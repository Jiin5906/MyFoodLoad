package com.example.myfoodload.backend.service.youtube

import com.example.myfoodload.backend.exception.YouTubeAuthException
import com.example.myfoodload.backend.exception.YouTubeFetchException
import com.example.myfoodload.shared.validation.ValidationRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * YouTube Data API v3 HTTP 클라이언트.
 *
 * - liked videos: OAuth access token (Android에서 전달)
 * - 폴백 쇼츠 검색: 서버 사이드 Data API 키 (youtube.data-api-key)
 * GlobalScope 금지 → withContext(Dispatchers.IO) 사용.
 */
@Component
class YouTubeApiClient(
    @Value("\${youtube.data-api-key:}") private val dataApiKey: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient =
        RestClient
            .builder()
            .baseUrl("https://www.googleapis.com/youtube/v3")
            .build()

    companion object {
        private const val PAGE_SIZE = 50
    }

    /**
     * 사용자의 YouTube 좋아요 영상을 최대 [ValidationRules.MAX_LIKED_VIDEOS_PER_USER]개 수집.
     * YouTube API Quota 절약을 위해 maxVideos 도달 즉시 페이징 중단.
     */
    suspend fun fetchLikedVideos(
        accessToken: String,
        maxVideos: Int = ValidationRules.MAX_LIKED_VIDEOS_PER_USER,
    ): List<YouTubeVideoItem> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<YouTubeVideoItem>()
            var pageToken: String? = null

            while (result.size < maxVideos) {
                val page =
                    try {
                        fetchPage(accessToken, pageToken)
                    } catch (e: YouTubeFetchException) {
                        log.warn("YouTube 페이지 수집 중 오류 — 부분 결과 반환 (수집: {}개): {}", result.size, e.message)
                        break
                    }
                if (page == null || page.items.isEmpty()) break

                result.addAll(page.items)
                pageToken = page.nextPageToken
                if (pageToken == null) break
            }

            log.info("YouTube 좋아요 수집 완료 — totalFetched={}", result.size.coerceAtMost(maxVideos))
            result.take(maxVideos)
        }

    /**
     * 지역 키워드 기반 맛집 YouTube 쇼츠를 조회수 순으로 검색.
     *
     * YouTube의 location 파라미터는 지오태그된 영상만 검색하므로 대부분 결과 없음.
     * 대신 역지오코딩으로 얻은 지역명을 키워드로 검색 (예: "은평구 맛집").
     *
     * YouTube Data API 서버 사이드 키([dataApiKey]) 사용.
     * [dataApiKey]가 비어있으면 빈 리스트 반환 (graceful degradation).
     *
     * @param areaKeyword    검색 키워드 (예: "은평구 맛집", "서울 맛집")
     * @param maxResults     최대 결과 수 (기본 5)
     * @param publishedAfter ISO 8601 형식 기간 시작점 (예: "2025-01-01T00:00:00Z"). null 이면 기간 제한 없음.
     */
    suspend fun searchFoodShortsNearby(
        areaKeyword: String,
        maxResults: Int = 5,
        publishedAfter: String? = null,
    ): List<YouTubeSearchItem> =
        withContext(Dispatchers.IO) {
            if (dataApiKey.isBlank()) {
                log.warn("youtube.data-api-key 미설정 — 폴백 쇼츠 검색 불가")
                return@withContext emptyList()
            }
            try {
                val query = if (areaKeyword.contains("맛집")) areaKeyword else "$areaKeyword 맛집"
                val response =
                    restClient
                        .get()
                        .uri { builder ->
                            builder
                                .path("/search")
                                .queryParam("part", "id,snippet")
                                .queryParam("q", query)
                                .queryParam("type", "video")
                                .queryParam("videoDuration", "short")
                                .queryParam("order", "viewCount")
                                .queryParam("maxResults", maxResults * 3) // 여유분 (Kakao 미매칭 대비)
                                .queryParam("key", dataApiKey)
                                .apply { publishedAfter?.let { queryParam("publishedAfter", it) } }
                                .build()
                        }.retrieve()
                        .body<YouTubeSearchResponse>()
                        ?: YouTubeSearchResponse()

                log.info("YouTube 쇼츠 검색 '{}' (publishedAfter={}) → {}건", query, publishedAfter ?: "제한없음", response.items.size)
                response.items
            } catch (e: Exception) {
                log.error("YouTube 쇼츠 검색 실패: ${e.message}")
                emptyList()
            }
        }

    /**
     * 영상 ID 목록의 YouTube 조회수를 일괄 조회.
     *
     * videos.list?part=statistics&id={ids} 호출 (1 quota unit per call).
     * [dataApiKey]가 비어있거나 [videoIds]가 빈 리스트면 빈 Map 반환.
     *
     * @return videoId → viewCount 매핑 (조회 실패 ID는 포함되지 않음)
     */
    suspend fun getVideoStatistics(videoIds: List<String>): Map<String, Long> =
        withContext(Dispatchers.IO) {
            if (dataApiKey.isBlank() || videoIds.isEmpty()) return@withContext emptyMap()
            try {
                val response =
                    restClient
                        .get()
                        .uri { builder ->
                            builder
                                .path("/videos")
                                .queryParam("part", "statistics")
                                .queryParam("id", videoIds.joinToString(","))
                                .queryParam("key", dataApiKey)
                                .build()
                        }.retrieve()
                        .body<YouTubeVideosResponse>()
                        ?: YouTubeVideosResponse()

                response.items
                    .associate { item ->
                        item.id to (item.statistics?.viewCount?.toLongOrNull() ?: 0L)
                    }.also { log.debug("YouTube 통계 조회 → {}개", it.size) }
            } catch (e: Exception) {
                log.error("YouTube 영상 통계 조회 실패: ${e.message}")
                emptyMap()
            }
        }

    private fun fetchPage(
        accessToken: String,
        pageToken: String?,
    ): YouTubeLikedVideosResponse? =
        try {
            restClient
                .get()
                .uri { builder ->
                    builder
                        .path("/videos")
                        .queryParam("part", "id,snippet")
                        .queryParam("myRating", "like")
                        .queryParam("maxResults", PAGE_SIZE)
                        .apply { pageToken?.let { queryParam("pageToken", it) } }
                        .build()
                }.header("Authorization", "Bearer $accessToken")
                .retrieve()
                .body<YouTubeLikedVideosResponse>()
        } catch (e: org.springframework.web.client.HttpClientErrorException) {
            val status = e.statusCode
            val body = e.responseBodyAsString.take(200)
            log.error("YouTube API HTTP 오류 — status={}, body={}", status, body)
            // 401/403 = 토큰 만료 또는 scope 부족 → 클라이언트가 토큰을 재발급해야 함
            if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
                throw YouTubeAuthException(
                    "YouTube 토큰이 만료되었습니다 (status=$status). 앱에서 다시 연동해 주세요.",
                    status.value(),
                )
            }
            null
        } catch (e: YouTubeAuthException) {
            throw e
        } catch (e: Exception) {
            log.error("YouTube API 호출 실패 — {}: {}", e.javaClass.simpleName, e.message, e)
            throw YouTubeFetchException(
                "YouTube API 호출 실패: ${e.javaClass.simpleName}: ${e.message}",
                e,
            )
        }
}
