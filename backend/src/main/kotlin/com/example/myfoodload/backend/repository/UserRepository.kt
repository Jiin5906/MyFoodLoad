package com.example.myfoodload.backend.repository

import com.example.myfoodload.backend.model.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByGoogleId(googleId: String): User?
}
