package com.example.myfoodload.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myfoodload.data.remote.LlmAnalysisApiService
import com.example.myfoodload.shared.dto.UserPreferenceDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ProfileUiState {
    object Loading : ProfileUiState()
    data class Loaded(val preferences: UserPreferenceDto) : ProfileUiState()
    object Empty : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

/**
 * Phase 10: 내 취향 분석 ViewModel.
 *
 * GET /api/llm/preferences 호출 → LLM이 분석한 UserPreferenceDto 로드.
 * 분석 결과 없으면 Empty 상태로 전환 (재분석 버튼 표시).
 */
class ProfileViewModel(
    private val llmAnalysisApiService: LlmAnalysisApiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "ProfileViewModel"
    }

    init {
        loadPreferences()
    }

    fun loadPreferences() {
        viewModelScope.launch {
            _uiState.emit(ProfileUiState.Loading)
            try {
                val result = llmAnalysisApiService.getPreferences()
                val prefs = result.data
                if (prefs == null || prefs.foodTags.isEmpty()) {
                    _uiState.emit(ProfileUiState.Empty)
                } else {
                    _uiState.emit(ProfileUiState.Loaded(prefs))
                    Log.d(TAG, "취향 프로필 로드 완료 — ${prefs.foodTags.size}개 태그")
                }
            } catch (e: Exception) {
                Log.e(TAG, "취향 프로필 로드 실패", e)
                _uiState.emit(ProfileUiState.Error(e.message ?: "알 수 없는 오류"))
            }
        }
    }

    class Factory(
        private val llmAnalysisApiService: LlmAnalysisApiService,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ProfileViewModel(llmAnalysisApiService) as T
    }
}
