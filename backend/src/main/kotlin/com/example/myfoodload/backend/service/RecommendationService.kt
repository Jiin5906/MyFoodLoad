package com.example.myfoodload.backend.service

import com.example.myfoodload.backend.model.mapper.RestaurantMapper
import com.example.myfoodload.backend.repository.RestaurantRepository
import com.example.myfoodload.shared.dto.RecommendedRestaurantDto
import com.github.benmanes.caffeine.cache.Cache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 위치 기반 맛집 추천 서비스 (Phase 20).
 *
 * 추천 알고리즘:
 * PERSONALIZED 탭은 오직 사용자의 YouTube 좋아요 영상에서 직접 추출된 맛집만 반환.
 * 결과가 0개이면 앱의 YouTubeSyncBanner를 통해 YouTube 연동을 안내.
 * 거리 오름차순 정렬 (가까운 순).
 *
 * Type B (태그 기반 주변 맛집 폴백) 완전 제거 — Phase 20.
 */
@Service
@Transactional(readOnly = true)
class RecommendationService(
    private val restaurantRepository: RestaurantRepository,
    private val personalizedCache: Cache<String, List<RecommendedRestaurantDto>>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val FETCH_MULTIPLIER = 3
        private const val MAX_LIMIT = 50
        private const val EARTH_RADIUS_METERS = 6_371_000.0
    }

    /**
     * 내 취향 맛집 — YouTube 좋아요 영상에서 직접 추출된 맛집만 반환.
     *
     * 결과가 없으면 빈 리스트 반환 → 앱의 YouTubeSyncBanner가 YouTube 연동 안내.
     */
    suspend fun getRecommendations(
        userId: Long,
        latitude: Double,
        longitude: Double,
        limit: Int,
        excludeVisited: Boolean = false,
    ): List<RecommendedRestaurantDto> {
        // GPS 미세 떨림 무시: 소수점 4자리 ≈11m 정밀도
        val cacheKey = "${userId}_${"%.4f".format(latitude)}_${"%.4f".format(longitude)}_$excludeVisited"
        personalizedCache.getIfPresent(cacheKey)?.let { cached ->
            log.info("PERSONALIZED Caffeine HIT — key=$cacheKey")
            return cached
        }

        val result = withContext(Dispatchers.IO) {
            val validLimit = limit.coerceIn(1, MAX_LIMIT)

            val candidates =
                if (excludeVisited) {
                    restaurantRepository.findByUserLikedVideosExcludingVisited(
                        userId = userId,
                        latitude = latitude,
                        longitude = longitude,
                        limitCount = validLimit * FETCH_MULTIPLIER,
                    )
                } else {
                    restaurantRepository.findByUserLikedVideos(
                        userId = userId,
                        latitude = latitude,
                        longitude = longitude,
                        limitCount = validLimit * FETCH_MULTIPLIER,
                    )
                }

            val results =
                candidates
                    .map { restaurant ->
                        RecommendedRestaurantDto(
                            restaurant = RestaurantMapper.toDto(restaurant),
                            matchScore = 1.0,
                            distanceMeters =
                                haversineDistance(
                                    latitude,
                                    longitude,
                                    restaurant.latitude ?: latitude,
                                    restaurant.longitude ?: longitude,
                                ),
                            mode = "PERSONALIZED",
                        )
                    }.sortedBy { it.distanceMeters }
                    .take(validLimit)

            log.info("PERSONALIZED — {}개 반환, userId=$userId", results.size)
            results
        }

        personalizedCache.put(cacheKey, result)
        return result
    }

    /** Haversine 공식 — 위도/경도 기반 구면 거리 계산 (미터) */
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
