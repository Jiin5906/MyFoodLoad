package com.example.myfoodload.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myfoodload.data.local.TokenManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed class SettingsEvent {
    object LoggedOut : SettingsEvent()
}

class SettingsViewModel(
    private val tokenManager: TokenManager,
) : ViewModel() {

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun logout() {
        viewModelScope.launch {
            tokenManager.clearTokens()
            _events.send(SettingsEvent.LoggedOut)
        }
    }

    class Factory(
        private val tokenManager: TokenManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(tokenManager) as T
    }
}
