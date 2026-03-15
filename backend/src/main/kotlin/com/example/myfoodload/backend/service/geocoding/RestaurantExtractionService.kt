package com.example.myfoodload.backend.service.geocoding

import com.example.myfoodload.backend.model.entity.Restaurant
import com.example.myfoodload.backend.model.entity.VideoMetadata
import com.example.myfoodload.backend.repository.RestaurantRepository
import com.example.myfoodload.backend.repository.UserVideoLikeRepository
import com.example.myfoodload.backend.repository.VideoMetadataRepository
import com.example.myfoodload.backend.service.llm.GeminiClient
import com.example.myfoodload.shared.dto.ExtractionResultDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException

/**
 * 맛집 추출 파이프라인 — Zero-Cost Architecture (3단계 방어선).
 *
 * ┌─────────────────────────────────────────────────────────┐
 * │  🛡️ 제1 방어선: Global Data Pooling (DB 캐시 히트)       │
 * │  → restaurant_extracted=true 영상은 LLM 호출 0회         │
 * ├─────────────────────────────────────────────────────────┤
 * │  🛡️ 제2 방어선: Regex Fast-Track (카카오 API 선제 타격)  │
 * │  → 제목에서 상호명 추출 → 카카오 검색 → LLM 호출 0회     │
 * ├─────────────────────────────────────────────────────────┤
 * │  🛡️ 제3 방어선: Async LLM Queue + Context Windowing     │
 * │  → RPM 게이트 + ±150자 자막 다이어트 → 최소 토큰 소비    │
 * └─────────────────────────────────────────────────────────┘
 */
