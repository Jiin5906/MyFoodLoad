package com.example.myfoodload.backend.service

import com.example.myfoodload.backend.model.entity.User
import com.example.myfoodload.backend.repository.UserRepository
import com.example.myfoodload.backend.security.JwtTokenProvider
import com.example.myfoodload.shared.dto.AuthResponse
import com.example.myfoodload.shared.dto.GoogleLoginRequest
import com.example.myfoodload.shared.dto.RefreshTokenResponse
import com.example.myfoodload.shared.dto.UserInfo
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    @Value("\${google.client-id}") googleClientId: String,
) {
    private val verifier: GoogleIdTokenVerifier =
        GoogleIdTokenVerifier
            .Builder(NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(listOf(googleClientId))
            .build()

    fun googleLogin(request: GoogleLoginRequest): AuthResponse {
        val idToken =
            verifier.verify(request.idToken)
                ?: throw IllegalArgumentException("유효하지 않은 Google ID Token입니다")

        val payload = idToken.payload
        val googleId = payload.subject
        val email = payload["email"] as String
        val name = (payload["name"] as? String) ?: email
        val picture = payload["picture"] as? String

        val user =
            userRepository
                .findByGoogleId(googleId)
                ?.also { existing ->
                    if (existing.name != name || existing.profileImageUrl != picture) {
                        existing.name = name
                        existing.profileImageUrl = picture
                        existing.updatedAt = OffsetDateTime.now()
                        userRepository.save(existing)
                    }
                }
                ?: userRepository.save(
                    User(
                        googleId = googleId,
                        email = email,
                        name = name,
                        profileImageUrl = picture,
                    ),
                )

        return AuthResponse(
            accessToken = jwtTokenProvider.generateAccessToken(user.id),
            refreshToken = jwtTokenProvider.generateRefreshToken(user.id),
            user =
                UserInfo(
                    id = user.id,
                    email = user.email,
                    name = user.name,
                    profileImageUrl = user.profileImageUrl,
                ),
        )
    }

    fun refreshToken(refreshToken: String): RefreshTokenResponse {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw IllegalArgumentException("만료되었거나 유효하지 않은 Refresh Token입니다")
        }
        val userId = jwtTokenProvider.getUserIdFromToken(refreshToken)
        return RefreshTokenResponse(
            accessToken = jwtTokenProvider.generateAccessToken(userId),
            refreshToken = jwtTokenProvider.generateRefreshToken(userId),
        )
    }
}
