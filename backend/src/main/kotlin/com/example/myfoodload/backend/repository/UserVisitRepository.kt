package com.example.myfoodload.backend.repository

import com.example.myfoodload.backend.model.entity.UserVisit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserVisitRepository : JpaRepository<UserVisit, Long> {
    fun existsByUserIdAndRestaurantId(
        userId: Long,
        restaurantId: Long,
    ): Boolean

    fun deleteByUserIdAndRestaurantId(
        userId: Long,
        restaurantId: Long,
    )

    @Query("SELECT v.restaurant.id FROM UserVisit v WHERE v.user.id = :userId")
    fun findRestaurantIdsByUserId(
        @Param("userId") userId: Long,
    ): Set<Long>
}
