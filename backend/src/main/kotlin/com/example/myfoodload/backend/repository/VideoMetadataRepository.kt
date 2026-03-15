package com.example.myfoodload.backend.repository

import com.example.myfoodload.backend.model.entity.VideoMetadata
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional

interface VideoMetadataRepository : JpaRepository<VideoMetadata, Long> {
    fun findByVideoId(videoId: String): VideoMetadata?

    fun findAllByVideoIdIn(videoIds: Collection<String>): List<VideoMetadata>

    fun existsByVideoId(videoId: String): Boolean

    /**
     * 공유 분석 캐시: 이미 DB에 존재하는 video_id 목록만 반환.
     * 없는 ID는 새로 YouTube API에서 수집해야 함.
     */
    @Query("SELECT vm.videoId FROM VideoMetadata vm WHERE vm.videoId IN :videoIds")
    fun findExistingVideoIds(videoIds: List<String>): List<String>

    /**
     * 맛집 추출 미완료 영상 조회 (공유 캐시: 한 번 추출되면 다른 사용자도 재사용).
     */
    @Query("SELECT vm FROM VideoMetadata vm WHERE vm.videoId IN :videoIds AND vm.restaurantExtracted = false")
    fun findUnextractedByVideoIds(videoIds: List<String>): List<VideoMetadata>

    /**
     * 지정 영상 목록의 추출 상태 초기화 (source_video_id 버그 수정 후 재추출 시 사용).
     */
    @Modifying
    @Transactional
    @Query("UPDATE VideoMetadata vm SET vm.restaurantExtracted = false WHERE vm.videoId IN :videoIds")
    fun resetExtractedStatusByVideoIds(videoIds: List<String>)

    /**
     * 사용자의 좋아요 영상 중 맛집이 연결된 영상 조회 (PERSONALIZED 추천 진단용).
     */
    @Query(
        "SELECT COUNT(vm) FROM VideoMetadata vm " +
            "WHERE vm.videoId IN :videoIds AND vm.restaurantExtracted = true",
    )
    fun countExtractedByVideoIds(videoIds: List<String>): Long
}
