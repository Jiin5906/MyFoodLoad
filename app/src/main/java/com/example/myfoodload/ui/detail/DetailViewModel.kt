package com.example.myfoodload.ui.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myfoodload.data.remote.FavoritesApiService
import com.example.myfoodload.data.remote.RestaurantApiService
import com.example.myfoodload.data.remote.VisitApiService
import com.example.myfoodload.shared.dto.RestaurantDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class DetailUiState {
    object Loading : DetailUiState()
    data class Loaded(
        val restaurant: RestaurantDto,
        val isFavorite: Boolean = false,
        val isVisited: Boolean = false,
    ) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}

/**
 * Phase 9: 맛집 상세 ViewModel.
 * Phase 12: 즐겨찾기 토글 기능 추가.
 */
class DetailViewModel(
    private val restaurantId: Long,
    private val restaurantApiService: RestaurantApiService,
    private val favoritesApiService: FavoritesApiService,
    private val visitApiService: VisitApiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "DetailViewModel"
    }

    init {
        loadRestaurant()
    }

    fun retry() = loadRestaurant()

    private fun loadRestaurant() {
        viewModelScope.launch {
            _uiState.emit(DetailUiState.Loading)
            try {
                val restaurantResult = restaurantApiService.getRestaurant(restaurantId)
                val restaurant = restaurantResult.data ?: throw Exception("맛집 정보를 찾을 수 없습니다.")

                val favoriteIds = runCatching {
                    favoritesApiService.getMyFavoriteIds().data ?: emptySet()
                }.getOrDefault(emptySet())

                val visitedIds = runCatching {
                    visitApiService.getVisitedIds().data ?: emptySet()
                }.getOrDefault(emptySet())

                _uiState.emit(
                    DetailUiState.Loaded(
                        restaurant,
                        isFavorite = restaurantId in favoriteIds,
                        isVisited = restaurantId in visitedIds,
                    ),
                )
                Log.d(TAG, "맛집 로드 완료 — ${restaurant.name}, 즐겨찾기: ${restaurantId in favoriteIds}, 방문: ${restaurantId in visitedIds}")
            } catch (e: Exception) {
                Log.e(TAG, "맛집 로드 실패", e)
                _uiState.emit(DetailUiState.Error(e.message ?: "알 수 없는 오류"))
            }
        }
    }

    fun toggleFavorite() {
        val current = _uiState.value as? DetailUiState.Loaded ?: return
        // Optimistic update: 즉시 UI 반영 후 서버 요청
        val optimistic = !current.isFavorite
        _uiState.value = current.copy(isFavorite = optimistic)

        viewModelScope.launch {
            runCatching {
                if (optimistic) {
                    favoritesApiService.addFavorite(restaurantId)
                } else {
                    favoritesApiService.removeFavorite(restaurantId)
                }
            }.onFailure { e ->
                // 서버 오류 시 롤백
                Log.e(TAG, "즐겨찾기 토글 실패", e)
                _uiState.value = current.copy(isFavorite = !optimistic)
            }
        }
    }

    fun toggleVisit() {
        val current = _uiState.value as? DetailUiState.Loaded ?: return
        val optimistic = !current.isVisited
        _uiState.value = current.copy(isVisited = optimistic)

        viewModelScope.launch {
            runCatching {
                if (optimistic) {
                    visitApiService.markVisited(restaurantId)
                } else {
                    visitApiService.unmarkVisited(restaurantId)
                }
            }.onFailure { e ->
                Log.e(TAG, "방문 완료 토글 실패", e)
                _uiState.value = current.copy(isVisited = !optimistic)
            }
        }
    }

    class Factory(
        private val restaurantId: Long,
        private val restaurantApiService: RestaurantApiService,
        private val favoritesApiService: FavoritesApiService,
        private val visitApiService: VisitApiService,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DetailViewModel(restaurantId, restaurantApiService, favoritesApiService, visitApiService) as T
    }
}
