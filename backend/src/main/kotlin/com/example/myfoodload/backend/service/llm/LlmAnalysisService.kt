package com.example.myfoodload.backend.service.llm

import com.example.myfoodload.backend.model.entity.FoodTagEntry
import com.example.myfoodload.backend.model.entity.UserPreference
import com.example.myfoodload.backend.repository.UserPreferenceRepository
import com.example.myfoodload.backend.repository.UserVideoLikeRepository
import com.example.myfoodload.backend.repository.VideoMetadataRepository
import com.example.myfoodload.shared.dto.FoodTagDto
import com.example.myfoodload.shared.dto.UserPreferenceDto
import com.example.myfoodload.shared.validation.ValidationRules
import com.github.benmanes.caffeine.cache.Cache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import java.time.OffsetDateTime

/**
 * YouTube 영상 메타데이터 + 자막 → Gemini 분석 → UserPreference 저장 서비스.
 *
 * 분석 흐름:
 * 1. 사용자가 좋아요 누른 영상 목록 조회
 * 2. 자막 없는 영상에 대해 timedtext API로 자막 수집 (공유 캐시)
 * 3. 메타데이터 + 자막 조합하여 Gemini 프롬프트 구성
 * 4. Gemini 2.0 Flash → JSON Schema 구조화 응답
 * 5. UserPreference 저장 (upsert)
 */
private val FOOD_KEYWORDS = com.example.myfoodload.backend.service.FoodKeywords.FOOD_KEYWORDS

