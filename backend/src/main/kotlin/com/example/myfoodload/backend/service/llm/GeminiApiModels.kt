package com.example.myfoodload.backend.service.llm

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// ── 요청 모델 ─────────────────────────────────────────────────────────────────

data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null,
)

data class GeminiContent(
    val role: String = "user",
    val parts: List<GeminiPart>,
)

data class GeminiPart(
    val text: String,
)

data class GeminiGenerationConfig(
    val responseMimeType: String = "application/json",
    val responseSchema: GeminiSchema? = null,
    val temperature: Double = 0.2, // 낮을수록 일관된 응답
    val maxOutputTokens: Int = 8192,
)

data class GeminiSchema(
    val type: String,
    val properties: Map<String, GeminiSchema>? = null,
    val items: GeminiSchema? = null,
    @JsonProperty("enum") val enumValues: List<String>? = null,
    val nullable: Boolean? = null,
    val required: List<String>? = null,
)

// ── 응답 모델 ─────────────────────────────────────────────────────────────────

@JsonIgnoreProperties(ignoreUnknown = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GeminiCandidate(
    val content: GeminiContent? = null,
)

// ── LLM 분석 결과 (JSON Schema로 강제된 응답) ─────────────────────────────────

@JsonIgnoreProperties(ignoreUnknown = true)
data class FoodPreferenceAnalysis(
    @JsonProperty("food_tags")
    val foodTags: List<FoodTagAnalysis> = emptyList(),
    @JsonProperty("ambiance_tags")
    val ambianceTags: List<String> = emptyList(),
    @JsonProperty("price_range")
    val priceRange: String? = null,
    val confidence: Double = 0.0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FoodTagAnalysis(
    val tag: String = "",
    val score: Double = 1.0,
)

// ── 맛집 추출 결과 (Phase 6.5) ────────────────────────────────────────────────

@JsonIgnoreProperties(ignoreUnknown = true)
data class RestaurantExtractionResult(
    val restaurants: List<ExtractedRestaurant> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExtractedRestaurant(
    @JsonProperty("video_id") val videoId: String = "",
    val name: String = "",
    @JsonProperty("search_query") val searchQuery: String = "",
    val confidence: Double = 0.0,
    @JsonProperty("recommendation_reason") val recommendationReason: String? = null,
)
