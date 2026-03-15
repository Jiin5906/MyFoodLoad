package com.example.myfoodload.backend.controller

import com.example.myfoodload.backend.exception.YouTubeAuthException
import com.example.myfoodload.backend.service.youtube.VideoIngestionService
import com.example.myfoodload.shared.dto.IngestionResultDto
import com.example.myfoodload.shared.dto.YoutubeIngestRequest
import com.example.myfoodload.shared.response.ApiResponse
import com.example.myfoodload.shared.response.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/youtube")
class VideoIngestionController(
    private val videoIngestionService: VideoIngestionService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * YouTube 좋아요 영상 수집 트리거.
     * JWT로 인증된 사용자의 YouTube access token을 받아 수집 파이프라인 실행.
     *
     * POST /api/youtube/ingest
     * Authorization: Bearer {jwtToken}
     * Body: { "youtubeAccessToken": "ya29.xxx" }
     *
     * 응답:
     * - 200 OK: 수집 성공
     * - 401 Unauthorized: YouTube access token 만료 또는 scope 부족
     */
    @PostMapping("/ingest")
    suspend fun ingestLikedVideos(
        @AuthenticationPrincipal userId: String,
        @RequestBody request: YoutubeIngestRequest,
    ): ResponseEntity<ApiResponse<IngestionResultDto>> =
        try {
            val result =
                videoIngestionService.ingestLikedVideos(
                    userId = userId.toLong(),
                    youtubeAccessToken = request.youtubeAccessToken,
                )
            ResponseEntity.ok(ApiResponse(success = true, data = result))
        } catch (e: YouTubeAuthException) {
            log.warn("YouTube 인증 오류 — userId={}, status={}: {}", userId, e.httpStatus, e.message)
            ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(
                    ApiResponse(
                        success = false,
                        error =
                            ErrorResponse(
                                code = "YOUTUBE_TOKEN_EXPIRED",
                                message = "구글 연동이 만료되었습니다. 다시 시도해 주세요.",
                            ),
                    ),
                )
        }
}
