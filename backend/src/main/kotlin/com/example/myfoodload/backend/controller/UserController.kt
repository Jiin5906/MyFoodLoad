package com.example.myfoodload.backend.controller

import com.example.myfoodload.backend.service.AuthService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
class UserController(
    private val authService: AuthService,
) {
    @DeleteMapping("/me")
    suspend fun deleteAccount(
        @AuthenticationPrincipal userId: String,
    ): ResponseEntity<Void> {
        authService.deleteAccount(userId.toLong())
        return ResponseEntity.noContent().build()
    }
}
