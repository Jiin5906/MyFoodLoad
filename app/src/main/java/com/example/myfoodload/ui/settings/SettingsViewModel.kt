package com.example.myfoodload.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myfoodload.data.local.TokenManager
import com.example.myfoodload.data.remote.UserApiService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed class SettingsEvent {
    object LoggedOut : SettingsEvent()
    object AccountDeleted : SettingsEvent()
    data class Error(val message: String) : SettingsEvent()
}

class SettingsViewModel(
    private val tokenManager: TokenManager,
    private val userApiService: UserApiService,
) : ViewModel() {

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog = _showDeleteDialog.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting = _isDeleting.asStateFlow()

    fun showDeleteConfirmation() {
        _showDeleteDialog.value = true
    }

    fun dismissDeleteDialog() {
        _showDeleteDialog.value = false
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _isDeleting.value = true
            _showDeleteDialog.value = false
            try {
                userApiService.deleteAccount()
                tokenManager.clearTokens()
                _events.send(SettingsEvent.AccountDeleted)
            } catch (e: Exception) {
                _events.send(SettingsEvent.Error("계정 삭제에 실패했습니다. 다시 시도해주세요."))
            } finally {
                _isDeleting.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            tokenManager.clearTokens()
            _events.send(SettingsEvent.LoggedOut)
        }
    }

    class Factory(
        private val tokenManager: TokenManager,
        private val userApiService: UserApiService,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(tokenManager, userApiService) as T
    }
}
