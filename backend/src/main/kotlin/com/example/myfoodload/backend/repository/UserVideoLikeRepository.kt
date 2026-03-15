package com.example.myfoodload.backend.repository

import com.example.myfoodload.backend.model.entity.UserVideoLike
import com.example.myfoodload.backend.model.entity.UserVideoLikeId
import org.springframework.data.jpa.repository.JpaRepository

interface UserVideoLikeRepository : JpaRepository<UserVideoLike, UserVideoLikeId> {
    fun existsByIdUserIdAndIdVideoId(
        userId: Long,
        videoId: String,
    ): Boolean

    fun countByIdUserId(userId: Long): Long

    fun findByIdUserId(userId: Long): List<UserVideoLike>
}
