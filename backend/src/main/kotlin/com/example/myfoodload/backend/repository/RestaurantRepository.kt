package com.example.myfoodload.backend.repository

import com.example.myfoodload.backend.model.entity.Restaurant
import com.example.myfoodload.shared.dto.CategoryType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface RestaurantRepository : JpaRepository<Restaurant, Long> {
    fun findByKakaoPlaceId(kakaoPlaceId: String): Restaurant?

    fun findBySourceVideoId(sourceVideoId: String): Restaurant?

    fun findAllBySourceVideoIdIn(sourceVideoIds: Collection<String>): List<Restaurant>

    fun findAllByKakaoPlaceIdIn(kakaoPlaceIds: Collection<String>): List<Restaurant>

    fun findByCategory(category: CategoryType): List<Restaurant>

    fun findByNameContainingIgnoreCase(keyword: String): List<Restaurant>

    /**
     * Phase 7 — 위치 기반 반경 검색
     *
     * GiST 인덱스 활용 (idx_restaurants_location).
     * GEOGRAPHY 타입으로 구면 거리 계산 → ST_Distance 금지, ST_DWithin 전용.
     * 결과는 거리 오름차순 정렬 (<-> 연산자 = KNN 인덱스 사용).
     */
    @Query(
        value = """
            SELECT r.* FROM restaurants r
            WHERE r.location IS NOT NULL
              AND ST_DWithin(
                  r.location,
                  ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                  :radiusMeters
              )
            ORDER BY r.location <-> ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
            LIMIT :limitCount
        """,
        nativeQuery = true,
    )
    fun findWithinRadius(
        @Param("latitude") latitude: Double,
        @Param("longitude") longitude: Double,
        @Param("radiusMeters") radiusMeters: Double,
        @Param("limitCount") limitCount: Int,
    ): List<Restaurant>

    /**
     * 위치 기반 반경 검색 + 구(區) 단위 필터링.
     *
     * address LIKE '%은평구%' 조건으로 같은 행정구역의 맛집만 반환.
     * 트렌딩 추천에서 인접 구 결과를 DB 레벨에서 제거.
     */
    @Query(
        value = """
            SELECT r.* FROM restaurants r
            WHERE r.location IS NOT NULL
              AND ST_DWithin(
                  r.location,
                  ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                  :radiusMeters
              )
              AND r.address LIKE '%' || :district || '%'
            ORDER BY r.location <-> ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
            LIMIT :limitCount
        """,
        nativeQuery = true,
    )
    fun findWithinRadiusAndDistrict(
        @Param("latitude") latitude: Double,
        @Param("longitude") longitude: Double,
        @Param("radiusMeters") radiusMeters: Double,
        @Param("district") district: String,
        @Param("limitCount") limitCount: Int,
    ): List<Restaurant>

    /**
     * PERSONALIZED 탭 — 사용자 좋아요 YouTube 영상 기반 전국 단위 맛집 조회.
     *
     * user_video_likes.video_id ↔ restaurants.source_video_id JOIN으로
     * 이 사용자가 좋아요 누른 영상에서 추출된 맛집만 반환.
     * 거리 제한 없음(전국) — 현재 위치로부터 거리 오름차순 정렬(표시용).
     */
    @Query(
        value = """
            SELECT r.* FROM restaurants r
            INNER JOIN user_video_likes uvl ON r.source_video_id = uvl.video_id
            WHERE uvl.user_id = :userId
              AND r.location IS NOT NULL
            ORDER BY r.location <-> ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
            LIMIT :limitCount
        """,
        nativeQuery = true,
    )
    fun findByUserLikedVideos(
        @Param("userId") userId: Long,
        @Param("latitude") latitude: Double,
        @Param("longitude") longitude: Double,
        @Param("limitCount") limitCount: Int,
    ): List<Restaurant>

    /**
     * PERSONALIZED 탭 — 좋아요 영상 기반 전국 단위 맛집 조회 (방문한 맛집 제외).
     */
    @Query(
        value = """
            SELECT r.* FROM restaurants r
            INNER JOIN user_video_likes uvl ON r.source_video_id = uvl.video_id
            WHERE uvl.user_id = :userId
              AND r.location IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1 FROM user_visits v
                  WHERE v.restaurant_id = r.id AND v.user_id = :userId
              )
            ORDER BY r.location <-> ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
            LIMIT :limitCount
        """,
        nativeQuery = true,
    )
    fun findByUserLikedVideosExcludingVisited(
        @Param("userId") userId: Long,
        @Param("latitude") latitude: Double,
        @Param("longitude") longitude: Double,
        @Param("limitCount") limitCount: Int,
    ): List<Restaurant>

    /**
     * C-2 — 방문한 맛집 제외 반경 검색 (Gemini 지적: DB 레벨 NOT EXISTS로 메모리 필터링 방지).
     *
     * idx_user_visits_user_restaurant 복합 인덱스 활용.
     */
    @Query(
        value = """
            SELECT r.* FROM restaurants r
            WHERE r.location IS NOT NULL
              AND ST_DWithin(
                  r.location,
                  ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                  :radiusMeters
              )
              AND NOT EXISTS (
                  SELECT 1 FROM user_visits v
                  WHERE v.restaurant_id = r.id AND v.user_id = :userId
              )
            ORDER BY r.location <-> ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
            LIMIT :limitCount
        """,
        nativeQuery = true,
    )
    fun findWithinRadiusExcludingVisited(
        @Param("latitude") latitude: Double,
        @Param("longitude") longitude: Double,
        @Param("radiusMeters") radiusMeters: Double,
        @Param("limitCount") limitCount: Int,
        @Param("userId") userId: Long,
    ): List<Restaurant>

    /**
     * INSERT ON CONFLICT DO NOTHING — kakao_place_id 중복 시 무시.
     * JPA saveAll의 트랜잭션 오염 문제를 원자적으로 해결.
     */
    @Modifying
    @Transactional
    @Query(
        value = """
            INSERT INTO restaurants (
                name, address, latitude, longitude, location, category, price_range,
                kakao_place_id, source_video_id, phone, kakao_place_url,
                recommendation_reason, created_at, updated_at
            ) VALUES (
                :name, :address, :latitude, :longitude,
                CASE WHEN :longitude IS NOT NULL AND :latitude IS NOT NULL
                     THEN ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
                     ELSE NULL END,
                :category, 'UNKNOWN',
                :kakaoPlaceId, :sourceVideoId, :phone, :kakaoPlaceUrl,
                :recommendationReason, NOW(), NOW()
            )
            ON CONFLICT (kakao_place_id) DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertIgnoreDuplicate(
        @Param("name") name: String,
        @Param("address") address: String,
        @Param("latitude") latitude: Double?,
        @Param("longitude") longitude: Double?,
        @Param("category") category: String,
        @Param("kakaoPlaceId") kakaoPlaceId: String?,
        @Param("sourceVideoId") sourceVideoId: String?,
        @Param("phone") phone: String?,
        @Param("kakaoPlaceUrl") kakaoPlaceUrl: String?,
        @Param("recommendationReason") recommendationReason: String?,
    )
}
