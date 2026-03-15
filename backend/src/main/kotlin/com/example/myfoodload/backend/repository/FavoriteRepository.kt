package com.example.myfoodload.backend.repository

import com.example.myfoodload.backend.model.entity.Favorite
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FavoriteRepository : JpaRepository<Favorite, Long> {
    fun existsByUserIdAndRestaurantId(
        userId: Long,
        restaurantId: Long,
    ): Boolean

    fun findAllByUserId(userId: Long): List<Favorite>

    fun deleteByUserIdAndRestaurantId(
        userId: Long,
        restaurantId: Long,
    )

    @Query("SELECT f.restaurant.id FROM Favorite f WHERE f.user.id = :userId")
    fun findRestaurantIdsByUserId(
        @Param("userId") userId: Long,
    ): Set<Long>
}