@Service
class RestaurantExtractionService(
    private val geminiClient: GeminiClient,
    private val kakaoGeocodingClient: KakaoGeocodingClient,
    private val restaurantRepository: RestaurantRepository,
    private val videoMetadataRepository: VideoMetadataRepository,
    private val userVideoLikeRepository: UserVideoLikeRepository,
    private val llmRateLimiter: LlmRateLimiter,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val BATCH_SIZE = 3
        private const val MIN_CONFIDENCE = 0.2
        private const val FAST_TRACK_MAX_RESULTS = 3

        private val FOOD_KEYWORDS = com.example.myfoodload.backend.service.FoodKeywords.FOOD_KEYWORDS
        private val FOOD_CATEGORY_CODES = setOf("FD6", "CE7")
    }

    /**
     * 3단계 방어선 체인 파이프라인 실행.
     *
     * 비음식 필터 → 제1 방어선(Global Pooling) → 제2 방어선(Regex Fast-Track)
     * → 제3 방어선(Async LLM Queue) 순서로 필터링.
     */
    suspend fun extractAndSave(userId: Long): ExtractionResultDto =
        withContext(Dispatchers.IO) {
            val likedVideoIds = userVideoLikeRepository
                .findByIdUserId(userId)
                .map { it.id.videoId }

            if (likedVideoIds.isEmpty()) {
                log.info("좋아요 영상 없음 — userId=$userId")
                return@withContext ExtractionResultDto()
            }

            repairMissingSourceVideoLinks(likedVideoIds)

            val unextractedVideos =
                videoMetadataRepository.findUnextractedByVideoIds(likedVideoIds)

            if (unextractedVideos.isEmpty()) {
                log.info("추출할 미처리 영상 없음 — userId=$userId")
                return@withContext ExtractionResultDto()
            }

            // ── 사전 필터: 비음식 영상 즉시 스킵 ──
            val (foodVideos, nonFoodVideos) = unextractedVideos.partition { isFoodRelated(it) }
            markAsExtracted(nonFoodVideos, "비음식 영상 스킵")

            if (foodVideos.isEmpty()) {
                return@withContext ExtractionResultDto(videosProcessed = nonFoodVideos.size)
            }

            // ── 🛡️ 제1 방어선: Global Data Pooling ──
            val (pooledVideos, afterDefense1) = defense1GlobalPooling(foodVideos)

            if (afterDefense1.isEmpty()) {
                return@withContext ExtractionResultDto(
                    videosProcessed = foodVideos.size,
                    restaurantsFound = pooledVideos.size,
                )
            }

            // ── 🛡️ 제2 방어선: Regex Fast-Track ──
            val (fastTracked, afterDefense2) = defense2RegexFastTrack(afterDefense1)
            saveFastTrackResults(fastTracked)

            log.info(
                "방어선 요약 — userId={}, 풀링={}개, Fast-Track={}개, LLM대상={}개, 비음식={}개",
                userId, pooledVideos.size, fastTracked.size, afterDefense2.size, nonFoodVideos.size,
            )

            if (afterDefense2.isEmpty()) {
                return@withContext ExtractionResultDto(
                    videosProcessed = foodVideos.size,
                    restaurantsFound = fastTracked.size + pooledVideos.size,
                    restaurantsAdded = fastTracked.size,
                )
            }

            // ── 🛡️ 제3 방어선: Async LLM Queue + Context Windowing ──
            val (found, added, failures) = defense3AsyncLlmQueue(afterDefense2, userId)
            val totalFound = found + fastTracked.size + pooledVideos.size

            ExtractionResultDto(
                videosProcessed = foodVideos.size,
                restaurantsFound = totalFound,
                restaurantsAdded = added + fastTracked.size,
                geminiBatchFailures = failures,
            )
        }

    // ══════════════════════════════════════════════════════════
    //  🛡️ 제1 방어선: Global Data Pooling (DB 캐시 히트)
    // ══════════════════════════════════════════════════════════

    /**
     * DB에서 이미 추출 완료된 영상을 찾아 LLM 호출 없이 재활용.
     * 타 유저가 이미 분석한 영상이면 관계(Mapping)만 설정하고 스킵.
     */
    private fun defense1GlobalPooling(
        foodVideos: List<VideoMetadata>,
    ): Pair<List<VideoMetadata>, List<VideoMetadata>> {
        val foodVideoIds = foodVideos.map { it.videoId }
        val alreadyExtracted = restaurantRepository
            .findAllBySourceVideoIdIn(foodVideoIds)
            .associateBy { it.sourceVideoId }
        val (pooled, newVideos) = foodVideos.partition {
            alreadyExtracted.containsKey(it.videoId)
        }
        markAsExtracted(pooled, "글로벌 데이터 풀링 — LLM 스킵")
        return pooled to newVideos
    }

    // ══════════════════════════════════════════════════════════
    //  🛡️ 제2 방어선: Regex Fast-Track (카카오 API 선제 타격)
    // ══════════════════════════════════════════════════════════

    /**
     * 영상 제목에서 상호명을 정규식으로 추출 → 카카오 키워드 검색.
     * 음식점(FD6/CE7) 결과가 1~3건이면 정답으로 간주, LLM 호출 0회.
     */
    private suspend fun defense2RegexFastTrack(
        videos: List<VideoMetadata>,
    ): Pair<List<Pair<VideoMetadata, KakaoPlace>>, List<VideoMetadata>> {
        val fastTracked = mutableListOf<Pair<VideoMetadata, KakaoPlace>>()
        val llmNeeded = mutableListOf<VideoMetadata>()

        for (video in videos) {
            val searchQuery = RegexFastTrackUtil.extractSearchQuery(video.title)
            if (searchQuery.length < 2) {
                llmNeeded.add(video)
                continue
            }

            try {
                val results = kakaoGeocodingClient.searchKeyword(searchQuery)
                val foodResults = results.filter {
                    it.categoryGroupCode in FOOD_CATEGORY_CODES
                }

                if (foodResults.size in 1..FAST_TRACK_MAX_RESULTS) {
                    fastTracked.add(video to foodResults.first())
                    log.info(
                        "🛡️2 Fast-Track 성공 — '{}' → '{}'",
                        searchQuery, foodResults.first().placeName,
                    )
                } else {
                    llmNeeded.add(video)
                    if (foodResults.isEmpty()) {
                        log.debug("Fast-Track 미매칭 — '{}' (결과 0건)", searchQuery)
                    } else {
                        log.debug("Fast-Track 모호 — '{}' (결과 {}건)", searchQuery, foodResults.size)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.debug("Fast-Track 오류 — '{}': {}", searchQuery, e.message)
                llmNeeded.add(video)
            }
        }
        return fastTracked to llmNeeded
    }

    private fun saveFastTrackResults(fastTracked: List<Pair<VideoMetadata, KakaoPlace>>) {
        for ((video, place) in fastTracked) {
            restaurantRepository.insertIgnoreDuplicate(
                name = place.placeName,
                address = place.roadAddressName.ifBlank { place.addressName },
                latitude = place.y.toDoubleOrNull(),
                longitude = place.x.toDoubleOrNull(),
                category = KakaoCategoryUtil.mapCategory(place).name,
                kakaoPlaceId = place.id.ifBlank { null },
                sourceVideoId = video.videoId,
                phone = place.phone.ifBlank { null },
                kakaoPlaceUrl = place.placeUrl.ifBlank { null },
                recommendationReason = null,
            )
            video.restaurantExtracted = true
        }
        if (fastTracked.isNotEmpty()) {
            videoMetadataRepository.saveAll(fastTracked.map { it.first })
        }
    }

    // ══════════════════════════════════════════════════════════
    //  🛡️ 제3 방어선: Async LLM Queue + Context Windowing
    // ══════════════════════════════════════════════════════════

    private data class GeminiResult(val found: Int, val added: Int, val failures: Int)

    /**
     * 1, 2방어선을 통과한 영상만 Gemini API로 전송.
     * RPM 게이트(Semaphore + delay)로 429 방지.
     * Context Windowing으로 자막 ±150자만 전달.
     */
    private suspend fun defense3AsyncLlmQueue(
        videos: List<VideoMetadata>,
        userId: Long,
    ): GeminiResult {
        var found = 0
        var added = 0
        var failures = 0

        log.info("🛡️3 LLM Queue 진입 — {}개 영상, 배치크기={}", videos.size, BATCH_SIZE)

        for (batch in videos.chunked(BATCH_SIZE)) {
            // RPM 게이트: Semaphore 획득 + Mutex 기반 cooldown
            llmRateLimiter.acquire(userId)
            try {
                val batchText = buildBatchVideoText(batch)
                val videoMap = batch.associateBy { it.videoId }

                var isQuotaExhausted = false
                val extraction = runCatching {
                    geminiClient.extractRestaurants(batchText)
                }.onFailure {
                    if (it is CancellationException) throw it
                    failures++
                    isQuotaExhausted = it is HttpClientErrorException.TooManyRequests
                    log.error("Gemini 배치 실패 ({}개): {}", batch.size, it.message)
                }.getOrNull()

                if (extraction == null && isQuotaExhausted) {
                    log.warn("Gemini RPD 초과 — 남은 배치 전부 스킵")
                    break
                }

                if (extraction != null) {
                    val (batchFound, batchAdded) = saveGeminiResults(extraction, videoMap)
                    found += batchFound
                    added += batchAdded
                    batch.forEach { it.restaurantExtracted = true }
                    videoMetadataRepository.saveAll(batch)
                }
            } finally {
                llmRateLimiter.release()
            }
        }
        return GeminiResult(found, added, failures)
    }

    private suspend fun saveGeminiResults(
        extraction: com.example.myfoodload.backend.service.llm.RestaurantExtractionResult,
        videoMap: Map<String, VideoMetadata>,
    ): Pair<Int, Int> {
        val validCandidates = extraction.restaurants
            .filter { it.confidence >= MIN_CONFIDENCE && videoMap.containsKey(it.videoId) }

        val apiResults = coroutineScope {
            validCandidates.map { candidate ->
                async {
                    try {
                        val places = kakaoGeocodingClient.searchKeyword(candidate.searchQuery)
                        val place = places.firstOrNull()
                            ?: if (candidate.name.isNotBlank() && candidate.name != candidate.searchQuery) {
                                kakaoGeocodingClient.searchKeyword(candidate.name).firstOrNull()
                            } else {
                                null
                            }
                        candidate to place
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log.error("Kakao 검색 실패 — query={}: {}", candidate.searchQuery, e.message)
                        candidate to null
                    }
                }
            }.awaitAll()
        }

        val found = apiResults.count { it.second != null }
        val placeIds = apiResults.mapNotNull { it.second?.id?.takeIf { id -> id.isNotBlank() } }
        val existingMap = if (placeIds.isNotEmpty()) {
            restaurantRepository.findAllByKakaoPlaceIdIn(placeIds).associateBy { it.kakaoPlaceId }
        } else {
            emptyMap()
        }

        var added = 0
        val entitiesToUpdate = mutableListOf<Restaurant>()

        for ((candidate, place) in apiResults) {
            if (place == null) continue
            val sourceVideo = videoMap[candidate.videoId] ?: continue
            val existing = if (place.id.isNotBlank()) existingMap[place.id] else null

            if (existing != null) {
                if (existing.sourceVideoId == null) {
                    existing.sourceVideoId = sourceVideo.videoId
                    entitiesToUpdate.add(existing)
                }
                continue
            }

            restaurantRepository.insertIgnoreDuplicate(
                name = place.placeName,
                address = place.roadAddressName.ifBlank { place.addressName },
                latitude = place.y.toDoubleOrNull(),
                longitude = place.x.toDoubleOrNull(),
                category = KakaoCategoryUtil.mapCategory(place).name,
                kakaoPlaceId = place.id.ifBlank { null },
                sourceVideoId = sourceVideo.videoId,
                phone = place.phone.ifBlank { null },
                kakaoPlaceUrl = place.placeUrl.ifBlank { null },
                recommendationReason = candidate.recommendationReason?.takeIf { it.isNotBlank() },
            )
            added++
        }

        if (entitiesToUpdate.isNotEmpty()) restaurantRepository.saveAll(entitiesToUpdate)
        return found to added
    }

    // ══════════════════════════════════════════════════════════
    //  공통 유틸리티
    // ══════════════════════════════════════════════════════════

    private fun repairMissingSourceVideoLinks(likedVideoIds: List<String>) {
        val allMetadatas = videoMetadataRepository.findAllByVideoIdIn(likedVideoIds)
        val existingByVideoId = restaurantRepository
            .findAllBySourceVideoIdIn(likedVideoIds)
            .associateBy { it.sourceVideoId }
        val extractedWithNoLink = allMetadatas
            .filter { it.restaurantExtracted && existingByVideoId[it.videoId] == null }
            .map { it.videoId }
        if (extractedWithNoLink.isNotEmpty()) {
            log.info("source_video_id 미연결 영상 재추출 초기화 — {}개", extractedWithNoLink.size)
            videoMetadataRepository.resetExtractedStatusByVideoIds(extractedWithNoLink)
        }
    }

    private fun markAsExtracted(videos: List<VideoMetadata>, reason: String) {
        if (videos.isNotEmpty()) {
            videos.forEach { it.restaurantExtracted = true }
            videoMetadataRepository.saveAll(videos)
            log.info("{} — {}개 처리", reason, videos.size)
        }
    }

    private fun isFoodRelated(video: VideoMetadata): Boolean {
        val titleLower = video.title.lowercase()
        val descLower = video.description?.lowercase() ?: ""
        val tagsLower = video.tags.joinToString(" ") { it.lowercase() }
        return FOOD_KEYWORDS.any { kw ->
            titleLower.contains(kw) || descLower.contains(kw) || tagsLower.contains(kw)
        }
    }

    private fun buildBatchVideoText(videos: List<VideoMetadata>): String =
        buildString {
            append("다음 YouTube 영상들에서 언급된 실제 맛집을 추출해주세요.\n")
            append("각 영상의 video_id를 정확히 그대로 응답에 포함하세요.\n\n")
            videos.forEach { video ->
                append("[video_id: ${video.videoId}]\n")
                append("제목: ${video.title}\n")
                video.channelTitle?.let { append("채널: $it\n") }
                video.description?.takeIf { it.isNotBlank() }?.let {
                    append("설명: ${it.take(120)}\n")
                }
                video.transcript?.takeIf { it.isNotBlank() }?.let {
                    append("자막(우선 참조): ${TranscriptWindowingUtil.extractRelevantTranscript(it)}\n")
                } ?: run { append("자막: 없음 (제목/설명에서만 추출)\n") }
                append("\n")
            }
        }
}
