package com.example.myfoodload.backend.service.llm

import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Google Gemini 2.0 Flash REST API 클라이언트.
 *
 * responseMimeType = "application/json" + responseSchema로
 * Function Calling 없이도 구조화된 JSON 응답 강제 (환각 방지).
 * GlobalScope 금지 → withContext(Dispatchers.IO) 사용.
 */
@Component
class GeminiClient(
    @Value("\${gemini.api-key}") private val apiKey: String,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Gemini가 "0." 같은 잘못된 숫자 리터럴을 반환하므로 관대한 파서 사용
    private val lenientMapper: ObjectMapper =
        JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS)
            .build()
            .registerKotlinModule()

    private val restClient =
        RestClient
            .builder()
            .baseUrl("https://generativelanguage.googleapis.com")
            .build()

    // 서킷 브레이커: RPD 소진된 모델을 세션 내에서 스킵 (ConcurrentHashMap — 스레드 안전)
    private val deadModels = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** 서킷 브레이커 상태 초기화 (새 세션/테스트 시) */
    fun resetCircuitBreaker() {
        deadModels.clear()
        log.info("서킷 브레이커 초기화 — 모든 모델 활성화")
    }

    companion object {
        private const val MODEL = "gemini-2.0-flash"
        private val FALLBACK_MODELS = listOf("gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-2.5-flash")
        private const val MAX_RETRIES = 3
        private val RETRY_DELAYS_MS = longArrayOf(3000, 6000, 12000)

        // 맛집 추출용 JSON Schema
        private val RESTAURANT_EXTRACTION_SCHEMA =
            GeminiSchema(
                type = "OBJECT",
                required = listOf("restaurants"),
                properties =
                    mapOf(
                        "restaurants" to
                            GeminiSchema(
                                type = "ARRAY",
                                items =
                                    GeminiSchema(
                                        type = "OBJECT",
                                        required = listOf("video_id", "name", "search_query", "confidence"),
                                        properties =
                                            mapOf(
                                                "video_id" to GeminiSchema(type = "STRING"),
                                                "name" to GeminiSchema(type = "STRING"),
                                                "search_query" to GeminiSchema(type = "STRING"),
                                                "confidence" to GeminiSchema(type = "NUMBER"),
                                                // B-4: 추천 이유 스토리텔링 (nullable — 기존 호환성 유지)
                                                "recommendation_reason" to
                                                    GeminiSchema(
                                                        type = "STRING",
                                                        nullable = true,
                                                    ),
                                            ),
                                    ),
                            ),
                    ),
            )

        private val RESTAURANT_EXTRACTION_SYSTEM_PROMPT =
            """
            당신은 맛집 정보 추출 전문가입니다.
            유튜브 영상 데이터에서 언급되거나 연관된 실제 음식점(맛집) 정보를 추출하세요.

            규칙:
            1. 다음 기준으로 음식점을 추출하세요 (우선순위):
               (1) 영상에서 직접 언급된 구체적인 음식점 상호명 (confidence 0.8~1.0)
               (2) 제목/해시태그에 지역명+음식 종류가 있어 추정 가능한 경우 (confidence 0.5~0.7)
                   - 예: "#강남맛집 #라멘" → 강남의 유명 라멘 맛집을 search_query로 추출
               (3) 채널명이 식당/맛집인 경우 (confidence 0.5~0.7)
            2. 각 맛집에 대해:
               - video_id: 반드시 입력에서 제공된 [video_id: ...] 값을 정확히 그대로 복사하세요.
                 절대로 임의로 생성하거나 수정하지 마세요. 모르는 경우 해당 맛집을 생략하세요.
               - name: 음식점 이름 또는 추정 검색어 (예: "강남 라멘 맛집")
               - search_query: 카카오 지도 검색용 검색어 (지역+이름 조합, 예: "홍대 이치란 라멘")
               - confidence: 실제 존재하는 음식점이라는 확신도 0.0~1.0
               - recommendation_reason: 이 맛집을 추천하는 이유를 한 문장으로 작성 (예: "좋아요한 먹방에서 등장한 홍대 대표 라멘 맛집이에요")
                 - 사용자의 유튜브 좋아요 기록과 연결하여 자연스럽게 작성하세요
                 - 없으면 null
            3. confidence 0.3 미만은 포함하지 마세요
            4. 맛집이 전혀 연관되지 않은 영상만 건너뛰세요
            5. 완전히 가상의 음식점만 제외하세요 (실제 존재할 가능성이 있으면 포함)
            """.trimIndent()

        // 트렌딩 파이프라인용 검색어 추출 JSON Schema
        private val SEARCH_QUERY_SCHEMA =
            GeminiSchema(
                type = "OBJECT",
                required = listOf("results"),
                properties =
                    mapOf(
                        "results" to
                            GeminiSchema(
                                type = "ARRAY",
                                items =
                                    GeminiSchema(
                                        type = "OBJECT",
                                        required = listOf("index", "search_query"),
                                        properties =
                                            mapOf(
                                                "index" to GeminiSchema(type = "INTEGER"),
                                                "search_query" to GeminiSchema(type = "STRING"),
                                            ),
                                    ),
                            ),
                    ),
            )

        private val SEARCH_QUERY_SYSTEM_PROMPT =
            """
            당신은 YouTube 맛집 쇼츠에서 카카오 지도 검색어를 추출하는 전문가입니다.

            규칙:
            1. 각 쇼츠의 제목/설명에서 실제 음식점 상호명을 찾아 "지역명 상호명" 형태의 검색어를 만드세요
            2. 어그로성 문구(🔥, ㄷㄷ, 레전드, 실화, 미쳤다 등), 해시태그, 광고 문구는 제거하세요
            3. 검색어 예시: "은평구 냉삼슈퍼", "홍대 이치란 라멘", "강남 도원 중식당"
            4. 상호명이 명확하지 않으면 해당 index를 결과에서 생략하세요
            5. 음식점이 아닌 일반 콘텐츠(여행, 뷰티 등)도 생략하세요
            """.trimIndent()

        // 음식 선호도 분석용 JSON Schema (환각 방지 — null 허용)
        private val FOOD_PREFERENCE_SCHEMA =
            GeminiSchema(
                type = "OBJECT",
                required = listOf("food_tags", "ambiance_tags", "confidence"),
                properties =
                    mapOf(
                        "food_tags" to
                            GeminiSchema(
                                type = "ARRAY",
                                items =
                                    GeminiSchema(
                                        type = "OBJECT",
                                        required = listOf("tag", "score"),
                                        properties =
                                            mapOf(
                                                "tag" to GeminiSchema(type = "STRING"),
                                                "score" to GeminiSchema(type = "NUMBER"),
                                            ),
                                    ),
                            ),
                        "ambiance_tags" to
                            GeminiSchema(
                                type = "ARRAY",
                                items = GeminiSchema(type = "STRING"),
                            ),
                        "price_range" to
                            GeminiSchema(
                                type = "STRING",
                                enumValues = listOf("LOW", "MEDIUM", "HIGH"),
                                nullable = true,
                            ),
                        "confidence" to GeminiSchema(type = "NUMBER"),
                    ),
            )

        private val SYSTEM_PROMPT =
            """
            당신은 음식 취향 분석 전문가입니다.
            사용자가 YouTube에서 좋아요를 누른 영상들의 정보를 분석하여
            사용자의 음식 선호도를 추출하세요.

            규칙:
            1. food_tags: 음식 종류, 요리 카테고리, 구체적인 음식명 (최대 20개)
               - score 0.0~1.0: 해당 음식이 얼마나 자주/강하게 등장하는지
               - 예: "한식", "라멘", "오마카세", "매운음식", "디저트", "치킨", "카페"
               - 직접적인 음식 영상이 아니더라도, 영상 내용에서 간접적으로 추론할 수 있는 음식 취향도 포함
               - 최소 3개 이상의 태그를 반드시 추출하세요 (음식 관련 단서가 있다면)
            2. ambiance_tags: 식당 분위기나 상황 특성 (최대 10개)
               - 예: "혼밥가능", "데이트", "조용한", "뷰맛집", "가성비"
            3. price_range: 전반적 가격대 LOW/MEDIUM/HIGH (모르면 null)
            4. confidence: 분석 신뢰도 0.0~1.0
               - 음식 관련 영상이 1개라도 있으면 최소 0.3 이상
            5. 음식과 무관한 영상은 무시하고 관련 영상만 분석
            """.trimIndent()
    }

    /**
     * YouTube 쇼츠 제목+설명 목록에서 카카오 검색용 "지역명 상호명" 검색어 일괄 추출.
     *
     * - 입력 items 인덱스와 결과 리스트 인덱스가 1:1 대응 (상호명 불명확 시 null)
     * - Gemini 429 또는 API 키 미설정 시 emptyList() 반환 → 호출부에서 regex 폴백 처리
     *
     * @param items (title, description?) 쌍의 리스트
     */
    suspend fun extractSearchQueries(items: List<Pair<String, String?>>): List<String?> =
        withContext(Dispatchers.IO) {
            if (items.isEmpty()) return@withContext emptyList()
            if (apiKey.isBlank()) {
                log.warn("gemini.api-key 미설정 — 검색어 추출 불가")
                return@withContext emptyList()
            }
            try {
                val inputText =
                    items
                        .mapIndexed { i, (title, desc) ->
                            "[$i] 제목: \"$title\"" +
                                (desc?.takeIf { it.isNotBlank() }?.let { "\n    설명: \"${it.take(200)}\"" } ?: "")
                        }.joinToString("\n")

                val request =
                    GeminiRequest(
                        systemInstruction =
                            GeminiContent(
                                role = "model",
                                parts = listOf(GeminiPart(SEARCH_QUERY_SYSTEM_PROMPT)),
                            ),
                        contents =
                            listOf(
                                GeminiContent(
                                    role = "user",
                                    parts = listOf(GeminiPart(inputText)),
                                ),
                            ),
                        generationConfig =
                            GeminiGenerationConfig(
                                responseMimeType = "application/json",
                                responseSchema = SEARCH_QUERY_SCHEMA,
                                temperature = 0.1,
                            ),
                    )

                val response =
                    restClient
                        .post()
                        .uri("/v1beta/models/$MODEL:generateContent?key=$apiKey")
                        .header("Content-Type", "application/json")
                        .body(request)
                        .retrieve()
                        .body<GeminiResponse>()
                        ?: return@withContext emptyList()

                val jsonText =
                    response.candidates
                        .firstOrNull()
                        ?.content
                        ?.parts
                        ?.firstOrNull()
                        ?.text
                        ?: return@withContext emptyList()

                val queryMap = mutableMapOf<Int, String>()
                objectMapper.readTree(jsonText).path("results").forEach { node ->
                    val idx = node.path("index").asInt(-1)
                    val query = node.path("search_query").asText("")
                    if (idx in items.indices && query.isNotBlank()) queryMap[idx] = query
                }
                log.info("Gemini 검색어 추출 — {}개 입력 → {}개 추출", items.size, queryMap.size)
                List(items.size) { i -> queryMap[i] }
            } catch (e: HttpClientErrorException.TooManyRequests) {
                log.warn("Gemini 429 — 검색어 추출 건너뜀, regex 폴백")
                emptyList()
            } catch (e: Exception) {
                log.warn("Gemini 검색어 추출 실패: ${e.message}")
                emptyList()
            }
        }

    /**
     * 영상 데이터 목록에서 언급된 맛집 추출.
     * JSON Schema로 구조화된 응답 보장.
     */
    suspend fun extractRestaurants(videosText: String): RestaurantExtractionResult =
        withContext(Dispatchers.IO) {
            val request =
                GeminiRequest(
                    systemInstruction =
                        GeminiContent(
                            role = "model",
                            parts = listOf(GeminiPart(RESTAURANT_EXTRACTION_SYSTEM_PROMPT)),
                        ),
                    contents =
                        listOf(
                            GeminiContent(
                                role = "user",
                                parts = listOf(GeminiPart(videosText)),
                            ),
                        ),
                    generationConfig =
                        GeminiGenerationConfig(
                            responseMimeType = "application/json",
                            responseSchema = RESTAURANT_EXTRACTION_SCHEMA,
                            temperature = 0.1,
                        ),
                )

            val jsonText = callGeminiWithRetryAndFallback(request)

            log.debug("Gemini 맛집 추출 원본: $jsonText")
            try {
                val repaired = repairTruncatedJson(jsonText)
                lenientMapper.readValue(repaired, RestaurantExtractionResult::class.java)
            } catch (e: com.fasterxml.jackson.core.JacksonException) {
                log.warn("Gemini 맛집 추출 JSON 파싱 실패: ${e.message}")
                RestaurantExtractionResult()
            }
        }

    /**
     * 영상 데이터 목록을 분석하여 음식 선호도 프로파일 반환.
     * JSON Schema로 구조화된 응답 보장.
     */
    suspend fun analyzeFoodPreference(videoDataText: String): FoodPreferenceAnalysis =
        withContext(Dispatchers.IO) {
            // 입력 텍스트가 너무 크면 Gemini가 출력을 잘라냄 → 최대 15개 영상으로 제한
            val trimmedText = trimVideoInput(videoDataText, maxVideos = 15)

            val request =
                GeminiRequest(
                    systemInstruction =
                        GeminiContent(
                            role = "model",
                            parts = listOf(GeminiPart(SYSTEM_PROMPT)),
                        ),
                    contents =
                        listOf(
                            GeminiContent(
                                role = "user",
                                parts = listOf(GeminiPart(trimmedText)),
                            ),
                        ),
                    generationConfig =
                        GeminiGenerationConfig(
                            responseMimeType = "application/json",
                            responseSchema = FOOD_PREFERENCE_SCHEMA,
                            temperature = 0.2,
                        ),
                )

            // 잘린 JSON / 잘못된 숫자 대비: 최대 2회 시도
            repeat(2) { attempt ->
                try {
                    val rawJson = callGeminiWithRetryAndFallback(request)
                    log.debug("Gemini 원본 응답 (attempt=$attempt): $rawJson")
                    val repaired = repairTruncatedJson(rawJson)
                    return@withContext lenientMapper.readValue(repaired, FoodPreferenceAnalysis::class.java)
                } catch (e: com.fasterxml.jackson.core.JacksonException) {
                    log.warn("Gemini JSON 파싱 실패 (attempt=$attempt): ${e.message}")
                    if (attempt == 1) {
                        log.error("Gemini JSON 파싱 최종 실패, 빈 결과 반환")
                        return@withContext FoodPreferenceAnalysis()
                    }
                }
            }
            FoodPreferenceAnalysis()
        }

    /** 영상 입력 텍스트를 maxVideos개로 제한 (구분자: 빈 줄 또는 "[video_id:" 패턴) */
    private fun trimVideoInput(text: String, maxVideos: Int): String {
        val segments = text.split(Regex("""\n(?=\[video_id:)"""))
        if (segments.size <= maxVideos) return text
        log.info("입력 영상 ${segments.size}개 → ${maxVideos}개로 축소")
        return segments.take(maxVideos).joinToString("\n")
    }

    /** 잘린 JSON 복구: 미닫힌 괄호/따옴표 보완 */
    private fun repairTruncatedJson(json: String): String {
        var s = json.trim()
        // 잘린 문자열 값 닫기
        val quoteCount = s.count { it == '"' }
        if (quoteCount % 2 != 0) s += "\""
        // 닫히지 않은 배열/객체 괄호 보완
        val openBrackets = s.count { it == '[' } - s.count { it == ']' }
        val openBraces = s.count { it == '{' } - s.count { it == '}' }
        repeat(openBrackets) { s += "]" }
        repeat(openBraces) { s += "}" }
        if (s != json.trim()) log.info("잘린 JSON 복구 완료")
        return s
    }

    /**
     * Gemini API 호출 — 429 Exponential Backoff 재시도 + 모델 폴백.
     *
     * 1. RPM 쿼터 초과(retryDelay 있음) → 대기 후 재시도 (최대 3회)
     * 2. RPD 일일 쿼터 초과(limit: 0 + PerDay) → 다음 모델로 폴백
     * 3. 모든 모델 실패 → 최종 예외 throw
     */
    private suspend fun callGeminiWithRetryAndFallback(request: GeminiRequest): String {
        for (model in FALLBACK_MODELS) {
            if (model in deadModels) {
                log.debug("서킷 브레이커: $model 스킵 (RPD 소진)")
                continue
            }
            for (attempt in 0 until MAX_RETRIES) {
                try {
                    val response =
                        restClient
                            .post()
                            .uri("/v1beta/models/$model:generateContent?key=$apiKey")
                            .header("Content-Type", "application/json")
                            .body(request)
                            .retrieve()
                            .body<GeminiResponse>()
                            ?: throw RuntimeException("Gemini API 응답 없음 (model=$model)")

                    val text =
                        response.candidates
                            .firstOrNull()
                            ?.content
                            ?.parts
                            ?.firstOrNull()
                            ?.text
                            ?: throw RuntimeException("Gemini 응답에 텍스트 없음 (model=$model)")

                    if (model != MODEL) log.info("폴백 모델 $model 성공")
                    return text
                } catch (e: HttpClientErrorException.TooManyRequests) {
                    val body = e.responseBodyAsString
                    val isRpdExhausted = body.contains("PerDay") || body.contains("limit: 0")

                    if (isRpdExhausted) {
                        deadModels.add(model)
                        log.warn("Gemini $model 일일 쿼터(RPD) 소진 — 서킷 브레이커 마킹, 다음 모델로 폴백")
                        break // 이 모델 포기, 다음 모델 시도
                    }

                    // RPM 쿼터: 대기 후 재시도
                    val delayMs = RETRY_DELAYS_MS.getOrElse(attempt) { 12000L }
                    log.warn("Gemini $model 429 (RPM) — ${delayMs}ms 대기 후 재시도 (${attempt + 1}/$MAX_RETRIES)")
                    delay(delayMs)
                }
            }
        }
        throw HttpClientErrorException.TooManyRequests.create(
            org.springframework.http.HttpStatusCode
                .valueOf(429),
            "모든 Gemini 모델 쿼터 소진",
            org.springframework.http.HttpHeaders.EMPTY,
            ByteArray(0),
            null,
        )
    }
}
