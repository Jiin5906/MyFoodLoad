package com.example.myfoodload.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * JWT 및 YouTube 토큰을 DataStore에 영구 저장/조회.
 *
 * Hilt 활성화 시 @Singleton @Inject constructor(private val dataStore: ...) 으로 전환.
 */
class TokenManager(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        val YOUTUBE_TOKEN_KEY = stringPreferencesKey("youtube_token")
        val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
        /** null = 시스템 설정 따름, true = 항상 다크, false = 항상 라이트 */
        val DARK_MODE_KEY = booleanPreferencesKey("dark_mode_override")
    }

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN_KEY] = accessToken
            prefs[REFRESH_TOKEN_KEY] = refreshToken
        }
    }

    fun getAccessToken(): Flow<String?> = dataStore.data.map { it[ACCESS_TOKEN_KEY] }

    fun getRefreshToken(): Flow<String?> = dataStore.data.map { it[REFRESH_TOKEN_KEY] }

    suspend fun saveYoutubeToken(token: String) {
        dataStore.edit { prefs -> prefs[YOUTUBE_TOKEN_KEY] = token }
    }

    fun getYoutubeToken(): Flow<String?> = dataStore.data.map { it[YOUTUBE_TOKEN_KEY] }

    suspend fun clearTokens() {
        dataStore.edit { it.clear() }
    }

    fun isOnboardingCompleted(): Flow<Boolean> =
        dataStore.data.map { it[ONBOARDING_COMPLETED_KEY] ?: false }

    suspend fun markOnboardingCompleted() {
        dataStore.edit { it[ONBOARDING_COMPLETED_KEY] = true }
    }

    /** 다크 모드 설정: null = 시스템, true = 항상 다크, false = 항상 라이트 */
    fun getDarkMode(): Flow<Boolean?> = dataStore.data.map { it[DARK_MODE_KEY] }

    suspend fun setDarkMode(enabled: Boolean?) {
        dataStore.edit { prefs ->
            if (enabled == null) prefs.remove(DARK_MODE_KEY)
            else prefs[DARK_MODE_KEY] = enabled
        }
    }
}
