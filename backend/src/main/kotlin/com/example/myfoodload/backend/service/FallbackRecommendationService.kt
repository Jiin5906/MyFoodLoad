package com.example.myfoodload.backend.service

import com.example.myfoodload.backend.model.entity.FallbackAreaCache
import com.example.myfoodload.backend.model.entity.FallbackAreaCacheId
import com.example.myfoodload.backend.model.entity.Restaurant
import com.example.myfoodload.backend.model.mapper.RestaurantMapper
import com.example.myfoodload.backend.repository.FallbackAreaCacheRepository
import com.example.myfoodload.backend.repository.RestaurantRepository
import com.example.myfoodload.backend.service.geocoding.KakaoGeocodingClient
import com.example.myfoodload.backend.service.geocoding.KakaoPlace
import com.example.myfoodload.backend.service.llm.GeminiClient
import com.example.myfoodload.backend.service.youtube.YouTubeApiClient
import com.example.myfoodload.shared.dto.CategoryType
import com.example.myfoodload.shared.dto.RecommendedRestaurantDto
import com.github.benmanes.caffeine.cache.Cache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * YouTube 쇼츠 기반 트렌딩 추천 서비스.
 *
 * ## 동작 흐름
 * 1. 사용자 위치를 소수점 1자리(≈11km 격자)로 반올림 → 격자 키 생성
 * 2. DB [fallback_area_cache]에서 캐시 유효 여부 확인 (유효기간 7일)
 * 3. **캐시 유효** → DB에서 카테고리별 viewCount 내림차순으로 총 15개 반환
 * 4. **캐시 없거나 만료** → 카테고리별(한/일/중/양/카페) YouTube API 검색
 *    → 기간 점진적 확장(1→3→6→12개월) → 카카오 매칭 → DB 저장 → 반환
 *
 * API 할당량 최적화: 목표치(카테고리당 3개) 달성 시 즉시 탐색 중단.
 */
