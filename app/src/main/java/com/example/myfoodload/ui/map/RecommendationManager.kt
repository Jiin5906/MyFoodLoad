package com.example.myfoodload.ui.map

import android.content.Context
import android.util.Log
import com.example.myfoodload.data.local.db.CachedRecommendationEntity
import com.example.myfoodload.data.local.db.RecommendationDatabase
import com.example.myfoodload.data.location.getLocation
import com.example.myfoodload.data.remote.RecommendationApiService
import com.example.myfoodload.shared.dto.RecommendedRestaurantDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class SnackbarEvent(val message: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null)

/**
 * 맛집 추천 데이터 로딩 담당.
 *
 * PERSONALIZED(좋아요 기반) + TRENDING(YouTube 조회수 기반) 양쪽 관리.
 * Room 캐시 폴백 포함.
 */
class RecommendationManager(
    private val recommendationApiService: RecommendationApiService,
    private val recommendationDatabase: RecommendationDatabase,
) {
    private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Idle)
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _trendingUiState = MutableStateFlow<MapUiState>(MapUiState.Idle)
    val trendingUiState: StateFlow<MapUiState> = _trendingUiState.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<SnackbarEvent>(extraBufferCapacity = 1)
    val snackbarEvents: SharedFlow<SnackbarEvent> = _snackbarEvents.asSharedFlow()

    private val gson = Gson()

    companion object {
        private const val TAG = "RecommendationManager"
        private const val DEFAULT_LIMIT = 20
        private const val TRENDING_LIMIT = 15
    }

    suspend fun loadNearbyRestaurants(context: Context, excludeVisited: Boolean) {
        _uiState.emit(MapUiState.Loading)
        try {
            val location = getLocation(context)
            if (location == null) {
                Log.w(TAG, "위치 조회 실패 — 캐시 폴백")
                loadFromCache()
                return
            }
            Log.d(TAG, "위치 획득: lat=${location.latitude}, lon=${location.longitude}")

            val result = recommendationApiService.getRecommendations(
                lat = location.latitude,
                lon = location.longitude,
                limit = DEFAULT_LIMIT,
                excludeVisited = excludeVisited,
            )
            val restaurants = result.data ?: emptyList()

            recommendationDatabase.cacheDao().insertCache(
                CachedRecommendationEntity(
                    restaurantsJson = gson.toJson(restaurants),
                    latitude = location.latitude,
                    longitude = location.longitude,
                ),
            )

            _uiState.emit(MapUiState.Loaded(restaurants, location.latitude, location.longitude))
            Log.d(TAG, "취향 추천 로드 완료 — ${restaurants.size}개")
        } catch (e: Exception) {
            Log.e(TAG, "취향 추천 로드 실패, 캐시 사용", e)
            _snackbarEvents.tryEmit(SnackbarEvent("추천 로드 실패. 캐시 사용 중", "재시도") {
                // 재시도는 ViewModel에서 처리
            })
            loadFromCache()
        }
    }

    suspend fun loadTrendingRestaurants(context: Context) {
        _trendingUiState.emit(MapUiState.Loading)
        try {
            val location = getLocation(context)
            if (location == null) {
                _trendingUiState.emit(MapUiState.Error("위치를 가져올 수 없습니다.\nGPS를 켜고 다시 시도해주세요."))
                return
            }
            Log.d(TAG, "트렌딩 위치 획득: lat=${location.latitude}, lon=${location.longitude}")

            val result = recommendationApiService.getTrendingRecommendations(
                lat = location.latitude,
                lon = location.longitude,
                limit = TRENDING_LIMIT,
            )
            val restaurants = result.data ?: emptyList()

            _trendingUiState.emit(
                MapUiState.Loaded(restaurants, location.latitude, location.longitude),
            )
            Log.d(TAG, "핫한 맛집 로드 완료 — ${restaurants.size}개")
        } catch (e: Exception) {
            Log.e(TAG, "핫한 맛집 로드 실패", e)
            _trendingUiState.emit(MapUiState.Error("핫한 맛집을 불러오지 못했습니다.\n다시 시도해주세요."))
        }
    }

    suspend fun loadFromCache() {
        val entity = recommendationDatabase.cacheDao().getCacheEntity()
        if (entity != null) {
            val type = object : TypeToken<List<RecommendedRestaurantDto>>() {}.type
            val restaurants: List<RecommendedRestaurantDto> =
                gson.fromJson(entity.restaurantsJson, type)
            Log.d(TAG, "캐시에서 취향 추천 로드 — ${restaurants.size}개")
            _uiState.emit(MapUiState.Loaded(restaurants, entity.latitude, entity.longitude))
        } else {
            _uiState.emit(
                MapUiState.Error("현재 위치를 가져올 수 없습니다.\nGPS를 켜고 다시 시도해주세요."),
            )
        }
    }
}
