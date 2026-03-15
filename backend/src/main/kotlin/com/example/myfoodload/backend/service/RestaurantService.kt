package com.example.myfoodload.backend.service

import com.example.myfoodload.backend.model.mapper.RestaurantMapper
import com.example.myfoodload.backend.repository.RestaurantRepository
import com.example.myfoodload.shared.dto.CategoryType
import com.example.myfoodload.shared.dto.RestaurantDto
import com.example.myfoodload.shared.validation.ValidationRules
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 맛집 비즈니스 로직
 *
 * Phase 2: JPA CRUD (Spring MVC 블로킹 모델)
 * Phase 5+: 외부 API 호출(YouTube, LLM, Kakao Geocoding)은
 *           Dispatchers.IO + Kotlin Coroutines로 전환 (GlobalScope 금지)
 */
@Service
class RestaurantService(
    private val restaurantRepository: RestaurantRepository,
) {
    @Transactional(readOnly = true)
    fun findAll(): List<RestaurantDto> = restaurantRepository.findAll().map(RestaurantMapper::toDto)

    @Cacheable(value = ["restaurant"], key = "#id")
    @Transactional(readOnly = true)
    fun findById(id: Long): RestaurantDto? =
        restaurantRepository
            .findById(id)
            .map(RestaurantMapper::toDto)
            .orElse(null)

    @Transactional(readOnly = true)
    fun findByCategory(category: CategoryType): List<RestaurantDto> =
        restaurantRepository.findByCategory(category).map(RestaurantMapper::toDto)

    @Transactional(readOnly = true)
    fun search(keyword: String): List<RestaurantDto> =
        restaurantRepository.findByNameContainingIgnoreCase(keyword).map(RestaurantMapper::toDto)

    @Transactional
    fun create(dto: RestaurantDto): RestaurantDto {
        val entity = RestaurantMapper.toEntity(dto)
        return RestaurantMapper.toDto(restaurantRepository.save(entity))
    }

    @CacheEvict(value = ["restaurant"], key = "#id")
    @Transactional
    fun update(
        id: Long,
        dto: RestaurantDto,
    ): RestaurantDto? {
        val existing = restaurantRepository.findById(id).orElse(null) ?: return null
        RestaurantMapper.applyUpdate(existing, dto)
        return RestaurantMapper.toDto(restaurantRepository.save(existing))
    }

    @CacheEvict(value = ["restaurant"], key = "#id")
    @Transactional
    fun delete(id: Long): Boolean {
        if (!restaurantRepository.existsById(id)) return false
        restaurantRepository.deleteById(id)
        return true
    }

    /**
     * Phase 7에서 실제 구현 — 현재는 자리 예약
     * ST_DWithin(GEOGRAPHY, GiST 인덱스) 기반 반경 검색 + 선호도 스코어링
     */
    @Transactional(readOnly = true)
    fun findNearby(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double = ValidationRules.SEARCH_RADIUS_DEFAULT_METERS,
    ): List<RestaurantDto> {
        val safeRadius =
            radiusMeters.coerceIn(
                ValidationRules.SEARCH_RADIUS_MIN_METERS,
                ValidationRules.SEARCH_RADIUS_MAX_METERS,
            )
        return restaurantRepository
            .findWithinRadius(latitude, longitude, safeRadius, limitCount = 50)
            .map(RestaurantMapper::toDto)
    }
}
