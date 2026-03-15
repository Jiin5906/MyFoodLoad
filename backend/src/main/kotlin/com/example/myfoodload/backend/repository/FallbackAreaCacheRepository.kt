package com.example.myfoodload.backend.repository

import com.example.myfoodload.backend.model.entity.FallbackAreaCache
import com.example.myfoodload.backend.model.entity.FallbackAreaCacheId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface FallbackAreaCacheRepository : JpaRepository<FallbackAreaCache, FallbackAreaCacheId>