@Service
class LlmAnalysisService(
    private val geminiClient: GeminiClient,
    private val captionClient: YouTubeCaptionClient,
    private val userVideoLikeRepository: UserVideoLikeRepository,
    private val videoMetadataRepository: VideoMetadataRepository,
    private val userPreferenceRepository: UserPreferenceRepository,
    private val userPreferenceCache: Cache<Long, UserPreference>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun analyzeAndSave(userId: Long): UserPreferenceDto =
        withContext(Dispatchers.IO) {
            log.info("LLM 분석 시작 — userId=$userId")

            // 1. 이 사용자가 좋아요 누른 영상 ID 목록
            val likedVideoIds =
                userVideoLikeRepository
                    .findByIdUserId(userId)
                    .map { it.id.videoId }
                    .take(ValidationRules.MAX_LIKED_VIDEOS_PER_USER)

            if (likedVideoIds.isEmpty()) {
                log.info("좋아요 영상 없음 — userId=$userId")
                return@withContext emptyPreference(userId)
            }

            // 2. 영상 메타데이터 로드
            val videos =
                videoMetadataRepository
                    .findExistingVideoIds(likedVideoIds)
                    .let { ids -> likedVideoIds.filter { it in ids } }
                    .mapNotNull { videoMetadataRepository.findByVideoId(it) }

            // 3. 자막 미수집 영상에 대해 병렬 수집 (공유 캐시 활용)
            val captionJobs =
                videos
                    .filter { it.transcript == null }
                    .map { video ->
                        async {
                            val transcript = captionClient.fetchTranscript(video.videoId)
                            if (transcript != null) {
                                video.transcript = transcript
                                videoMetadataRepository.save(video)
                                log.debug("자막 저장 완료 — videoId=${video.videoId}")
                            }
                        }
                    }
            captionJobs.awaitAll()

            // 4. 기존 선호도가 충분하면 Gemini 호출 스킵 (쿼터 절약 → Step 3에 양보)
            val existingPref = getCachedPreference(userId)
            if (existingPref != null && existingPref.foodTags.size >= 3) {
                log.info("기존 선호도 충분 (foodTags=${existingPref.foodTags.size}) — Gemini 스킵, 쿼터 절약")
                videos.forEach { it.isAnalyzed = true }
                videoMetadataRepository.saveAll(videos)
                return@withContext existingPref.toDto()
            }

            // 5. Gemini 프롬프트 구성 — 음식 관련 영상만 필터링하여 전달
            // (비음식 영상이 대다수이면 Gemini가 confidence=0 반환하는 문제 방지)
            val foodVideos =
                videos.filter { video ->
                    val titleLower = video.title.lowercase()
                    val descLower = video.description?.lowercase() ?: ""
                    val tagText = video.tags.joinToString(" ").lowercase()
                    FOOD_KEYWORDS.any { kw ->
                        titleLower.contains(kw) || descLower.contains(kw) || tagText.contains(kw)
                    }
                }
            val analysisVideos = if (foodVideos.isNotEmpty()) foodVideos else videos
            log.info("Gemini 분석 대상: 음식 관련 {}개 / 전체 {}개", foodVideos.size, videos.size)

            val videoDataText =
                buildPrompt(
                    analysisVideos.map { video ->
                        VideoAnalysisInput(
                            title = video.title,
                            channelTitle = video.channelTitle,
                            description = video.description,
                            tags = video.tags,
                            transcript = video.transcript,
                        )
                    },
                )

            // 5. Gemini 분석 — 할당량 초과(429) 시 기존 선호도 사용 또는 폴백 태그 생성
            val analysis =
                try {
                    geminiClient
                        .analyzeFoodPreference(videoDataText)
                        .also { log.info("Gemini 분석 완료 — foodTags=${it.foodTags.size}, confidence=${it.confidence}") }
                } catch (e: HttpClientErrorException.TooManyRequests) {
                    log.warn("Gemini API 할당량 초과(429) — userId=$userId")
                    val existing = getCachedPreference(userId)
                    if (existing != null && existing.foodTags.isNotEmpty()) {
                        log.info("기존 선호도 유지 — foodTags=${existing.foodTags.size}")
                        return@withContext existing.toDto()
                    }
                    // 기존 선호도도 없으면 폴백 태그로 생성
                    val fallbackTags = extractFallbackFoodTags(analysisVideos)
                    log.info("Gemini 429 + 기존 선호도 없음 → 폴백 태그 {}개 생성", fallbackTags.size)
                    FoodPreferenceAnalysis(
                        foodTags = fallbackTags,
                        ambianceTags = emptyList(),
                        priceRange = null,
                        confidence = if (fallbackTags.isNotEmpty()) 0.4 else 0.0,
                    )
                }

            // 5.5 Gemini가 빈 foodTags 반환 시 영상 제목/태그에서 폴백 추출
            val finalAnalysis =
                if (analysis.foodTags.isEmpty()) {
                    val fallbackTags = extractFallbackFoodTags(analysisVideos)
                    if (fallbackTags.isNotEmpty()) {
                        log.info("Gemini 빈 foodTags → 폴백 태그 추출 {}개", fallbackTags.size)
                        FoodPreferenceAnalysis(
                            foodTags = fallbackTags,
                            ambianceTags = analysis.ambianceTags,
                            priceRange = analysis.priceRange,
                            confidence = 0.4.coerceAtLeast(analysis.confidence),
                        )
                    } else {
                        analysis
                    }
                } else {
                    analysis
                }

            // 6. 분석된 영상을 is_analyzed = true로 마킹
            videos.forEach { it.isAnalyzed = true }
            videoMetadataRepository.saveAll(videos)

            // 7. UserPreference upsert
            val preference =
                getCachedPreference(userId)
                    ?.apply {
                        foodTags.clear()
                        foodTags.addAll(finalAnalysis.foodTags.map { FoodTagEntry(it.tag, it.score) })
                        ambianceTags.clear()
                        ambianceTags.addAll(finalAnalysis.ambianceTags)
                        priceRange = finalAnalysis.priceRange
                        confidence = finalAnalysis.confidence
                        analyzedVideoCount = videos.size
                        lastAnalyzedAt = OffsetDateTime.now()
                        updatedAt = OffsetDateTime.now()
                    }
                    ?: UserPreference(
                        userId = userId,
                        foodTags = finalAnalysis.foodTags.map { FoodTagEntry(it.tag, it.score) }.toMutableList(),
                        ambianceTags = finalAnalysis.ambianceTags.toMutableList(),
                        priceRange = finalAnalysis.priceRange,
                        confidence = finalAnalysis.confidence,
                        analyzedVideoCount = videos.size,
                        lastAnalyzedAt = OffsetDateTime.now(),
                    )

            val saved = userPreferenceRepository.save(preference)
            userPreferenceCache.put(userId, saved)
            log.info("UserPreference 저장 완료 — userId=$userId")
            saved.toDto()
        }

    companion object {
        private val FALLBACK_TAG_MAP =
            mapOf(
                "한식" to "한식",
                "일식" to "일식",
                "중식" to "중식",
                "양식" to "양식",
                "라멘" to "라멘",
                "초밥" to "초밥",
                "스시" to "초밥",
                "치킨" to "치킨",
                "피자" to "피자",
                "햄버거" to "햄버거",
                "버거" to "햄버거",
                "파스타" to "파스타",
                "스테이크" to "스테이크",
                "국밥" to "국밥",
                "갈비" to "갈비",
                "삼겹" to "삼겹살",
                "냉면" to "냉면",
                "카페" to "카페",
                "커피" to "카페",
                "디저트" to "디저트",
                "빵" to "디저트",
                "오마카세" to "오마카세",
                "먹방" to "먹방",
                "맛집" to "맛집",
                "족발" to "족발",
                "보쌈" to "보쌈",
                "찜닭" to "찜닭",
                "꼬치" to "꼬치",
                "야키토리" to "야키토리",
                "이자카야" to "이자카야",
                "맥주" to "맥주",
                "분식" to "분식",
                "떡볶이" to "분식",
                "쌀국수" to "쌀국수",
                "타코" to "타코",
            )
    }

    fun getPreference(userId: Long): UserPreferenceDto? = getCachedPreference(userId)?.toDto()

    private fun getCachedPreference(userId: Long): UserPreference? =
        userPreferenceCache.get(userId) { userPreferenceRepository.findByUserId(it) }

    private fun buildPrompt(inputs: List<VideoAnalysisInput>): String {
        val header =
            "다음은 사용자가 좋아요를 누른 YouTube 영상 ${inputs.size}개의 정보입니다.\n" +
                "이 영상들을 분석하여 사용자의 음식 취향을 추출해주세요.\n\n"

        val videos =
            inputs
                .mapIndexed { idx, v ->
                    buildString {
                        append("[${idx + 1}]\n")
                        append("제목: ${v.title}\n")
                        v.channelTitle?.let { append("채널: $it\n") }
                        v.description?.takeIf { it.isNotBlank() }?.let {
                            append("설명: ${it.take(300)}\n")
                        }
                        v.tags.takeIf { it.isNotEmpty() }?.let {
                            append("태그: ${it.joinToString(", ")}\n")
                        }
                        v.transcript?.takeIf { it.isNotBlank() }?.let {
                            append("자막: ${it.take(500)}\n")
                        } ?: append("자막: 없음\n")
                    }
                }.joinToString("\n")

        return header + videos
    }

    /**
     * Gemini가 빈 foodTags를 반환했을 때 영상 제목/태그에서 음식 키워드를 추출하여 폴백 태그 생성.
     */
    private fun extractFallbackFoodTags(videos: List<com.example.myfoodload.backend.model.entity.VideoMetadata>): List<FoodTagAnalysis> {
        val tagCounts = mutableMapOf<String, Int>()
        videos.forEach { video ->
            val text = "${video.title} ${video.description ?: ""} ${video.tags.joinToString(" ")}".lowercase()
            FALLBACK_TAG_MAP.forEach { (keyword, tag) ->
                if (text.contains(keyword)) {
                    tagCounts[tag] = (tagCounts[tag] ?: 0) + 1
                }
            }
        }
        if (tagCounts.isEmpty()) return emptyList()
        val maxCount = tagCounts.values.max().toDouble()
        return tagCounts
            .map { (tag, count) -> FoodTagAnalysis(tag, (count / maxCount).coerceIn(0.3, 1.0)) }
            .sortedByDescending { it.score }
            .take(10)
    }

    private fun emptyPreference(userId: Long) = UserPreferenceDto(userId = userId.toString())

    private fun UserPreference.toDto() =
        UserPreferenceDto(
            userId = userId.toString(),
            foodTags = foodTags.map { FoodTagDto(tag = it.tag, score = it.score) },
            ambianceTags = ambianceTags.toList(),
            priceRange =
                priceRange?.let {
                    runCatching {
                        com.example.myfoodload.shared.dto.PriceRange
                            .valueOf(it)
                    }.getOrNull()
                },
            confidence = confidence,
        )
}

private data class VideoAnalysisInput(
    val title: String,
    val channelTitle: String?,
    val description: String?,
    val tags: List<String>,
    val transcript: String?,
)
