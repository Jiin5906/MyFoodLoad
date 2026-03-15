package com.example.myfoodload.backend.service.llm

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * YouTube timedtext API를 통한 자막 수집 클라이언트.
 *
 * 공식 captions.download API는 youtube.force-ssl 스코프가 필요하므로
 * 공개 영상의 자동 생성 자막을 timedtext 엔드포인트로 수집한다.
 * 자막이 없는 경우 null 반환 → LLM 분석 시 메타데이터만 사용.
 */
@Component
class YouTubeCaptionClient {
    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient =
        RestClient
            .builder()
            .baseUrl("https://www.youtube.com")
            .build()

    /**
     * 주어진 videoId의 자막 텍스트를 반환.
     * 한국어 자동생성 → 한국어 수동 → 영어 자동생성 순서로 시도.
     */
    suspend fun fetchTranscript(videoId: String): String? =
        withContext(Dispatchers.IO) {
            fetchTimedText(videoId, "ko", asr = true)
                ?: fetchTimedText(videoId, "ko", asr = false)
                ?: fetchTimedText(videoId, "en", asr = true)
        }

    private fun fetchTimedText(
        videoId: String,
        lang: String,
        asr: Boolean,
    ): String? =
        try {
            val kind = if (asr) "asr" else "standard"
            val response =
                restClient
                    .get()
                    .uri { builder ->
                        builder
                            .path("/api/timedtext")
                            .queryParam("v", videoId)
                            .queryParam("lang", lang)
                            .queryParam("fmt", "json3")
                            .apply { if (asr) queryParam("kind", kind) }
                            .build()
                    }.retrieve()
                    .body<TimedTextResponse>()

            val text =
                response
                    ?.events
                    ?.flatMap { it.segs ?: emptyList() }
                    ?.mapNotNull { it.utf8 }
                    ?.joinToString(" ")
                    ?.replace("\n", " ")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }

            text
        } catch (e: Exception) {
            log.debug("자막 수집 실패 videoId=$videoId lang=$lang asr=$asr: ${e.message}")
            null
        }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class TimedTextResponse(
        val events: List<TimedTextEvent>? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class TimedTextEvent(
        val segs: List<TimedTextSeg>? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class TimedTextSeg(
        val utf8: String? = null,
    )
}
