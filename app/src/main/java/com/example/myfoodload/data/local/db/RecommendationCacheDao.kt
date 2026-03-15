package com.example.myfoodload.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Phase 10: 추천 캐시 DAO.
 *
 * interface 사용 — KSP abstract class suspend fun Unit 버그 우회.
 * JSON 직렬화/역직렬화는 MapViewModel에서 담당.
 */
@Dao
interface RecommendationCacheDao {

    @Query("SELECT * FROM recommendation_cache WHERE id = 1 LIMIT 1")
    suspend fun getCacheEntity(): CachedRecommendationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(entity: CachedRecommendationEntity): Long
}
