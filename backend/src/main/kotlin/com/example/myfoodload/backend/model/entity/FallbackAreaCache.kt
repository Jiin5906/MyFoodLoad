package com.example.myfoodload.backend.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.OffsetDateTime

/**
 * 폴백 추천 위치 격자 캐시 엔티티.
 *
 * 위도·경도를 소수점 2자리로 반올림한 격자(≈1.1km)별로
 * YouTube API 마지막 호출 시각을 기록한다.
 * 7일 이내 동일 격자 요청은 DB 맛집 조회만 수행 (API 호출 없음).
 */
@Entity
@Table(name = "fallback_area_cache")
class FallbackAreaCache(
    @EmbeddedId
    val id: FallbackAreaCacheId,
    @Column(name = "cached_at", nullable = false)
    var cachedAt: OffsetDateTime = OffsetDateTime.now(),
)

@Embeddable
data class FallbackAreaCacheId(
    @Column(name = "lat_grid", nullable = false)
    val latGrid: Double,
    @Column(name = "lon_grid", nullable = false)
    val lonGrid: Double,
) : Serializable
