package com.example.myfoodload.backend.controller

import com.example.myfoodload.backend.service.llm.LlmAnalysisService
import com.example.myfoodload.shared.dto.UserPreferenceDto
import com.example.myfoodload.shared.response.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/llm")
class LlmAnalysisController(
    private val llmAnalysisService: LlmAnalysisService,
) {
    /**
     * YouTube 좋아요 영상 LLM 분석 실행.
     * 자막 수집 + Gemini 분석 + UserPreference 저장 후 결과 반환.
     *
     * POST /api/llm/analyze
     * Authorization: Bearer {jwtToken}
     */
    @PostMapping("/analyze")
    suspend fun analyze(
        @AuthenticationPrincipal userId: String,
    ): ResponseEntity<ApiResponse<UserPreferenceDto>> {
        val result = llmAnalysisService.analyzeAndSave(userId.toLong())
        return ResponseEntity.ok(ApiResponse(success = true, data = result))
    }

    /**
     * 기존 분석 결과 조회 (재분석 없이).
     *
     * GET /api/llm/preferences
     * Authorization: Bearer {jwtToken}
     */
    @GetMapping("/preferences")
    fun getPreferences(
        @AuthenticationPrincipal userId: String,
    ): ResponseEntity<ApiResponse<UserPreferenceDto?>> {
        val result = llmAnalysisService.getPreference(userId.toLong())
        return ResponseEntity.ok(ApiResponse(success = true, data = result))
    }
}
