package com.example.myfoodload.backend.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.OffsetDateTime

@Embeddable
data class UserVideoLikeId(
    @Column(name = "user_id")
    val userId: Long = 0,
    @Column(name = "video_id", length = 20)
    val videoId: String = "",
) : Serializable

@Entity
@Table(name = "user_video_likes")
class UserVideoLike(
    @EmbeddedId
    val id: UserVideoLikeId,
    @Column(name = "liked_at", nullable = false)
    val likedAt: OffsetDateTime = OffsetDateTime.now(),
)
