package com.example.myfoodload.backend.model.entity

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "video_metadata")
class VideoMetadata(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "video_id", unique = true, nullable = false, length = 20)
    val videoId: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    var title: String,
    @Column(columnDefinition = "TEXT")
    var description: String? = null,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "video_tags",
        joinColumns = [JoinColumn(name = "video_metadata_id")],
    )
    @Column(name = "tag", length = 100)
    var tags: MutableList<String> = mutableListOf(),
    @Column(name = "channel_id", nullable = false, length = 30)
    val channelId: String,
    @Column(name = "channel_title", length = 255)
    var channelTitle: String? = null,
    @Column(name = "category_id", length = 10)
    var categoryId: String? = null,
    @Column(name = "published_at")
    var publishedAt: OffsetDateTime? = null,
    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    var thumbnailUrl: String? = null,
    /** YouTube timedtext API로 수집한 자막 (없으면 null). 공유 캐시: 최초 수집 후 재사용. */
    @Column(name = "transcript", columnDefinition = "TEXT")
    var transcript: String? = null,
    @Column(name = "is_analyzed", nullable = false)
    var isAnalyzed: Boolean = false,
    /** 이 영상에서 맛집 정보 추출(LLM + 지오코딩)이 완료됐는지 여부. */
    @Column(name = "restaurant_extracted", nullable = false)
    var restaurantExtracted: Boolean = false,
    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
