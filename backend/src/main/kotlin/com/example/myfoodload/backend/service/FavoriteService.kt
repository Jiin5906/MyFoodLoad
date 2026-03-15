package com.example.myfoodload.backend.service

import com.example.myfoodload.backend.model.entity.Favorite
import com.example.myfoodload.backend.model.mapper.RestaurantMapper
import com.example.myfoodload.backend.repository.FavoriteRepository
import com.example.myfoodload.backend.repository.RestaurantRepository
import com.example.myfoodload.backend.repository.UserRepository
import com.example.myfoodload.shared.dto.FavoriteStatusDto
import com.example.myfoodload.shared.dto.RestaurantDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FavoriteService(
    private val favoriteRepository: FavoriteRepository,
    private val userRepository: UserRepository,
    private val restaurantRepository: RestaurantRepository,
) {
    @Transactional
    fun toggle(
        userId: Long,
        restaurantId: Long,
    ): FavoriteStatusDto =
        if (favoriteRepository.existsByUserIdAndRestaurantId(userId, restaurantId)) {
            favoriteRepository.deleteByUserIdAndRestaurantId(userId, restaurantId)
            FavoriteStatusDto(restaurantId = restaurantId, isFavorite = false)
        } else {
            val user =
                userRepository.findById(userId).orElseThrow {
                    IllegalArgumentException("사용자를 찾을 수 없습니다: $userId")
                }
            val restaurant =
                restaurantRepository.findById(restaurantId).orElseThrow {
                    IllegalArgumentException("맛집을 찾을 수 없습니다: $restaurantId")
                }
            favoriteRepository.save(Favorite(user = user, restaurant = restaurant))
            FavoriteStatusDto(restaurantId = restaurantId, isFavorite = true)
        }

    @Transactional(readOnly = true)
    fun getMyFavorites(userId: Long): List<RestaurantDto> =
        favoriteRepository
            .findAllByUserId(userId)
            .map { RestaurantMapper.toDto(it.restaurant) }

    @Transactional(readOnly = true)
    fun getMyFavoriteIds(userId: Long): Set<Long> = favoriteRepository.findRestaurantIdsByUserId(userId)
}
