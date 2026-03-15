package com.example.myfoodload.backend.service.geocoding

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 카카오 로컬 API — 키워드 검색 응답 모델
 *
 * GET https://dapi.kakao.com/v2/local/search/keyword.json
 * Authorization: KakaoAK {REST_API_KEY}
 */

@JsonIgnoreProperties(ignoreUnknown = true)
data class KakaoKeywordResponse(
    val documents: List<KakaoPlace> = emptyList(),
    val meta: KakaoMeta = KakaoMeta(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KakaoPlace(
    @JsonProperty("id") val id: String = "",
    @JsonProperty("place_name") val placeName: String = "",
    @JsonProperty("category_name") val categoryName: String = "",
    @JsonProperty("category_group_code") val categoryGroupCode: String = "",
    @JsonProperty("address_name") val addressName: String = "",
    @JsonProperty("road_address_name") val roadAddressName: String = "",
    @JsonProperty("x") val x: String = "", // 경도(longitude)
    @JsonProperty("y") val y: String = "", // 위도(latitude)
    @JsonProperty("phone") val phone: String = "",
    @JsonProperty("place_url") val placeUrl: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KakaoMeta(
    @JsonProperty("total_count") val totalCount: Int = 0,
    @JsonProperty("pageable_count") val pageableCount: Int = 0,
    @JsonProperty("is_end") val isEnd: Boolean = true,
)

/** 카카오 좌표→행정구역 변환 응답 모델 (coord2regioncode.json) */
@JsonIgnoreProperties(ignoreUnknown = true)
data class KakaoRegionResponse(
    val documents: List<KakaoRegionDocument> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KakaoRegionDocument(
    @JsonProperty("region_type") val regionType: String = "", // "H" = 행정동, "B" = 법정동
    @JsonProperty("address_name") val addressName: String = "",
    @JsonProperty("region_1depth_name") val region1: String = "", // 시/도
    @JsonProperty("region_2depth_name") val region2: String = "", // 구/군
    @JsonProperty("region_3depth_name") val region3: String = "", // 동/읍/면
)