@Service
class FallbackRecommendationService(
    private val youTubeApiClient: YouTubeApiClient,
    private val kakaoGeocodingClient: KakaoGeocodingClient,
    private val restaurantRepository: RestaurantRepository,
    private val fallbackAreaCacheRepository: FallbackAreaCacheRepository,
    private val geminiClient: GeminiClient,
    private val trendingCache: Cache<String, List<RecommendedRestaurantDto>>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 동일 격자 동시 요청 합치기 (cache stampede 방지) */
    private val inflightRequests = ConcurrentHashMap<String, CompletableDeferred<List<RecommendedRestaurantDto>>>()

    companion object {
        private const val FALLBACK_LIMIT = 15
        private const val FALLBACK_RADIUS_METERS = 5_000.0 // 반경 5km (구 단위 커버)
        private const val CACHE_EXPIRY_DAYS = 14L
        private const val GRID_SCALE = 10.0 // 소수점 1자리 ≈11km 격자 (변경 전: 100.0 ≈1.1km)
        private const val EARTH_RADIUS_METERS = 6_371_000.0
        private const val DB_FETCH_MULTIPLIER = 5
        private const val TARGET_PER_CATEGORY = 3

        /** 기간 점진적 확장 순서 (개월 단위) */
        private val PERIOD_MONTHS = listOf(1, 3, 6, 12)

        /** 카테고리별 YouTube 검색 키워드 (순서 유지 위해 LinkedHashMap) */
        private val CATEGORY_KEYWORDS =
            linkedMapOf(
                CategoryType.KOREAN to "한식",
                CategoryType.JAPANESE to "일식",
                CategoryType.CHINESE to "중식",
                CategoryType.WESTERN to "양식",
                CategoryType.CAFE to "카페",
            )

        private val GENERIC_FOOD_WORDS =
            setOf(
                "맛집",
                "먹방",
                "리뷰",
                "방문",
                "추천",
                "브이로그",
                "vlog",
                "광고",
                "협찬",
                "은평구맛집",
                "강남맛집",
                "홍대맛집",
                "신촌맛집",
                "서울맛집",
                "부산맛집",
                "냉동삼겹살",
                "삼겹살맛집",
                "한식맛집",
                "일식맛집",
                "카페맛집",
            )
        private val STOP_WORDS =
            setOf(
                "맛집",
                "먹방",
                "리뷰",
                "방문기",
                "방문",
                "추천",
                "top",
                "vlog",
                "브이로그",
                "인생",
            )
        private val CATEGORY_EXCLUDED = setOf("음식점", "음식", "식음료")

        private val HASHTAG_REGEX = Regex("#([가-힣a-zA-Z0-9]{2,})")
        private val SPECIAL_CHAR_REGEX = Regex("[|｜/…•·#]")
        private val WHITESPACE_REGEX = Regex("\\s+")
    }

    /**
     * 트렌딩(카테고리별 최신순 + 조회수 기반) 맛집 추천 반환.
     * 캐시가 유효하면 YouTube API 없이 DB에서 카테고리별 분배로 즉시 반환.
     * 사용자 위치의 구(區) 단위로 필터링하여 같은 구의 맛집만 반환.
     */
    suspend fun getFallbackRecommendations(
        latitude: Double,
        longitude: Double,
        limit: Int = FALLBACK_LIMIT,
    ): List<RecommendedRestaurantDto> {
        val latGrid = roundToGrid(latitude)
        val lonGrid = roundToGrid(longitude)
        val gridKey = "${latGrid}_$lonGrid"

        // 1. Caffeine 인메모리 캐시 HIT → 즉시 반환 (DB 조회도 스킵)
        trendingCache.getIfPresent(gridKey)?.let { cached ->
            log.info("트렌딩 Caffeine HIT — grid=({}, {})", latGrid, lonGrid)
            return cached
        }

        // 2. 동일 격자 동시 요청 합치기 (cache stampede 방지)
        val existing = inflightRequests[gridKey]
        if (existing != null) {
            log.info("트렌딩 inflight JOIN — grid=({}, {})", latGrid, lonGrid)
            return existing.await()
        }

        val deferred = CompletableDeferred<List<RecommendedRestaurantDto>>()
        val prev = inflightRequests.putIfAbsent(gridKey, deferred)
        if (prev != null) {
            return prev.await()
        }

        return try {
            val result = doGetFallbackRecommendations(latitude, longitude, latGrid, lonGrid, gridKey)
            trendingCache.put(gridKey, result)
            deferred.complete(result)
            result
        } catch (e: Exception) {
            deferred.completeExceptionally(e)
            throw e
        } finally {
            inflightRequests.remove(gridKey)
        }
    }

    private suspend fun doGetFallbackRecommendations(
        latitude: Double,
        longitude: Double,
        latGrid: Double,
        lonGrid: Double,
        gridKey: String,
    ): List<RecommendedRestaurantDto> {
        val cacheId = FallbackAreaCacheId(latGrid, lonGrid)
        val areaName = kakaoGeocodingClient.getAreaName(latitude, longitude)

        val cache =
            withContext(Dispatchers.IO) {
                fallbackAreaCacheRepository.findById(cacheId).orElse(null)
            }
        val isCacheValid =
            cache != null &&
                cache.cachedAt.isAfter(OffsetDateTime.now().minusDays(CACHE_EXPIRY_DAYS))

        if (isCacheValid) {
            log.info("트렌딩 DB캐시 HIT — grid=({}, {}), 구={}", latGrid, lonGrid, areaName)
            return loadFromDbByCategorical(latitude, longitude, areaName)
        }

        log.info("트렌딩 캐시 MISS — grid=({}, {}), 구={}, 카테고리별 YouTube 탐색 시작", latGrid, lonGrid, areaName)
        val freshResults = fetchCategorically(latitude, longitude)

        if (freshResults.isNotEmpty()) {
            val newCache =
                cache?.apply { cachedAt = OffsetDateTime.now() }
                    ?: FallbackAreaCache(id = cacheId)
            withContext(Dispatchers.IO) {
                fallbackAreaCacheRepository.save(newCache)
            }
        }

        return loadFromDbByCategorical(latitude, longitude, areaName)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 캐시 HIT: DB에서 카테고리별 분배
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * DB에서 반경 내 맛집을 카테고리별로 viewCount 내림차순 [TARGET_PER_CATEGORY]개씩 선택.
     * 부족한 경우 나머지 카테고리로 보충하여 총 최대 [FALLBACK_LIMIT]개 반환.
     * [districtName]이 지정되면 해당 구(區)에 속한 맛집만 필터링.
     */
    private suspend fun loadFromDbByCategorical(
        latitude: Double,
        longitude: Double,
        districtName: String? = null,
    ): List<RecommendedRestaurantDto> {
        val allRestaurants =
            withContext(Dispatchers.IO) {
                if (districtName.isNullOrBlank()) {
                    restaurantRepository.findWithinRadius(
                        latitude = latitude,
                        longitude = longitude,
                        radiusMeters = FALLBACK_RADIUS_METERS,
                        limitCount = FALLBACK_LIMIT * DB_FETCH_MULTIPLIER,
                    )
                } else {
                    restaurantRepository.findWithinRadiusAndDistrict(
                        latitude = latitude,
                        longitude = longitude,
                        radiusMeters = FALLBACK_RADIUS_METERS,
                        district = districtName,
                        limitCount = FALLBACK_LIMIT * DB_FETCH_MULTIPLIER,
                    ).also {
                        log.info("구 필터링(DB): {} → {}개 조회", districtName, it.size)
                    }
                }
            }

        val grouped = allRestaurants.groupBy { it.category }
        val result = mutableListOf<Restaurant>()

        // 목표 카테고리(한/일/중/양/카페)별로 3개씩 우선 채우기
        for (cat in CATEGORY_KEYWORDS.keys) {
            val catList =
                (grouped[cat] ?: emptyList())
                    .sortedByDescending { it.viewCount ?: 0L }
                    .take(TARGET_PER_CATEGORY)
            result.addAll(catList)
        }

        // 아직 FALLBACK_LIMIT 미만이면 나머지 카테고리(UNKNOWN 등)로 보충
        if (result.size < FALLBACK_LIMIT) {
            val usedIds = result.map { it.id }.toSet()
            val extras =
                allRestaurants
                    .filter { it.id !in usedIds }
                    .sortedByDescending { it.viewCount ?: 0L }
                    .take(FALLBACK_LIMIT - result.size)
            result.addAll(extras)
        }

        return result
            .take(FALLBACK_LIMIT)
            .map { r ->
                RecommendedRestaurantDto(
                    restaurant = RestaurantMapper.toDto(r),
                    matchScore = 0.0,
                    distanceMeters =
                        haversineDistance(
                            lat1 = latitude,
                            lon1 = longitude,
                            lat2 = r.latitude ?: latitude,
                            lon2 = r.longitude ?: longitude,
                        ),
                    mode = "TRENDING",
                    sourceVideoTitle = r.sourceVideoTitle,
                )
            }.also { log.info("DB 카테고리별 트렌딩 반환 — {}개", it.size) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 캐시 MISS: 기간 점진적 확장 + 카테고리별 병렬 YouTube 탐색
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 5개 카테고리 × 기간 점진적 확장(1→3→6→12개월)으로 YouTube 검색 후 Kakao 매칭 → DB 저장.
     *
     * API 할당량 최적화:
     * - 목표치(카테고리당 3개) 달성한 카테고리는 다음 기간 탐색 생략.
     * - 모든 카테고리 목표 달성 시 즉시 반복 중단.
     * - 각 기간 내에서 미충족 카테고리만 병렬 검색.
     */
    private suspend fun fetchCategorically(
        latitude: Double,
        longitude: Double,
    ): List<RecommendedRestaurantDto> {
        val areaName = kakaoGeocodingClient.getAreaName(latitude, longitude) ?: "서울"

        // 카테고리별 결과 누적 맵
        val categoryResults: MutableMap<CategoryType, MutableList<RecommendedRestaurantDto>> =
            CATEGORY_KEYWORDS.keys
                .associateWith { mutableListOf<RecommendedRestaurantDto>() }
                .toMutableMap()

        for (months in PERIOD_MONTHS) {
            // 아직 목표치 미달인 카테고리만 탐색
            val pendingCategories =
                CATEGORY_KEYWORDS.entries
                    .filter { (cat, _) -> (categoryResults[cat]?.size ?: 0) < TARGET_PER_CATEGORY }

            if (pendingCategories.isEmpty()) {
                log.info("모든 카테고리 목표 달성 — {}개월 이내에서 탐색 완료", months)
                break
            }

            // YouTube API는 RFC 3339 형식 요구 ('Z' 종료 또는 오프셋). 나노초 제거 후 UTC 변환.
            val publishedAfter =
                OffsetDateTime
                    .now()
                    .minusMonths(months.toLong())
                    .toInstant()
                    .toString() // → "2025-01-28T10:48:31Z" (UTC, 나노초 없음)

            log.info(
                "기간 탐색: {}개월 이내 (publishedAfter={}), 미충족 카테고리 {}개",
                months,
                publishedAfter,
                pendingCategories.size,
            )

            // 미충족 카테고리 병렬 검색
            val batchResults =
                coroutineScope {
                    pendingCategories
                        .map { (categoryType, keyword) ->
                            async(Dispatchers.IO) {
                                try {
                                    val items =
                                        fetchCategoryItems(
                                            areaName = areaName,
                                            categoryKeyword = keyword,
                                            publishedAfter = publishedAfter,
                                            targetCount = TARGET_PER_CATEGORY,
                                            latitude = latitude,
                                            longitude = longitude,
                                        )
                                    categoryType to items
                                } catch (e: Exception) {
                                    log.warn("카테고리 '{}' 탐색 실패 — {}", keyword, e.message)
                                    categoryType to emptyList()
                                }
                            }
                        }.awaitAll()
                }

            // 중복 제거 후 누적
            batchResults.forEach { (cat, items) ->
                val existing = categoryResults[cat] ?: mutableListOf()
                val existingIds = existing.map { it.restaurant.id }.toSet()
                existing.addAll(items.filter { it.restaurant.id !in existingIds })
            }
        }

        // 카테고리별 viewCount 내림차순 TARGET_PER_CATEGORY개 선택 후 병합
        return categoryResults.values
            .flatMap { list ->
                list
                    .sortedByDescending { it.restaurant.viewCount ?: 0L }
                    .take(TARGET_PER_CATEGORY)
            }.also { log.info("카테고리별 트렌딩 YouTube 결과: {}개", it.size) }
    }

    /**
     * 단일 카테고리에 대한 YouTube 검색 → Kakao 매칭 → DB 저장 → DTO 반환.
     *
     * @param areaName        역지오코딩 지역명 (예: "은평구")
     * @param categoryKeyword 카테고리 한국어 키워드 (예: "한식", "카페")
     * @param publishedAfter  ISO 8601 기간 시작점
     * @param targetCount     목표 결과 수
     */
    private suspend fun fetchCategoryItems(
        areaName: String,
        categoryKeyword: String,
        publishedAfter: String,
        targetCount: Int,
        latitude: Double,
        longitude: Double,
    ): List<RecommendedRestaurantDto> {
        val areaKeyword = "$areaName $categoryKeyword 맛집"
        // 카카오 API는 카테고리 코드를 1개만 허용: 카페/디저트=CE7, 나머지 음식점=FD6
        val categoryGroupCode = if (categoryKeyword == "카페") "CE7" else "FD6"

        val searchItems =
            youTubeApiClient.searchFoodShortsNearby(
                areaKeyword = areaKeyword,
                maxResults = targetCount * 3,
                publishedAfter = publishedAfter,
            )
        if (searchItems.isEmpty()) return emptyList()

        val videoIds = searchItems.mapNotNull { it.id?.videoId }
        val viewCountMap = youTubeApiClient.getVideoStatistics(videoIds)

        // Gemini 일괄 검색어 추출: 제목+설명 → "지역명 상호명" 형태 정제 쿼리
        val titleDescPairs =
            searchItems.mapNotNull { item ->
                val title = item.snippet?.title?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                title to item.snippet?.description
            }
        val geminiQueries: List<String?> =
            try {
                geminiClient.extractSearchQueries(titleDescPairs)
            } catch (e: Exception) {
                log.warn("Gemini 검색어 추출 실패 — 폴백 regex 사용: {}", e.message)
                emptyList()
            }

        val rawItems =
            searchItems.mapIndexedNotNull { idx, item ->
                val title = item.snippet?.title?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
                val videoId = item.id?.videoId
                // 썸네일 우선순위: YouTube snippet high → medium → 정적 URL 생성
                val thumbnailUrl =
                    item.snippet
                        ?.thumbnails
                        ?.high
                        ?.url
                        ?: item.snippet
                            ?.thumbnails
                            ?.medium
                            ?.url
                        ?: videoId?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
                MatchedItem(
                    place = null,
                    videoId = videoId,
                    title = title,
                    description = item.snippet?.description,
                    viewCount = videoId?.let { viewCountMap[it] },
                    thumbnailUrl = thumbnailUrl,
                    searchQuery = geminiQueries.getOrNull(idx),
                )
            }

        val matched = matchKakaoPlaces(rawItems, latitude, longitude, categoryGroupCode, areaName)
        val matchedCount = matched.count { it != null }
        log.info("카카오 매칭 결과: $categoryKeyword — ${rawItems.size}개 중 ${matchedCount}개 매칭")
        return syncAndMap(matched, latitude, longitude, targetCount)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 공통 내부 구현 (기존 유지)
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun matchKakaoPlaces(
        items: List<MatchedItem?>,
        latitude: Double,
        longitude: Double,
        categoryGroupCode: String = "FD6",
        areaName: String? = null,
    ): List<MatchedItem?> =
        coroutineScope {
            items
                .map { item ->
                    async(Dispatchers.IO) {
                        if (item == null) return@async null
                        try {
                            // Gemini 정제 쿼리 우선 → 없으면 regex 추출 폴백
                            val candidates =
                                buildList {
                                    item.searchQuery?.takeIf { it.isNotBlank() }?.let { add(it) }
                                    addAll(extractSearchCandidates(item.title))
                                    // 지역명 + 제목 앞 단어 조합 (regex 폴백 보강)
                                    if (areaName != null && item.searchQuery == null) {
                                        val titleWords = item.title
                                            .replace(Regex("[^가-힣a-zA-Z0-9\\s]"), " ")
                                            .split(Regex("\\s+"))
                                            .filter { it.length >= 2 && it.lowercase() !in STOP_WORDS && it !in GENERIC_FOOD_WORDS }
                                        for (word in titleWords.take(3)) {
                                            add("$areaName $word")
                                        }
                                    }
                                }.distinct()
                            for (candidate in candidates) {
                                val results =
                                    kakaoGeocodingClient
                                        .searchKeywordNearby(
                                            query = candidate,
                                            latitude = latitude,
                                            longitude = longitude,
                                            categoryGroupCode = categoryGroupCode,
                                        )
                                if (results.isEmpty()) continue
                                // 같은 구(區)에 속한 장소만 매칭 허용 — 모든 결과를 순회
                                val found =
                                    if (areaName != null) {
                                        results.firstOrNull { place ->
                                            val addr = place.roadAddressName.ifBlank { place.addressName }
                                            addr.contains(areaName)
                                        } ?: run {
                                            log.debug("구 불일치 전체 제외: candidate='{}', 기대={}", candidate, areaName)
                                            continue
                                        }
                                    } else {
                                        results.first()
                                    }
                                return@async item.copy(place = found)
                            }
                            null
                        } catch (e: Exception) {
                            log.warn("Kakao 검색 실패 (제목: '{}') — {}", item.title, e.message)
                            null
                        }
                    }
                }.awaitAll()
        }

    private suspend fun syncAndMap(
        matched: List<MatchedItem?>,
        latitude: Double,
        longitude: Double,
        limit: Int,
    ): List<RecommendedRestaurantDto> {
        val deduplicated =
            matched
                .filterNotNull()
                .filter { it.place != null }
                .distinctBy { it.place!!.id.ifBlank { "${it.place!!.placeName}_${it.place!!.addressName}" } }
                .take(limit)

        val placeIds = deduplicated.mapNotNull { it.place!!.id.takeIf { it.isNotBlank() } }.toSet()
        val existingMap =
            if (placeIds.isEmpty()) {
                emptyMap()
            } else {
                withContext(Dispatchers.IO) {
                    restaurantRepository.findAllByKakaoPlaceIdIn(placeIds).associateBy { it.kakaoPlaceId }
                }
            }

        val toSave = mutableListOf<Restaurant>()
        for (item in deduplicated) {
            val place = item.place!!
            val existing = existingMap[place.id.takeIf { it.isNotBlank() }]
            if (existing != null) {
                if (item.viewCount != null && (existing.viewCount == null || item.viewCount > existing.viewCount!!)) {
                    existing.viewCount = item.viewCount
                    existing.updatedAt = OffsetDateTime.now()
                    toSave.add(existing)
                }
            } else {
                toSave.add(
                    Restaurant(
                        name = place.placeName,
                        address = place.roadAddressName.ifBlank { place.addressName },
                        latitude = place.y.toDoubleOrNull(),
                        longitude = place.x.toDoubleOrNull(),
                        category = mapCategory(place),
                        tags = extractTagsFromCategory(place.categoryName).toMutableList(),
                        kakaoPlaceId = place.id.ifBlank { null },
                        sourceVideoId = item.videoId,
                        sourceVideoTitle = item.title,
                        viewCount = item.viewCount,
                        thumbnailUrl = item.thumbnailUrl,
                        phone = place.phone.ifBlank { null },
                        kakaoPlaceUrl = place.placeUrl.ifBlank { null },
                        updatedAt = OffsetDateTime.now(),
                    ),
                )
            }
        }

        val savedEntities =
            withContext(Dispatchers.IO) {
                try {
                    restaurantRepository.saveAll(toSave)
                } catch (e: Exception) {
                    log.warn("saveAll 실패, 개별 저장으로 폴백: ${e.message}")
                    toSave.mapNotNull { entity ->
                        try {
                            restaurantRepository.save(entity)
                        } catch (inner: Exception) {
                            log.debug("개별 저장 스킵: ${entity.name} — ${inner.message}")
                            null
                        }
                    }
                }
            }
        val savedMap = savedEntities.associateBy { it.name to it.address }

        return deduplicated.mapNotNull { item ->
            val place = item.place!!
            val placeId = place.id.takeIf { it.isNotBlank() }
            val restaurant =
                existingMap[placeId]
                    ?: savedMap[place.placeName to place.roadAddressName.ifBlank { place.addressName }]
                    ?: return@mapNotNull null
            RecommendedRestaurantDto(
                restaurant = RestaurantMapper.toDto(restaurant),
                matchScore = 0.0,
                distanceMeters =
                    haversineDistance(
                        lat1 = latitude,
                        lon1 = longitude,
                        lat2 = restaurant.latitude ?: latitude,
                        lon2 = restaurant.longitude ?: longitude,
                    ),
                mode = "TRENDING",
                sourceVideoTitle = item.title,
            )
        }
    }

    private fun extractSearchCandidates(title: String): List<String> {
        val candidates = mutableListOf<String>()
        val cleanTitle = title.replace(SPECIAL_CHAR_REGEX, " ").replace(Regex("[🔥🍣✨🎉💯😍🤤❤️👍🏻💕🥰😋]+"), " ").trim()
        if (cleanTitle.length <= 90) candidates.add(cleanTitle)

        HASHTAG_REGEX.findAll(title).forEach { match ->
            val tag = match.groupValues[1]
            if (tag !in GENERIC_FOOD_WORDS) candidates.add(tag)
        }

        val words =
            title
                .replace(SPECIAL_CHAR_REGEX, " ")
                .split(WHITESPACE_REGEX)
                .filter { it.isNotBlank() && it.length >= 2 }
        val meaningfulWords = mutableListOf<String>()
        for (word in words) {
            if (word.lowercase() in STOP_WORDS) break
            meaningfulWords.add(word)
            if (meaningfulWords.size >= 4) break
        }
        if (meaningfulWords.size >= 2) {
            candidates.add(meaningfulWords.take(2).joinToString(" "))
            if (meaningfulWords.size >= 3) candidates.add(meaningfulWords.take(3).joinToString(" "))
        } else if (meaningfulWords.size == 1 && meaningfulWords[0].length >= 3) {
            candidates.add(meaningfulWords[0])
        }

        return candidates.distinct().filter { it.isNotBlank() && it.length in 2..90 }
    }

    private fun extractTagsFromCategory(categoryName: String): List<String> =
        categoryName
            .split(">")
            .map { it.trim() }
            .filter { it.isNotBlank() && it !in CATEGORY_EXCLUDED }
            .take(3)

    private fun roundToGrid(value: Double): Double = (value * GRID_SCALE).roundToInt() / GRID_SCALE

    private fun mapCategory(place: KakaoPlace): CategoryType {
        if (place.categoryGroupCode == "CE7") return CategoryType.CAFE
        val name = place.categoryName.lowercase()
        return when {
            name.contains("한식") || name.contains("한정식") || name.contains("백반") || name.contains("국밥") -> CategoryType.KOREAN
            name.contains("일식") || name.contains("일본") || name.contains("스시") || name.contains("라멘") || name.contains("오마카세") || name.contains("이자카야") || name.contains("돈카츠") || name.contains("우동") || name.contains("소바") -> CategoryType.JAPANESE
            name.contains("중식") || name.contains("중국") || name.contains("중화") -> CategoryType.CHINESE
            name.contains("양식") || name.contains("이탈리아") || name.contains("피자") || name.contains("파스타") || name.contains("프랑스") || name.contains("스테이크") || name.contains("브런치") || name.contains("버거") || name.contains("햄버거") -> CategoryType.WESTERN
            name.contains("카페") || name.contains("디저트") || name.contains("베이커리") || name.contains("빵") || name.contains("제과") -> CategoryType.CAFE
            else -> CategoryType.UNKNOWN
        }
    }

    private fun haversineDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lon2 - lon1)
        val a = sin(dPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dLambda / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }
}

private data class MatchedItem(
    val place: KakaoPlace?,
    val videoId: String?,
    val title: String,
    val description: String?,
    val viewCount: Long?,
    val thumbnailUrl: String?,
    val searchQuery: String? = null,
)
