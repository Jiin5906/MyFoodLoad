package com.example.myfoodload.backend.service.geocoding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * 카카오 로컬 API 키워드 검색 클라이언트.
 *
 * - category_group_code=FD6(음식점),CE7(카페)로 필터링
 * - 결과 없거나 오류 시 빈 리스트 반환 (비치명적 처리)
 * - GlobalScope 금지 → withContext(Dispatchers.IO) 사용
 */
@Component
class KakaoGeocodingClient(
    @Value("\${kakao.api-key}") private val apiKey: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient =
        RestClient
            .builder()
            .baseUrl("https://dapi.kakao.com")
            .build()

    /**
     * 카카오 키워드 검색 — 음식점/카페 장소 목록 반환.
     *
     * @param query 검색어 (예: "홍대 스시오마카세", "강남 라멘 이치란")
     * @return 검색 결과 최대 5개 (없으면 빈 리스트)
     */
    suspend fun searchKeyword(query: String): List<KakaoPlace> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            try {
                val response =
                    restClient
                        .get()
                        .uri { uriBuilder ->
                            uriBuilder
                                .path("/v2/local/search/keyword.json")
                                .queryParam("query", query)
                                .queryParam("category_group_code", "FD6")
                                .queryParam("size", 5)
                                .build()
                        }.header("Authorization", "KakaoAK $apiKey")
                        .retrieve()
                        .body<KakaoKeywordResponse>()
                        ?: KakaoKeywordResponse()

                log.debug("카카오 검색 '{}' → {}건", query, response.documents.size)
                response.documents
            } catch (e: Exception) {
                log.warn("카카오 검색 실패 '{}': {}", query, e.message)
                emptyList()
            }
        }

    /**
     * 좌표 → 행정구역명 변환 (역지오코딩).
     *
     * YouTube 트렌딩 검색 키워드 생성에 사용 (예: "은평구 맛집").
     * 결과 없거나 오류 시 null 반환.
     *
     * @return "${구/군명}" (예: "은평구", "강남구"), 실패 시 null
     */
    suspend fun getAreaName(
        latitude: Double,
        longitude: Double,
    ): String? =
        withContext(Dispatchers.IO) {
            try {
                val response =
                    restClient
                        .get()
                        .uri { uriBuilder ->
                            uriBuilder
                                .path("/v2/local/geo/coord2regioncode.json")
                                .queryParam("x", longitude)
                                .queryParam("y", latitude)
                                .build()
                        }.header("Authorization", "KakaoAK $apiKey")
                        .retrieve()
                        .body<KakaoRegionResponse>()
                        ?: KakaoRegionResponse()

                // 행정동(H) 우선 → 법정동(B) 폴백
                val doc =
                    response.documents.firstOrNull { it.regionType == "H" }
                        ?: response.documents.firstOrNull()
                val areaName = doc?.region2?.takeIf { it.isNotBlank() }
                log.debug("역지오코딩 ({}, {}) → {}", latitude, longitude, areaName)
                areaName
            } catch (e: Exception) {
                log.warn("역지오코딩 실패 ({}, {}): {}", latitude, longitude, e.message)
                null
            }
        }

    /**
     * 위치 기반 카카오 키워드 검색 — 사용자 근처 음식점/카페 목록 반환.
     *
     * YouTube 폴백 추천에서 Gemini가 정제한 검색어로 근처 맛집을 찾을 때 사용.
     *
     * @param query               검색어 (Gemini 정제 쿼리 또는 YouTube 쇼츠 제목)
     * @param latitude            기준 위도
     * @param longitude           기준 경도
     * @param radiusMeters        검색 반경 미터 (Kakao API 최대 20,000m)
     * @param categoryGroupCode   카카오 카테고리 그룹 코드 (FD6=음식점, CE7=카페/디저트)
     * @return 검색 결과 최대 3개 (없으면 빈 리스트)
     */
    suspend fun searchKeywordNearby(
        query: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 5_000,
        categoryGroupCode: String = "FD6",
    ): List<KakaoPlace> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            try {
                val response =
                    restClient
                        .get()
                        .uri { uriBuilder ->
                            uriBuilder
                                .path("/v2/local/search/keyword.json")
                                .queryParam("query", query)
                                .queryParam("category_group_code", categoryGroupCode)
                                .queryParam("x", longitude) // 카카오 API: x = 경도(longitude)
                                .queryParam("y", latitude) // 카카오 API: y = 위도(latitude)
                                .queryParam("radius", radiusMeters.coerceIn(1, 20_000))
                                .queryParam("sort", "distance")
                                .queryParam("size", 3)
                                .build()
                        }.header("Authorization", "KakaoAK $apiKey")
                        .retrieve()
                        .body<KakaoKeywordResponse>()
                        ?: KakaoKeywordResponse()

                log.debug(
                    "카카오 근처 검색 '{}' (반경 {}m, 코드={}) → {}건",
                    query,
                    radiusMeters,
                    categoryGroupCode,
                    response.documents.size,
                )
                response.documents
            } catch (e: Exception) {
                log.warn("카카오 근처 검색 실패 '{}': {}", query, e.message)
                emptyList()
            }
        }
}
