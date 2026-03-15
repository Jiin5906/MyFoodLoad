package com.example.myfoodload.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myfoodload.data.remote.RestaurantApiService
import com.example.myfoodload.shared.dto.CategoryType
import com.example.myfoodload.shared.dto.RestaurantDto
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()
    data class Results(val items: List<RestaurantDto>) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val restaurantApiService: RestaurantApiService,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "SearchViewModel"
        private const val DEBOUNCE_MS = 400L
    }

    init {
        // 300ms debounce: 사용자가 입력을 멈추면 검색 실행
        viewModelScope.launch {
            _query
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { q ->
                    if (q.isBlank()) {
                        _uiState.emit(SearchUiState.Idle)
                    } else {
                        search(q)
                    }
                }
        }
    }

    fun onQueryChange(text: String) {
        _query.value = text
    }

    fun clearQuery() {
        _query.value = ""
        _uiState.value = SearchUiState.Idle
    }

    private suspend fun search(keyword: String) {
        _uiState.emit(SearchUiState.Loading)
        runCatching {
            restaurantApiService.searchRestaurants(keyword = keyword).data ?: emptyList()
        }.onSuccess { results ->
            _uiState.emit(SearchUiState.Results(results))
            Log.d(TAG, "검색 '$keyword' → ${results.size}건")
        }.onFailure { e ->
            Log.e(TAG, "검색 실패 '$keyword'", e)
            _uiState.emit(SearchUiState.Error(e.message ?: "검색 오류"))
        }
    }

    fun filterByCategory(category: CategoryType?) {
        viewModelScope.launch {
            _uiState.emit(SearchUiState.Loading)
            runCatching {
                restaurantApiService.searchRestaurants(
                    keyword = _query.value.takeIf { it.isNotBlank() },
                    category = category?.name,
                ).data ?: emptyList()
            }.onSuccess { results ->
                _uiState.emit(SearchUiState.Results(results))
            }.onFailure { e ->
                _uiState.emit(SearchUiState.Error(e.message ?: "검색 오류"))
            }
        }
    }

    class Factory(
        private val restaurantApiService: RestaurantApiService,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SearchViewModel(restaurantApiService) as T
    }
}
