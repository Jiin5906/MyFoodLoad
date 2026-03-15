package com.example.myfoodload.backend.service.geocoding

/**
 * 쇼츠 제목에서 상호명 검색용 쿼리를 정제하는 유틸리티.
 *
 * 제2 방어선(Regex Fast-Track)에서 사용:
 * 1. 브래킷 `[]` / 괄호 `()` 안의 텍스트 우선 추출 (상호명이 들어가는 패턴)
 * 2. 해시태그 제거, 불용어 제거, 이모지 제거
 * 3. 남은 토큰 중 지역명 + 상호명 조합을 검색어로 반환
 */
object RegexFastTrackUtil {

    private val BRACKET_REGEX = Regex("\\[([^]]+)]")
    private val PAREN_REGEX = Regex("\\(([^)]+)\\)")
    private val HASHTAG_REGEX = Regex("#\\S+")
    private val EMOJI_REGEX = Regex("[\\p{So}\\p{Cn}\\uFE0F\\u200D]+")
    private val SPECIAL_CHAR_REGEX = Regex("[!?~.,;:@'\"…|/\\\\]+")
    private val WHITESPACE_REGEX = Regex("\\s+")

    private val STOP_WORDS = setOf(
        "shorts", "#shorts", "vlog", "브이로그", "먹방", "mukbang", "asmr",
        "추천", "탐방", "리뷰", "review", "eng", "sub", "eng sub",
        "존맛", "존맛탱", "꿀맛", "핵맛", "레전드", "미쳤", "대박",
        "맛집", "투어", "일상", "소개", "인생", "최고", "찐",
        "음식", "먹는", "먹기", "먹을", "가봤", "가보", "다녀",
        "솔직", "후기", "방문", "도전", "체험", "모음",
    )

    private val LOCATION_HINTS = setOf(
        "홍대", "강남", "이태원", "신촌", "건대", "잠실", "명동", "종로",
        "압구정", "청담", "을지로", "연남", "망원", "합정", "성수",
        "이수", "사당", "신림", "노량진", "여의도", "마포", "용산",
        "판교", "분당", "수원", "인천", "대전", "부산", "제주",
        "해운대", "서면", "전주", "경주", "춘천", "속초", "강릉",
    )

    /**
     * 제목에서 카카오 검색용 쿼리 추출.
     *
     * 전략:
     * 1. [상호명] 또는 (상호명) 패턴이 있으면 → 해당 텍스트 우선 사용
     * 2. 없으면 → 불용어 제거 후 남은 토큰에서 지역명 + 상호명 조합
     *
     * @return 검색 쿼리 (2자 미만이면 빈 문자열)
     */
    fun extractSearchQuery(title: String): String {
        val bracketContent = extractBracketContent(title)
        if (bracketContent.isNotBlank() && bracketContent.length >= 2) {
            return bracketContent
        }

        val cleaned = cleanTitle(title)
        return if (cleaned.length >= 2) cleaned else ""
    }

    /**
     * 브래킷/괄호 안의 텍스트 추출.
     * 예: "[카와카츠] 홍대 돈까스" → "카와카츠"
     * 예: "(연돈볼카츠)" → "연돈볼카츠"
     */
    private fun extractBracketContent(title: String): String {
        val bracketMatch = BRACKET_REGEX.find(title)
        if (bracketMatch != null) {
            val content = bracketMatch.groupValues[1].trim()
            val cleaned = removeStopWords(content)
            if (cleaned.length >= 2) return cleaned
        }

        val parenMatch = PAREN_REGEX.find(title)
        if (parenMatch != null) {
            val content = parenMatch.groupValues[1].trim()
            val cleaned = removeStopWords(content)
            if (cleaned.length >= 2) return cleaned
        }

        return ""
    }

    /**
     * 제목 정제: 해시태그, 이모지, 특수문자, 불용어 제거 후 핵심 토큰 반환.
     */
    private fun cleanTitle(title: String): String {
        var cleaned = title
        cleaned = HASHTAG_REGEX.replace(cleaned, " ")
        cleaned = EMOJI_REGEX.replace(cleaned, " ")
        cleaned = SPECIAL_CHAR_REGEX.replace(cleaned, " ")
        cleaned = WHITESPACE_REGEX.replace(cleaned, " ").trim()

        val tokens = cleaned.split(" ")
            .map { it.trim() }
            .filter { it.isNotBlank() && it.lowercase() !in STOP_WORDS }

        if (tokens.isEmpty()) return ""

        val locationToken = tokens.firstOrNull { tok ->
            LOCATION_HINTS.any { tok.contains(it) }
        }
        val nameTokens = tokens.filter { tok ->
            tok != locationToken && tok.lowercase() !in STOP_WORDS
        }

        return buildString {
            if (locationToken != null) append("$locationToken ")
            append(nameTokens.take(2).joinToString(" "))
        }.trim()
    }

    private fun removeStopWords(text: String): String {
        var result = text
        STOP_WORDS.forEach { word ->
            result = result.replace(word, "", ignoreCase = true)
        }
        return result.trim()
    }
}
