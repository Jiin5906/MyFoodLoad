package com.example.myfoodload.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Phase 10: 추천 결과 오프라인 캐시 Room 데이터베이스.
 *
 * 단일 테이블(recommendation_cache), version=1.
 * getInstance()로 Application 수명과 동일한 싱글톤 확보.
 */
@Database(
    entities = [CachedRecommendationEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class RecommendationDatabase : RoomDatabase() {

    abstract fun cacheDao(): RecommendationCacheDao

    companion object {
        @Volatile
        private var INSTANCE: RecommendationDatabase? = null

        fun getInstance(context: Context): RecommendationDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RecommendationDatabase::class.java,
                    "recommendation_cache.db",
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
