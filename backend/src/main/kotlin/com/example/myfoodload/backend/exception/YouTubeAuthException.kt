package com.example.myfoodload.backend.exception

/**
 * YouTube API 401/403 응답 시 발생하는 예외.
 *
 * - 401 Unauthorized: access token 만료 또는 미제공
 * - 403 Forbidden: youtube.readonly scope 부족
 *
 * VideoIngestionController에서 HTTP 401 응답으로 변환하여
 * 클라이언트가 "구글 연동이 만료되었습니다" 메시지를 표시할 수 있도록 한다.
 */
class YouTubeAuthException(
    message: String,
    val httpStatus: Int,
) : RuntimeException(message)
