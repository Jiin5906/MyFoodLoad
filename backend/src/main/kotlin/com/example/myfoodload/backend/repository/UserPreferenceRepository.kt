package com.example.myfoodload.backend.repository

import com.example.myfoodload.backend.model.entity.UserPreference
import org.springframework.data.jpa.repository.JpaRepository

interface UserPreferenceRepository : JpaRepository<UserPreference, Long> {
    fun findByUserId(userId: Long): UserPreference?
}
