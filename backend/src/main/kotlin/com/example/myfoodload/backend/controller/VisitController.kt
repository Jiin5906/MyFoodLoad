package com.example.myfoodload.backend.controller

import com.example.myfoodload.backend.service.VisitService
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
 * 방문 완료 API (C-2)
 *
 * POST   /api/visits/{restaurantId}  — 방문 완료 체크
 * DELETE /api/visits/{restaurantId}  — 방문 체크 취소
 * GET    /api/visits/ids             — 방문한 맛집 ID 집합
 *
 * 보안: @AuthenticationPrincipal로 JWT Principal에서 userId 추출 (Gemini 지적 반영)
 */
@RestController
@RequestMapping("/api/visits")
class VisitController(
    private val visitService: VisitService,
) {
    @PostMapping("/{restaurantId}")
    fun markVisited(
        @AuthenticationPrincipal userId: String,
        @PathVariable restaurantId: Long,
    ): ResponseEntity<ApiResponse<Boolean>> {
        val added = visitService.markVisited(userId.toLong(), restaurantId)
        return ResponseEntity.ok(ApiResponse(success = true, data = added))
    }

    @DeleteMapping("/{restaurantId}")
    fun unmarkVisited(
        @AuthenticationPrincipal userId: String,
        @PathVariable restaurantId: Long,
    ): ResponseEntity<ApiResponse<Boolean>> {
        visitService.unmarkVisited(userId.toLong(), restaurantId)
        return ResponseEntity.ok(ApiResponse(success = true, data = false))
    }

    @GetMapping("/ids")
    fun getVisitedIds(
        @AuthenticationPrincipal userId: String,
    ): ResponseEntity<ApiResponse<Set<Long>>> {
        val ids = visitService.getVisitedIds(userId.toLong())
        return ResponseEntity.ok(ApiResponse(success = true, data = ids))
    }
}
