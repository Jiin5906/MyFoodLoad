package com.example.myfoodload.backend.model.entity

import com.example.myfoodload.shared.dto.CategoryType
import com.example.myfoodload.shared.dto.PriceRange
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import java.time.OffsetDateTime

/**
 * 맛집 JPA 엔티티
 *
 * - location GEOGRAPHY(POINT,4326) 컬럼은 Flyway 트리거가
 *   latitude/longitude 변경 시 자동 갱신 → JPA에서 직접 매핑 불필요
 * - Phase 7에서 ST_DWithin Native Query를 RestaurantRepository에 추가
 */
@Entity
@Table(name = "restaurants")
class Restaurant(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    var name: String = "",
    @Column(nullable = false)
    var address: String = "",
    // Phase 6.5: Kakao Geocoding API 결과 저장 → 트리거가 location 컬럼 자동 갱신
    @Column(name = "latitude")
    var latitude: Double? = null,
    @Column(name = "longitude")
    var longitude: Double? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    var category: CategoryType = CategoryType.UNKNOWN,
    @Enumerated(EnumType.STRING)
    @Column(name = "price_range", nullable = false)
    var priceRange: PriceRange = PriceRange.UNKNOWN,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "restaurant_tags",
        joinColumns = [JoinColumn(name = "restaurant_id")],
    )
    @Column(name = "tag", nullable = false)
    var tags: MutableList<String> = mutableListOf(),
    @Column(name = "rating")
    var rating: Double? = null,
    @Column(name = "thumbnail_url")
    var thumbnailUrl: String? = null,
    /** 카카오 장소 고유 ID — 중복 저장 방지 (nullable: 수동 등록 맛집은 없을 수 있음) */
    @Column(name = "kakao_place_id", unique = true, length = 50)
    var kakaoPlaceId: String? = null,
    /** Phase 9: 이 맛집이 추출된 원본 YouTube 영상 ID (Shorts 재생용) */
    @Column(name = "source_video_id", length = 20)
    var sourceVideoId: String? = null,
    /** Phase 9(V9): YouTube 영상 조회수 — 핫한 맛집 정렬 기준 */
    @Column(name = "view_count")
    var viewCount: Long? = null,
    /** Phase 9(V9): 맛집이 소개된 YouTube 쇼츠 제목 */
    @Column(name = "source_video_title", length = 500)
    var sourceVideoTitle: String? = null,
    /** 카카오 로컬 API 전화번호 (예: "02-1234-5678") */
    @Column(name = "phone", length = 30)
    var phone: String? = null,
    /** 카카오 장소 상세 URL */
    @Column(name = "kakao_place_url", length = 300)
    var kakaoPlaceUrl: String? = null,
    /** B-4: Gemini 추천 이유 (사용자의 유튜브 좋아요 기록 기반 스토리텔링) */
    @Column(name = "recommendation_reason", length = 500)
    var recommendationReason: String? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
