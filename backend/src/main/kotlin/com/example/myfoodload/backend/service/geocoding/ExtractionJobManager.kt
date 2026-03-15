package com.example.myfoodload.backend.service.geocoding

import com.example.myfoodload.shared.dto.ExtractionJobStatusDto
import com.example.myfoodload.shared.dto.ExtractionResultDto
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Fire-and-Forget 맛집 추출 작업 관리자.
 *
 * - POST /extract 호출 시 백그라운드 코루틴으로 처리 시작 → 즉시 202 반환
 * - GET /status 폴링으로 진행 상태 확인
 * - ConcurrentHashMap.compute()로 원자적 중복 방지
 * - @PreDestroy로 Graceful Shutdown 보장
 */
@Component
class ExtractionJobManager(
    private val extractionService: RestaurantExtractionService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobStatuses = ConcurrentHashMap<Long, ExtractionJobStatusDto>()

    fun startExtraction(userId: Long): Boolean {
        var alreadyRunning = false
        jobStatuses.compute(userId) { _, current ->
            if (current?.status == "PROCESSING") {
                alreadyRunning = true
                current
            } else {
                ExtractionJobStatusDto(
                    status = "PROCESSING",
                    message = "맛집 추출 진행 중...",
                )
            }
        }

        if (alreadyRunning) {
            log.info("이미 추출 작업 진행 중 — userId=$userId")
            return false
        }

        scope.launch {
            try {
                val result = extractionService.extractAndSave(userId)
                jobStatuses[userId] = ExtractionJobStatusDto(
                    status = "COMPLETED",
                    message = "추출 완료",
                    result = result,
                )
                log.info("비동기 추출 완료 — userId=$userId, result=$result")
            } catch (e: Exception) {
                log.error("비동기 추출 실패 — userId=$userId", e)
                jobStatuses[userId] = ExtractionJobStatusDto(
                    status = "FAILED",
                    message = e.message ?: "추출 실패",
                    result = ExtractionResultDto(),
                )
            }
        }
        return true
    }

    fun getStatus(userId: Long): ExtractionJobStatusDto {
        val status = jobStatuses[userId] ?: ExtractionJobStatusDto(status = "IDLE")
        if (status.status == "COMPLETED" || status.status == "FAILED") {
            jobStatuses.remove(userId)
        }
        return status
    }

    @PreDestroy
    fun onDestroy() {
        log.info("ExtractionJobManager 종료 — 진행 중인 작업 취소")
        scope.cancel()
    }
}
