package com.example.myfoodload.backend.exception

/**
 * YouTube API 호출 중 인증 외 오류 발생 시 사용하는 예외.
 *
 * 네트워크 오류, 서버 오류(5xx), 쿼터 초과(429) 등
 * 인증과 무관한 YouTube API 실패를 구분하기 위해 사용.
 */
class YouTubeFetchException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
