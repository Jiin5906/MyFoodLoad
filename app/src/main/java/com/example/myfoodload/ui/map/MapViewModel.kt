package com.example.myfoodload.ui.map

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myfoodload.data.local.TokenManager
import com.example.myfoodload.data.local.db.RecommendationDatabase
import com.example.myfoodload.data.remote.GeocodingApiService
import com.example.myfoodload.data.remote.LlmAnalysisApiService
import com.example.myfoodload.data.remote.RecommendationApiService
import com.example.myfoodload.data.remote.YouTubeIngestionApiService
import com.example.myfoodload.shared.dto.CategoryType
import com.example.myfoodload.shared.dto.RecommendedRestaurantDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 지도 화면 탭 — "내 취향 맛집" vs "핫한 맛집" */
enum class MapTab { PERSONALIZED, TRENDING }

sealed class MapUiState {
    object Idle : MapUiState()
    object Loading : MapUiState()
    data class Loaded(
        val restaurants: List<RecommendedRestaurantDto>,
        val userLatitude: Double,
        val userLongitude: Double,
    ) : MapUiState()
    data class Error(val message: String) : MapUiState()
}

/** YouTube 분석 파이프라인(수집→LLM→지오코딩) 진행 상태 */
sealed class SyncUiState {
    object Idle : SyncUiState()
    data class AwaitingYoutubeConsent(val intentSender: IntentSender) : SyncUiState()
    data class Syncing(val step: String) : SyncUiState()
    data class Complete(val restaurantsAdded: Int, val warningMessage: String? = null) : SyncUiState()
    data class PartialFailure(val processed: Int, val total: Int) : SyncUiState()
    object NoResults : SyncUiState()
    data class Error(val message: String) : SyncUiState()
}

/** 카메라 이동 이벤트 (one-shot) */
sealed class CameraEvent {
    data class MoveToUser(val lat: Double, val lng: Double) : CameraEvent()
    data class ZoomToUser(val lat: Double, val lng: Double) : CameraEvent()
}

