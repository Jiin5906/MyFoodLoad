package com.example.myfoodload.data.remote

import com.example.myfoodload.shared.dto.AuthResponse
import com.example.myfoodload.shared.dto.GoogleLoginRequest
import com.example.myfoodload.shared.dto.RefreshTokenRequest
import com.example.myfoodload.shared.dto.RefreshTokenResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {
    @POST("api/auth/google")
    suspend fun googleLogin(@Body request: GoogleLoginRequest): AuthResponse

    @POST("api/auth/refresh")
    suspend fun refresh(@Body request: RefreshTokenRequest): RefreshTokenResponse
}
