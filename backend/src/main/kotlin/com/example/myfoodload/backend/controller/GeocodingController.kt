package com.example.myfoodload.backend.controller

import com.example.myfoodload.backend.service.geocoding.ExtractionJobManager
import com.example.myfoodload.shared.dto.ExtractionJobStatusDto
import com.example.myfoodload.shared.response.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/geocoding")
class GeocodingController(
    private val jobManager: ExtractionJobManager,
) {
    /**
     * 맛집 추출 시작 (Fire-and-Forget).
     *
     * POST /api/geocoding/extract
     * - 즉시 202 Accepted 반환, 백그라운드에서 추출 진행
     * - 이미 처리 중이면 409 Conflict 반환
     */
    @PostMapping("/extract")
    fun extractRestaurants(
        @AuthenticationPrincipal userId: String,
    ): ResponseEntity<ApiResponse<String>> {
        val started = jobManager.startExtraction(userId.toLong())
        return if (started) {
            ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse(success = true, data = "추출 작업이 시작되었습니다"))
        } else {
            ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse(success = false, data = "이미 추출 작업이 진행 중입니다"))
        }
    }

    /**
     * 맛집 추출 상태 폴링.
     *
     * GET /api/geocoding/status
     * - IDLE / PROCESSING / COMPLETED / FAILED 상태 반환
     * - COMPLETED/FAILED는 1회 전달 후 자동 초기화
     */
    @GetMapping("/status")
    fun getExtractionStatus(
        @AuthenticationPrincipal userId: String,
    ): ResponseEntity<ApiResponse<ExtractionJobStatusDto>> {
        val status = jobManager.getStatus(userId.toLong())
        return ResponseEntity.ok(ApiResponse(success = true, data = status))
    }
}
