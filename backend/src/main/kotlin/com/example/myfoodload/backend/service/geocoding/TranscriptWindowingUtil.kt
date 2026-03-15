package com.example.myfoodload.backend.service.geocoding


/**
 * 자막 Context Windowing 유틸리티.
 *
 * 전체 자막 대신 장소 지시어 주변 ±150자만 추출하여 LLM 토큰 극한 절약.
 * 제3 방어선(LLM Queue)에서 토큰 다이어트 목적으로 사용.
 */
object TranscriptWindowingUtil {

    private const val WINDOW_RADIUS = 150
    private const val MAX_WINDOWS = 4

    /** 장소 지시어 — 상호명/주소가 언급될 가능성이 높은 문맥 키워드 */
    private val PLACE_INDICATOR_KEYWORDS = setOf(
        "식당", "맛집", "카페", "가게", "매장", "본점", "지점",
        "역", "동", "구", "로", "길",
        "여기", "이곳", "이 집", "이집", "저기",
        "오마카세", "라멘", "초밥", "스시", "파스타",
        "삼겹", "갈비", "국밥", "냉면", "치킨", "버거",
    )

    /**
     * 자막에서 장소 지시어 주변 ±150자 문맥만 추출.
     * 키워드가 없으면 앞 250자 반환 (토큰 절약 강화).
     */
    fun extractRelevantTranscript(transcript: String): String {
        val lower = transcript.lowercase()
        val windows = mutableListOf<IntRange>()

        for (keyword in PLACE_INDICATOR_KEYWORDS) {
            var startIdx = 0
            while (true) {
                val idx = lower.indexOf(keyword, startIdx)
                if (idx == -1) break
                val windowStart = (idx - WINDOW_RADIUS).coerceAtLeast(0)
                val windowEnd = (idx + keyword.length + WINDOW_RADIUS).coerceAtMost(transcript.length)
                windows.add(windowStart..windowEnd)
                startIdx = idx + keyword.length
            }
            if (windows.size >= MAX_WINDOWS * 2) break
        }

        if (windows.isEmpty()) return transcript.take(250)

        val merged = mergeRanges(windows.sortedBy { it.first })
        return merged.take(MAX_WINDOWS).joinToString(" ... ") { range ->
            transcript.substring(range.first, range.last.coerceAtMost(transcript.length))
        }
    }

    private fun mergeRanges(sorted: List<IntRange>): List<IntRange> {
        val result = mutableListOf<IntRange>()
        for (range in sorted) {
            val last = result.lastOrNull()
            if (last != null && range.first <= last.last) {
                result[result.lastIndex] = last.first..maxOf(last.last, range.last)
            } else {
                result.add(range)
            }
        }
        return result
    }
}