class MapViewModel(
    recommendationApiService: RecommendationApiService,
    recommendationDatabase: RecommendationDatabase,
    youTubeIngestionApiService: YouTubeIngestionApiService,
    llmAnalysisApiService: LlmAnalysisApiService,
    geocodingApiService: GeocodingApiService,
    tokenManager: TokenManager,
) : ViewModel() {

    private val recommendationManager = RecommendationManager(
        recommendationApiService, recommendationDatabase,
    )

    private val youtubeSyncManager = YouTubeSyncManager(
        youTubeIngestionApiService, llmAnalysisApiService, geocodingApiService, tokenManager,
        onPipelineComplete = {},
    )

    private var lastContext: Context? = null

    // ── 상태 (Manager에서 위임) ───────────────────────────────────────────────────

    val uiState: StateFlow<MapUiState> = recommendationManager.uiState
    val trendingUiState: StateFlow<MapUiState> = recommendationManager.trendingUiState
    val syncUiState: StateFlow<SyncUiState> = youtubeSyncManager.syncUiState
    val snackbarEvents: SharedFlow<SnackbarEvent> = recommendationManager.snackbarEvents

    // ── 로컬 상태 ──────────────────────────────────────────────────────────────────

    private val _currentTab = MutableStateFlow(MapTab.PERSONALIZED)
    val currentTab: StateFlow<MapTab> = _currentTab.asStateFlow()

    private val _selectedRestaurant = MutableStateFlow<RecommendedRestaurantDto?>(null)
    val selectedRestaurant: StateFlow<RecommendedRestaurantDto?> = _selectedRestaurant.asStateFlow()

    private val _selectedCategory = MutableStateFlow<CategoryType?>(null)
    val selectedCategory: StateFlow<CategoryType?> = _selectedCategory.asStateFlow()

    private val _excludeVisited = MutableStateFlow(false)
    val excludeVisited: StateFlow<Boolean> = _excludeVisited.asStateFlow()

    /** 지도 터치/드래그 중 여부 → 바텀시트 최소화 트리거 */
    private val _isMapInteracting = MutableStateFlow(false)
    val isMapInteracting: StateFlow<Boolean> = _isMapInteracting.asStateFlow()

    // ── 카메라 이벤트 (MapView에서 소비) ──────────────────────────────────────────

    private val _cameraEvent = MutableStateFlow<CameraEvent?>(null)
    val cameraEvent: StateFlow<CameraEvent?> = _cameraEvent.asStateFlow()

    // ── 파생 StateFlow ─────────────────────────────────────────────────────────

    val filteredRestaurants: StateFlow<List<RecommendedRestaurantDto>> =
        combine(_currentTab, uiState, trendingUiState, _selectedCategory) { tab, state, trending, cat ->
            val all = when (tab) {
                MapTab.PERSONALIZED -> (state as? MapUiState.Loaded)?.restaurants ?: emptyList()
                MapTab.TRENDING -> (trending as? MapUiState.Loaded)?.restaurants ?: emptyList()
            }
            if (cat == null) all else all.filter { it.restaurant.category == cat }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sortedRestaurants: StateFlow<List<RecommendedRestaurantDto>> =
        filteredRestaurants
            .combine(_selectedRestaurant) { filtered, selected ->
                buildList {
                    selected?.let { if (it in filtered) add(it) }
                    addAll(filtered.filter { it != selected })
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        private const val TAG = "MapViewModel"
    }

    // ── Public Actions ──────────────────────────────────────────────────────────

    fun switchTab(tab: MapTab) {
        _currentTab.value = tab
        _selectedRestaurant.value = null
    }

    fun toggleExcludeVisited(context: Context) {
        _excludeVisited.value = !_excludeVisited.value
        loadNearbyRestaurants(context)
    }

    fun loadNearbyRestaurants(context: Context) {
        if (uiState.value is MapUiState.Loading) return
        lastContext = context
        viewModelScope.launch {
            recommendationManager.loadNearbyRestaurants(context, _excludeVisited.value)
        }
    }

    fun loadTrendingRestaurants(context: Context) {
        if (trendingUiState.value is MapUiState.Loading) return
        viewModelScope.launch {
            recommendationManager.loadTrendingRestaurants(context)
        }
    }

    fun selectRestaurant(recommendation: RecommendedRestaurantDto?) {
        _selectedRestaurant.value = recommendation
        if (recommendation != null) _isMapInteracting.value = false
    }

    fun setMapInteracting(interacting: Boolean) {
        _isMapInteracting.value = interacting
    }

    fun selectCategory(category: CategoryType?) {
        _selectedCategory.value = if (_selectedCategory.value == category) null else category
    }

    fun requestYouTubeSync(context: Context) {
        lastContext = context
        viewModelScope.launch {
            youtubeSyncManager.requestYouTubeSync(context)
        }
    }

    fun handleYouTubeConsentResult(context: Context, intentData: Intent?) {
        lastContext = context
        viewModelScope.launch {
            youtubeSyncManager.handleYouTubeConsentResult(context, intentData)
        }
    }

    fun resetSyncState() {
        youtubeSyncManager.resetSyncState()
    }

    // ── 카메라 이동 ──────────────────────────────────────────────────────────────

    /** FAB: 현재 위치로 부드럽게 카메라 이동 */
    fun moveCameraToUserLocation() {
        val loaded = getActiveLoaded() ?: return
        _cameraEvent.value = CameraEvent.MoveToUser(loaded.userLatitude, loaded.userLongitude)
    }

    fun consumeCameraEvent() {
        _cameraEvent.value = null
    }

    private fun getActiveLoaded(): MapUiState.Loaded? = when (_currentTab.value) {
        MapTab.PERSONALIZED -> uiState.value as? MapUiState.Loaded
        MapTab.TRENDING -> trendingUiState.value as? MapUiState.Loaded
    }

    init {
        viewModelScope.launch {
            syncUiState.collect { state ->
                if (state is SyncUiState.Complete) {
                    lastContext?.let { loadNearbyRestaurants(it) }
                }
            }
        }
    }

    class Factory(
        private val recommendationApiService: RecommendationApiService,
        private val recommendationDatabase: RecommendationDatabase,
        private val youTubeIngestionApiService: YouTubeIngestionApiService,
        private val llmAnalysisApiService: LlmAnalysisApiService,
        private val geocodingApiService: GeocodingApiService,
        private val tokenManager: TokenManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MapViewModel(
                recommendationApiService,
                recommendationDatabase,
                youTubeIngestionApiService,
                llmAnalysisApiService,
                geocodingApiService,
                tokenManager,
            ) as T
    }
}
