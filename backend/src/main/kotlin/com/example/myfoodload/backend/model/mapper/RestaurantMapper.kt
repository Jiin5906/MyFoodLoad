package com.example.myfoodload.backend.model.mapper

import com.example.myfoodload.backend.model.entity.Restaurant
import com.example.myfoodload.shared.dto.RestaurantDto
import java.time.OffsetDateTime

object RestaurantMapper {
    fun toDto(entity: Restaurant): RestaurantDto =
        RestaurantDto(
            id = entity.id,
            name = entity.name,
            address = entity.address,
            latitude = entity.latitude,
            longitude = entity.longitude,
            category = entity.category,
            priceRange = entity.priceRange,
            tags = entity.tags.toList(),
            rating = entity.rating,
            thumbnailUrl = entity.thumbnailUrl,
            sourceVideoId = entity.sourceVideoId,
            viewCount = entity.viewCount,
            phone = entity.phone,
            kakaoPlaceUrl = entity.kakaoPlaceUrl,
            recommendationReason = entity.recommendationReason,
        )

    fun toEntity(dto: RestaurantDto): Restaurant =
        Restaurant(
            name = dto.name,
            address = dto.address,
            latitude = dto.latitude,
            longitude = dto.longitude,
            category = dto.category,
            priceRange = dto.priceRange,
            tags = dto.tags.toMutableList(),
            rating = dto.rating,
            thumbnailUrl = dto.thumbnailUrl,
        )

    /** 기존 엔티티에 DTO 값을 덮어쓴다 (PUT 업데이트용). */
    fun applyUpdate(
        entity: Restaurant,
        dto: RestaurantDto,
    ): Restaurant {
        entity.name = dto.name
        entity.address = dto.address
        entity.latitude = dto.latitude
        entity.longitude = dto.longitude
        entity.category = dto.category
        entity.priceRange = dto.priceRange
        entity.tags.clear()
        entity.tags.addAll(dto.tags)
        entity.rating = dto.rating
        entity.thumbnailUrl = dto.thumbnailUrl
        entity.updatedAt = OffsetDateTime.now()
        return entity
    }
}
