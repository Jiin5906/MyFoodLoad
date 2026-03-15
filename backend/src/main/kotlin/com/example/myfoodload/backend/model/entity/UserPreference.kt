package com.example.myfoodload.backend.model.entity

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Embeddable
data class FoodTagEntry(
    @Column(name = "tag", length = 100)
    val tag: String = "",
    @Column(name = "score")
    val score: Double = 1.0,
)

@Entity
@Table(name = "user_preferences")
class UserPreference(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "user_id", unique = true, nullable = false)
    val userId: Long,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "user_food_tags",
        joinColumns = [JoinColumn(name = "user_preference_id")],
    )
    var foodTags: MutableList<FoodTagEntry> = mutableListOf(),
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "user_ambiance_tags",
        joinColumns = [JoinColumn(name = "user_preference_id")],
    )
    @Column(name = "tag", length = 100)
    var ambianceTags: MutableList<String> = mutableListOf(),
    @Column(name = "price_range", length = 20)
    var priceRange: String? = null,
    @Column(name = "confidence")
    var confidence: Double? = null,
    @Column(name = "analyzed_video_count", nullable = false)
    var analyzedVideoCount: Int = 0,
    @Column(name = "last_analyzed_at")
    var lastAnalyzedAt: OffsetDateTime? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
