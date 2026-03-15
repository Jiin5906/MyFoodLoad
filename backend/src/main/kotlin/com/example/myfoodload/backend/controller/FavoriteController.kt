package com.example.myfoodload.backend.controller

import com.example.myfoodload.backend.service.FavoriteService
import com.example.myfoodload.shared.dto.FavoriteStatusDto
import com.example.myfoodload.shared.dto.RestaurantDto
import com.example.myfoodload.shared.response.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 즐겨찾기 API
 *
 * POST   /api/favorites/{restaurantId}  — 즐겨찾기 추가
 * DELETE /api/favorites/{restaurantId}  — 즐겨찾기 제거
 * GET    /api/favorites                 — 내 즐겨찾기 목록
 * GET    /api/favorites/ids             — 즐겨찾기 ID 집합 (DetailScreen 초기화용)
 */
@RestController
@RequestMapping("/api/favorites")
class FavoriteController(
    private val favoriteService: FavoriteService,
) {
    @PostMapping("/{restaurantId}")
    fun addFavorite(
        @AuthenticationPrincipal userId: String,
        @PathVariable restaurantId: Long,
    ): ResponseEntity<ApiResponse<FavoriteStatusDto>> {
        val status = favoriteService.toggle(userId.toLong(), restaurantId)
        return ResponseEntity.ok(ApiResponse(success = true, data = status))
    }

    @DeleteMapping("/{restaurantId}")
    fun removeFavorite(
        @AuthenticationPrincipal userId: String,
        @PathVariable restaurantId: Long,
    ): ResponseEntity<ApiResponse<FavoriteStatusDto>> {
        val status = favoriteService.toggle(userId.toLong(), restaurantId)
        return ResponseEntity.ok(ApiResponse(success = true, data = status))
    }

    @GetMapping
    fun getMyFavorites(
        @AuthenticationPrincipal userId: String,
    ): ResponseEntity<ApiResponse<List<RestaurantDto>>> {
        val favorites = favoriteService.getMyFavorites(userId.toLong())
        return ResponseEntity.ok(ApiResponse(success = true, data = favorites))
    }

    @GetMapping("/ids")
    fun getMyFavoriteIds(
        @AuthenticationPrincipal userId: String,
    ): ResponseEntity<ApiResponse<Set<Long>>> {
        val ids = favoriteService.getMyFavoriteIds(userId.toLong())
        return ResponseEntity.ok(ApiResponse(success = true, data = ids))
    }
}
