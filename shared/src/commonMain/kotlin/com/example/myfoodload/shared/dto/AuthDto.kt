package com.example.myfoodload.shared.dto

data class GoogleLoginRequest(val idToken: String)

data class RefreshTokenRequest(val refreshToken: String)

data class RefreshTokenResponse(
    val accessToken: String,
    val refreshToken: String,
)

data class UserInfo(
    val id: Long,
    val email: String,
    val name: String,
    val profileImageUrl: String?,
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserInfo,
)
