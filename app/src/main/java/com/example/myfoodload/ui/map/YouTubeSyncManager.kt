package com.example.myfoodload.ui.map

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.myfoodload.data.local.TokenManager
import com.example.myfoodload.data.remote.GeocodingApiService
import com.example.myfoodload.data.remote.LlmAnalysisApiService
import com.example.myfoodload.data.remote.YouTubeIngestionApiService
import com.example.myfoodload.shared.dto.YoutubeIngestRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import retrofit2.HttpException

/**
 * YouTube 분석 파이프라인(수집→LLM→지오코딩) 관리.
 *
 * 파이프라인 완료 시 [onPipelineComplete] 콜백으로 추천 재로딩 요청.
 */
class YouTubeSyncManager(
    private val youTubeIngestionApiService: YouTubeIngestionApiService,
    private val llmAnalysisApiService: LlmAnalysisApiService,
    private val geocodingApiService: GeocodingApiService,
    private val tokenManager: TokenManager,
    private val onPipelineComplete: () -> Unit,
) {
    private val _syncUiState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val syncUiState: StateFlow<SyncUiState> = _syncUiState.asStateFlow()

    companion object {
        private const val TAG = "YouTubeSyncManager"
        private const val YOUTUBE_READONLY_SCOPE = "https://www.googleapis.com/auth/youtube.readonly"
        private const val POLL_INTERVAL_MS = 2000L
        private const val MAX_POLL_ATTEMPTS = 90 // 최대 3분 (2초 × 90)
    }

    suspend fun requestYouTubeSync(context: Context) {
        _syncUiState.emit(SyncUiState.Syncing("YouTube 연동 확인 중..."))
        try {
            requestYouTubeAuthorization(context)
        } catch (e: Exception) {
            Log.e(TAG, "YouTube 동기화 실패", e)
            _syncUiState.emit(SyncUiState.Error(e.message ?: "오류가 발생했습니다"))
        }
    }

    private suspend fun requestYouTubeAuthorization(context: Context) {
        val authReq = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(YOUTUBE_READONLY_SCOPE)))
            .build()
        val result = Identity.getAuthorizationClient(context).authorize(authReq).await()
        if (result.hasResolution()) {
            val pending = result.pendingIntent
            if (pending == null) {
                _syncUiState.emit(SyncUiState.Error("YouTube 권한을 요청할 수 없습니다"))
                return
            }
            _syncUiState.emit(SyncUiState.AwaitingYoutubeConsent(pending.intentSender))
        } else {
            val token = result.accessToken
            if (token != null) {
                tokenManager.saveYoutubeToken(token)
                runYouTubePipeline(context, token)
            } else {
                _syncUiState.emit(SyncUiState.Error("YouTube 토큰을 가져올 수 없습니다"))
            }
        }
    }

    suspend fun handleYouTubeConsentResult(context: Context, intentData: Intent?) {
        try {
            val authResult = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(intentData)
            val token = authResult.accessToken
            if (token != null) {
                tokenManager.saveYoutubeToken(token)
                runYouTubePipeline(context, token)
            } else {
                _syncUiState.emit(SyncUiState.Error("YouTube 토큰을 가져올 수 없습니다"))
            }
        } catch (e: Exception) {
            _syncUiState.emit(SyncUiState.Error("YouTube 연동 실패: ${e.message}"))
        }
    }

    private suspend fun runYouTubePipeline(context: Context, youtubeToken: String) {
        // Step 1: YouTube 좋아요 수집
        _syncUiState.emit(SyncUiState.Syncing("유튜브 좋아요 수집 중..."))
        try {
            Log.d(TAG, "[Pipeline] Step 1 시작: YouTube 좋아요 수집")
            val result = youTubeIngestionApiService.ingestLikedVideos(
                YoutubeIngestRequest(youtubeAccessToken = youtubeToken),
            )
            Log.d(TAG, "[Pipeline] Step 1 완료: ${result.data}")
        } catch (e: HttpException) {
            if (e.code() == 401) {
                Log.w(TAG, "YouTube 토큰 만료 (HTTP 401) — 재연동 안내")
                _syncUiState.emit(SyncUiState.Error("구글 연동이 만료되었습니다. 다시 시도해 주세요."))
                return
            }
            Log.e(TAG, "[Pipeline] Step 1 실패: 유튜브 수집 HTTP ${e.code()}", e)
            _syncUiState.emit(SyncUiState.Error("유튜브 수집 실패 (HTTP ${e.code()})"))
            return
        } catch (e: Exception) {
            Log.e(TAG, "[Pipeline] Step 1 실패: 유튜브 수집", e)
            _syncUiState.emit(SyncUiState.Error("유튜브 수집 실패: ${e.message}"))
            return
        }

        // Step 2: LLM 분석
        _syncUiState.emit(SyncUiState.Syncing("AI로 맛집 분석 중..."))
        try {
            Log.d(TAG, "[Pipeline] Step 2 시작: LLM 분석")
            val analyzeResult = llmAnalysisApiService.analyze()
            Log.d(TAG, "[Pipeline] Step 2 완료: foodTags=${analyzeResult.data?.foodTags?.size}")
        } catch (e: Exception) {
            Log.e(TAG, "[Pipeline] Step 2 실패: AI 분석", e)
            _syncUiState.emit(SyncUiState.Error("AI 분석 실패: ${e.message}"))
            return
        }

        // Step 3: 맛집 추출 (Fire-and-Forget + 폴링)
        _syncUiState.emit(SyncUiState.Syncing("맛집 위치 확인 중..."))
        try {
            Log.d(TAG, "[Pipeline] Step 3 시작: 맛집 추출 요청")
            val response = geocodingApiService.extractRestaurants()
            val httpCode = response.code()
            Log.d(TAG, "[Pipeline] Step 3 추출 요청 응답: HTTP $httpCode")

            if (httpCode == 202) {
                // Fire-and-Forget: 폴링으로 완료 대기
                pollExtractionStatus()
            } else if (httpCode == 409) {
                // 이미 진행 중: 폴링으로 기존 작업 추적
                Log.d(TAG, "[Pipeline] 이미 추출 진행 중 — 폴링 시작")
                pollExtractionStatus()
            } else {
                _syncUiState.emit(SyncUiState.Error("맛집 추출 요청 실패 (HTTP $httpCode)"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "[Pipeline] Step 3 실패: 맛집 위치 확인", e)
            _syncUiState.emit(SyncUiState.Error("맛집 위치 확인 실패: ${e.message}"))
        }
    }

    private suspend fun pollExtractionStatus() {
        for (attempt in 1..MAX_POLL_ATTEMPTS) {
            delay(POLL_INTERVAL_MS)
            try {
                val statusResponse = geocodingApiService.getExtractionStatus()
                val jobStatus = statusResponse.data ?: continue
                Log.d(TAG, "[Pipeline] 폴링 $attempt: status=${jobStatus.status}")

                when (jobStatus.status) {
                    "COMPLETED" -> {
                        val result = jobStatus.result
                        val added = result?.restaurantsAdded ?: 0
                        val found = result?.restaurantsFound ?: 0
                        val geminiFailures = result?.geminiBatchFailures ?: 0
                        val processed = result?.videosProcessed ?: 0
                        Log.d(TAG, "[Pipeline] Step 3 완료: found=$found, added=$added, geminiFail=$geminiFailures")

                        val total = processed + (if (geminiFailures > 0) geminiFailures * 5 else 0)
                        if (geminiFailures > 0 && found == 0) {
                            _syncUiState.emit(SyncUiState.PartialFailure(processed = processed, total = total))
                        } else if (found == 0) {
                            _syncUiState.emit(SyncUiState.Complete(0, "맛집 추출은 실패했지만 태그 기반 추천을 시도합니다"))
                        } else {
                            val warning = if (geminiFailures > 0) "일부 영상 분석에 실패했어요 (${geminiFailures}건)" else null
                            _syncUiState.emit(SyncUiState.Complete(added, warning))
                        }
                        onPipelineComplete()
                        return
                    }
                    "FAILED" -> {
                        _syncUiState.emit(SyncUiState.Error(jobStatus.message ?: "맛집 추출 실패"))
                        onPipelineComplete()
                        return
                    }
                    "PROCESSING" -> {
                        // 계속 폴링
                    }
                    else -> {
                        // IDLE 등 예상치 못한 상태
                        Log.w(TAG, "[Pipeline] 예상치 못한 상태: ${jobStatus.status}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[Pipeline] 폴링 실패 (attempt=$attempt)", e)
                // 일시적 네트워크 오류는 무시하고 계속 폴링
            }
        }
        // 최대 폴링 횟수 초과
        _syncUiState.emit(SyncUiState.Error("맛집 추출 시간이 초과되었습니다. 나중에 다시 시도해 주세요."))
        onPipelineComplete()
    }

    fun resetSyncState() {
        _syncUiState.value = SyncUiState.Idle
    }
}
