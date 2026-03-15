package com.example.myfoodload.backend.controller

import com.example.myfoodload.backend.service.AuthService
import com.example.myfoodload.shared.dto.AuthResponse
import com.example.myfoodload.shared.dto.GoogleLoginRequest
import com.example.myfoodload.shared.dto.RefreshTokenRequest
import com.example.myfoodload.shared.dto.RefreshTokenResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/google")
    fun googleLogin(
        @RequestBody request: GoogleLoginRequest,
    ): ResponseEntity<AuthResponse> = ResponseEntity.ok(authService.googleLogin(request))

    /** Access Token 만료 시 Refresh Token으로 갱신. 인증 불필요(Security permitAll). */
    @PostMapping("/refresh")
    fun refresh(
        @RequestBody request: RefreshTokenRequest,
    ): ResponseEntity<RefreshTokenResponse> = ResponseEntity.ok(authService.refreshToken(request.refreshToken))
}
