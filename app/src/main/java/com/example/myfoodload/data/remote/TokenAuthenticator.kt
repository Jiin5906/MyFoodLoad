package com.example.myfoodload.data.remote

import com.example.myfoodload.data.local.TokenManager
import com.example.myfoodload.shared.dto.RefreshTokenRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * OkHttp Authenticator — 401 응답 수신 시 Refresh Token으로 Access Token 갱신 후 재시도.
 *
 * 무한루프 방지:
 *   - 요청에 Authorization 헤더가 없으면 재시도하지 않음 (로그인 엔드포인트 등)
 *   - /api/auth/refresh 요청 자체가 401이면 재시도하지 않음 → null 반환 → 앱 레벨에서 로그아웃 처리
 */
class TokenAuthenticator(
    private val tokenManager: TokenManager,
    private val authApiService: AuthApiService,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        // Authorization 헤더 없는 요청(public 엔드포인트)은 갱신 시도 불필요
        if (response.request.header("Authorization") == null) return null

        // Refresh 엔드포인트 자체가 실패하면 무한 루프 방지
        if (response.request.url.encodedPath.contains("/api/auth/refresh")) return null

        val refreshToken = runBlocking { tokenManager.getRefreshToken().first() } ?: return null

        return runBlocking {
            runCatching {
                val tokenResponse = authApiService.refresh(RefreshTokenRequest(refreshToken))
                tokenManager.saveTokens(tokenResponse.accessToken, tokenResponse.refreshToken)
                response.request.newBuilder()
                    .header("Authorization", "Bearer ${tokenResponse.accessToken}")
                    .build()
            }.getOrNull()
        }
    }
}
