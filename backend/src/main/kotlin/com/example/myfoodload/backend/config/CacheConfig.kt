package com.example.myfoodload.backend.config

import com.example.myfoodload.backend.model.entity.UserPreference
import com.example.myfoodload.shared.dto.RecommendedRestaurantDto
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
@EnableCaching
class CacheConfig {
    /**
     * Spring @Cacheable 용 CacheManager (RestaurantService).
     * TTL 1h, maxSize 500.
     */
    @Bean
    fun cacheManager(): CacheManager =
        CaffeineCacheManager("restaurant").apply {
            setCaffeine(
                Caffeine
                    .newBuilder()
                    .expireAfterWrite(1, TimeUnit.HOURS)
                    .maximumSize(500),
            )
        }

    /** LlmAnalysisService — UserPreference 수동 캐시 (TTL 2h, max 200) */
    @Bean
    fun userPreferenceCache(): Cache<Long, UserPreference> =
        Caffeine
            .newBuilder()
            .expireAfterWrite(2, TimeUnit.HOURS)
            .maximumSize(200)
            .build()

    /** FallbackRecommendationService — 트렌딩 인메모리 캐시 (TTL 10min, max 50) */
    @Bean
    fun trendingCache(): Cache<String, List<RecommendedRestaurantDto>> =
        Caffeine
            .newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(50)
            .build()

    /** RecommendationService — PERSONALIZED 인메모리 캐시 (TTL 5min, max 100) */
    @Bean
    fun personalizedCache(): Cache<String, List<RecommendedRestaurantDto>> =
        Caffeine
            .newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(100)
            .build()

    /** VideoIngestionService — 24h 동기화 쿨다운 (max 1000) */
    @Bean
    fun syncCooldownCache(): Cache<Long, Boolean> =
        Caffeine
            .newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .maximumSize(1000)
            .build()
}
