package com.example.myfoodload.ui.favorite

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myfoodload.data.remote.FavoritesApiService
import com.example.myfoodload.shared.dto.RestaurantDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class FavoriteUiState {
    object Loading : FavoriteUiState()
    data class Loaded(val favorites: List<RestaurantDto>) : FavoriteUiState()
    data class Error(val message: String) : FavoriteUiState()
}

class FavoriteViewModel(
    private val favoritesApiService: FavoritesApiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow<FavoriteUiState>(FavoriteUiState.Loading)
    val uiState: StateFlow<FavoriteUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "FavoriteViewModel"
    }

    init {
        loadFavorites()
    }

    fun retry() = loadFavorites()

    private fun loadFavorites() {
        viewModelScope.launch {
            _uiState.emit(FavoriteUiState.Loading)
            runCatching {
                // JWT는 AuthInterceptor가 자동으로 헤더에 추가 — userId 파라미터 불필요 (Gemini 보안 지적 반영)
                favoritesApiService.getMyFavorites().data ?: emptyList()
            }.onSuccess { list ->
                _uiState.emit(FavoriteUiState.Loaded(list))
                Log.d(TAG, "즐겨찾기 로드 완료 — ${list.size}개")
            }.onFailure { e ->
                Log.e(TAG, "즐겨찾기 로드 실패", e)
                _uiState.emit(FavoriteUiState.Error(e.message ?: "알 수 없는 오류"))
            }
        }
    }

    fun removeFavorite(restaurantId: Long) {
        val current = _uiState.value as? FavoriteUiState.Loaded ?: return
        // Optimistic update: 즉시 목록에서 제거
        _uiState.value = current.copy(favorites = current.favorites.filter { it.id != restaurantId })

        viewModelScope.launch {
            runCatching {
                favoritesApiService.removeFavorite(restaurantId)
            }.onFailure { e ->
                Log.e(TAG, "즐겨찾기 제거 실패, 목록 복원", e)
                _uiState.value = current
            }
        }
    }

    class Factory(
        private val favoritesApiService: FavoritesApiService,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FavoriteViewModel(favoritesApiService) as T
    }
}
