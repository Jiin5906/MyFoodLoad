package com.example.myfoodload.ui.auth

import android.app.PendingIntent
import android.content.Context
import android.content.IntentSender
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myfoodload.BuildConfig
import com.example.myfoodload.data.local.TokenManager
import com.example.myfoodload.data.remote.AuthApiService
import com.example.myfoodload.data.remote.GeocodingApiService
import com.example.myfoodload.data.remote.LlmAnalysisApiService
import com.example.myfoodload.data.remote.YouTubeIngestionApiService
import com.example.myfoodload.shared.dto.GoogleLoginRequest
import com.example.myfoodload.shared.dto.YoutubeIngestRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    object Success : AuthUiState()

    /**
     * YouTube 권한 동의 화면이 필요한 상태.
     * AuthScreen이 이 상태를 받으면 intentSender로 Activity Result를 실행한다.
     */
    data class AwaitingYoutubeConsent(val intentSender: IntentSender) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

/**
 * Google OAuth 로그인 + YouTube 권한 획득을 담당하는 ViewModel.
 *
 * Hilt 활성화 시: @HiltViewModel + @Inject constructor 로 전환하고 Factory 제거.
 */
class AuthViewModel(
    private val authApiService: AuthApiService,
    private val youTubeIngestionApiService: YouTubeIngestionApiService,
    private val llmAnalysisApiService: LlmAnalysisApiService,
    private val geocodingApiService: GeocodingApiService,
    private val tokenManager: TokenManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    companion object {
        private const val YOUTUBE_READONLY_SCOPE = "https://www.googleapis.com/auth/youtube.readonly"
        private const val TAG = "AuthViewModel"
    }

    /**
     * 1단계: Google ID 토큰으로 백엔드 JWT 발급 → 2단계: YouTube 권한 요청.
     */
    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _uiState.emit(AuthUiState.Loading)
            try {
                val idToken = getGoogleIdToken(context)
                val authResponse = authApiService.googleLogin(GoogleLoginRequest(idToken))
                tokenManager.saveTokens(authResponse.accessToken, authResponse.refreshToken)
                Log.d(TAG, "JWT 발급 완료 — userId=${authResponse.user.id}")

                requestYouTubeAuthorization(context)
            } catch (e: GetCredentialException) {
                _uiState.emit(AuthUiState.Error(e.message ?: "Google 로그인을 취소하였습니다"))
            } catch (e: Exception) {
                _uiState.emit(AuthUiState.Error(e.message ?: "로그인 중 오류가 발생했습니다"))
            }
        }
    }

    /**
     * Identity.getAuthorizationClient로 youtube.readonly 스코프 요청.
     * - 이미 동의됨 → access token 저장 후 수집 트리거
     * - 동의 필요 → AwaitingYoutubeConsent 상태로 UI에 IntentSender 전달
     */
    private suspend fun requestYouTubeAuthorization(context: Context) {
        val authorizationRequest = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(YOUTUBE_READONLY_SCOPE)))
            .build()

        val result = Identity.getAuthorizationClient(context)
            .authorize(authorizationRequest)
            .await()

        if (result.hasResolution()) {
            val pendingIntent: PendingIntent = result.pendingIntent
                ?: run {
                    Log.w(TAG, "YouTube 권한 PendingIntent 없음 — 로그인만 완료")
                    _uiState.emit(AuthUiState.Success)
                    return
                }
            _uiState.emit(AuthUiState.AwaitingYoutubeConsent(pendingIntent.intentSender))
        } else {
            val accessToken = result.accessToken
            if (accessToken != null) {
                tokenManager.saveYoutubeToken(accessToken)
                Log.d(TAG, "YouTube access token 저장 완료")
                triggerIngestion()
            } else {
                Log.w(TAG, "YouTube access token null — 로그인만 완료")
            }
            _uiState.emit(AuthUiState.Success)
        }
    }

    /**
     * YouTube 동의 화면 결과 처리.
     * AuthScreen에서 ActivityResult를 받은 후 호출.
     */
    fun handleYoutubeConsentResult(context: Context, intentData: android.content.Intent?) {
        viewModelScope.launch {
            try {
                val authResult = Identity.getAuthorizationClient(context)
                    .getAuthorizationResultFromIntent(intentData)
                val accessToken = authResult.accessToken
                if (accessToken != null) {
                    tokenManager.saveYoutubeToken(accessToken)
                    Log.d(TAG, "YouTube 동의 완료 — access token 저장")
                    triggerIngestion()
                } else {
                    Log.w(TAG, "YouTube 동의 결과에 access token 없음")
                }
            } catch (e: Exception) {
                Log.w(TAG, "YouTube 동의 처리 실패 (비치명): ${e.message}")
            } finally {
                _uiState.emit(AuthUiState.Success)
            }
        }
    }

    /**
     * 저장된 YouTube access token으로 수집 → LLM 분석 → 맛집 추출 파이프라인 순차 실행.
     * 실패해도 로그인 플로우에 영향 없음.
     */
    private suspend fun triggerIngestion() {
        val youtubeToken = tokenManager.getYoutubeToken().first() ?: return
        try {
            val ingestResult = youTubeIngestionApiService.ingestLikedVideos(
                YoutubeIngestRequest(youtubeAccessToken = youtubeToken),
            )
            Log.d(TAG, "수집 완료 — ${ingestResult.data}")

            // 수집 완료 후 LLM 분석 자동 트리거
            val analyzeResult = llmAnalysisApiService.analyze()
            Log.d(TAG, "LLM 분석 완료 — foodTags=${analyzeResult.data?.foodTags?.size}")

            // LLM 분석 완료 후 맛집 추출 트리거 (Fire-and-Forget — 백그라운드 처리)
            val extractResponse = geocodingApiService.extractRestaurants()
            Log.d(TAG, "맛집 추출 요청 — HTTP ${extractResponse.code()}")
        } catch (e: Exception) {
            Log.w(TAG, "수집/분석/추출 실패 (비치명): ${e.message}")
        }
    }

    private suspend fun getGoogleIdToken(context: Context): String {
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(context, request)
        val credential = result.credential

        check(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
        ) { "지원하지 않는 인증 방식입니다" }

        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }

    class Factory(
        private val authApiService: AuthApiService,
        private val youTubeIngestionApiService: YouTubeIngestionApiService,
        private val llmAnalysisApiService: LlmAnalysisApiService,
        private val geocodingApiService: GeocodingApiService,
        private val tokenManager: TokenManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AuthViewModel(
                authApiService,
                youTubeIngestionApiService,
                llmAnalysisApiService,
                geocodingApiService,
                tokenManager,
            ) as T
    }
}
