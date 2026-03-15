package com.example.myfoodload.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Phase 10: 추천 결과 오프라인 캐시 Entity.
 *
 * 단일 행(id=1) 접근 패턴 — 항상 최신 추천 결과 1개만 저장.
 * restaurants는 List<RecommendedRestaurantDto>의 Gson JSON 직렬화 문자열.
 */
@Entity(tableName = "recommendation_cache")
data class CachedRecommendationEntity(
    @PrimaryKey val id: Int = 1,
    val restaurantsJson: String,
    val latitude: Double,
    val longitude: Double,
    val cachedAt: Long = System.currentTimeMillis(),
)
