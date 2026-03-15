package com.example.myfoodload

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.myfoodload.di.AppContainer
import com.google.android.gms.ads.MobileAds
import com.kakao.vectormap.KakaoMapSdk

/**
 * Application 진입점.
 *
 * Hilt Gradle Plugin이 AGP 9.x를 지원하면:
 *   @HiltAndroidApp 어노테이션 추가 + build.gradle.kts의 Hilt/KSP 플러그인 활성화
 *   AppContainer 제거 → @Module @InstallIn(SingletonComponent::class) 방식으로 전환
 */
class MyFoodLoadApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Kakao Maps SDK 초기화 — 네이티브 앱 키 사용
        // x86_64 에뮬레이터에서는 ARM64 네이티브 라이브러리 로드 실패 → try-catch로 보호
        try {
            KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        } catch (e: UnsatisfiedLinkError) {
            // x86/x86_64 에뮬레이터 환경: 지도 기능 비활성화, 나머지 기능은 정상 동작
        }
        // AdMob SDK 초기화
        MobileAds.initialize(this) {}
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024) // 50MB
                    .build()
            }
            .build()
}
