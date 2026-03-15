package com.example.myfoodload.backend.service.geocoding

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 글로벌 LLM RPM 제한기.
 *
 * 제3 방어선에서 Gemini API 429를 방지하기 위해
 * 동시 LLM 호출 수를 제한하고, Mutex 기반으로 호출 간 최소 간격을 보장한다.
 */
@Component
class LlmRateLimiter {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAX_CONCURRENT = 2
        private const val COOLDOWN_MS = 6500L
    }

    private val semaphore = Semaphore(MAX_CONCURRENT)
    private val mutex = Mutex()
    private var lastCallTime = 0L

    /**
     * LLM 호출 전 RPM 게이트.
     * Semaphore로 동시 실행 수 제한 + Mutex로 호출 간 최소 간격 보장.
     */
    suspend fun acquire(userId: Long) {
        semaphore.acquire()
        mutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastCallTime
            if (elapsed < COOLDOWN_MS) {
                delay(COOLDOWN_MS - elapsed)
            }
            lastCallTime = System.currentTimeMillis()
        }
        log.debug("LLM 슬롯 획득 — userId={}", userId)
    }

    fun release() {
        semaphore.release()
    }
}
