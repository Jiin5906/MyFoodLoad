package com.example.myfoodload.backend.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    @Value("\${jwt.secret}") secret: String,
    @Value("\${jwt.access-token-expiration}") private val accessTokenExpiration: Long,
    @Value("\${jwt.refresh-token-expiration}") private val refreshTokenExpiration: Long,
) {
    private val log = LoggerFactory.getLogger(JwtTokenProvider::class.java)
    private val secretKey: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8))

    fun generateAccessToken(userId: Long): String =
        Jwts
            .builder()
            .subject(userId.toString())
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + accessTokenExpiration))
            .signWith(secretKey)
            .compact()

    fun generateRefreshToken(userId: Long): String =
        Jwts
            .builder()
            .subject(userId.toString())
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + refreshTokenExpiration))
            .signWith(secretKey)
            .compact()

    fun validateToken(token: String): Boolean =
        runCatching {
            Jwts
                .parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
            true
        }.onFailure { e ->
            log.warn("JWT 검증 실패: ${e::class.simpleName} — ${e.message}")
        }.getOrDefault(false)

    fun getUserIdFromToken(token: String): Long {
        val claims =
            Jwts
                .parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .payload
        return claims.subject.toLong()
    }
}
