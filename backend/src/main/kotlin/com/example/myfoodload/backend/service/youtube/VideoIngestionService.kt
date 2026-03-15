package com.example.myfoodload.backend.service.youtube

import com.example.myfoodload.backend.model.entity.UserVideoLike
import com.example.myfoodload.backend.model.entity.UserVideoLikeId
import com.example.myfoodload.backend.model.entity.VideoMetadata
import com.example.myfoodload.backend.repository.UserVideoLikeRepository
import com.example.myfoodload.backend.repository.VideoMetadataRepository
import com.example.myfoodload.shared.dto.IngestionResultDto
import com.github.benmanes.caffeine.cache.Cache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

/**
 * YouTube 좋아요 영상 수집·저장 서비스.
 *
 * Gemini 피드백 반영 — 공유 분석 캐시:
 * 이미 다른 사용자가 수집한 video_id는 YouTube API 재호출 없이 건너뜀.
 * → YouTube API Quota(하루 10,000 유닛) 절약.
 *
 * GlobalScope 금지 → withContext(Dispatchers.IO) 사용.
 */
@Service
class VideoIngestionService(
    private val youTubeApiClient: YouTubeApiClient,
    private val videoMetadataRepository: VideoMetadataRepository,
    private val userVideoLikeRepository: UserVideoLikeRepository,
    private val syncCooldownCache: Cache<Long, Boolean>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun ingestLikedVideos(
        userId: Long,
        youtubeAccessToken: String,
    ): IngestionResultDto =
        withContext(Dispatchers.IO) {
            // 24h 동기화 쿨다운: 같은 사용자가 짧은 간격으로 반복 호출 시 YouTube 쿼터 절약
            if (syncCooldownCache.getIfPresent(userId) != null) {
                log.info("YouTube 동기화 쿨다운 — userId=$userId, 24h 내 재요청 스킵")
                return@withContext IngestionResultDto()
            }

            log.info("YouTube 수집 시작 — userId=$userId")

            // 1. YouTube API에서 좋아요 영상 수집
            val videos = youTubeApiClient.fetchLikedVideos(youtubeAccessToken)
            val fetchedIds = videos.map { it.id }

            if (fetchedIds.isEmpty()) {
                log.info("수집된 좋아요 영상 없음 — userId=$userId")
                return@withContext IngestionResultDto()
            }

            // 2. 공유 캐시: 이미 DB에 존재하는 video_id 조회
            val existingIds = videoMetadataRepository.findExistingVideoIds(fetchedIds).toSet()
            val newItems = videos.filter { it.id !in existingIds }

            // 3. 새 영상 DB 저장
            val newEntities =
                newItems.mapNotNull { item ->
                    val snippet = item.snippet ?: return@mapNotNull null
                    VideoMetadata(
                        videoId = item.id,
                        title = snippet.title,
                        description = snippet.description,
                        tags = (snippet.tags ?: emptyList()).toMutableList(),
                        channelId = snippet.channelId,
                        channelTitle = snippet.channelTitle,
                        categoryId = snippet.categoryId,
                        publishedAt =
                            snippet.publishedAt?.let {
                                runCatching { OffsetDateTime.parse(it) }.getOrNull()
                            },
                        thumbnailUrl =
                            snippet.thumbnails?.high?.url
                                ?: snippet.thumbnails?.medium?.url
                                ?: snippet.thumbnails?.defaultThumb?.url,
                    )
                }
            videoMetadataRepository.saveAll(newEntities)

            // 4. 이 사용자의 좋아요 관계 추가 (중복 제외)
            val newLikes =
                fetchedIds
                    .filter { videoId ->
                        !userVideoLikeRepository.existsByIdUserIdAndIdVideoId(userId, videoId)
                    }.map { videoId ->
                        UserVideoLike(id = UserVideoLikeId(userId = userId, videoId = videoId))
                    }
            userVideoLikeRepository.saveAll(newLikes)

            val result =
                IngestionResultDto(
                    totalFetched = fetchedIds.size,
                    newVideos = newEntities.size,
                    cachedVideos = existingIds.size,
                )
            syncCooldownCache.put(userId, true)
            log.info("YouTube 수집 완료 — userId=$userId, result=$result")
            result
        }
}
