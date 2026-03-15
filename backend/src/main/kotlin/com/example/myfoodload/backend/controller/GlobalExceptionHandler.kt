package com.example.myfoodload.backend.controller

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.client.HttpClientErrorException

data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
)

@ControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        log.warn("Bad request: {}", ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(400, "Bad Request", ex.message ?: "잘못된 요청입니다."),
        )
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException): ResponseEntity<ErrorResponse> {
        log.warn("Not found: {}", ex.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(404, "Not Found", ex.message ?: "리소스를 찾을 수 없습니다."),
        )
    }

    @ExceptionHandler(HttpClientErrorException.TooManyRequests::class)
    fun handleRateLimit(ex: HttpClientErrorException.TooManyRequests): ResponseEntity<ErrorResponse> {
        log.warn("Rate limit exceeded: {}", ex.message)
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
            ErrorResponse(429, "Too Many Requests", "요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요."),
        )
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleForbidden(ex: AccessDeniedException): ResponseEntity<ErrorResponse> {
        log.warn("Access denied: {}", ex.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse(403, "Forbidden", "접근 권한이 없습니다."),
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneral(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception: ", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(500, "Internal Server Error", "서버 내부 오류가 발생했습니다."),
        )
    }
}
