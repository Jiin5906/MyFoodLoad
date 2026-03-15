package com.example.myfoodload.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.myfoodload.data.local.TokenManager
import com.example.myfoodload.data.remote.AuthApiService
import com.example.myfoodload.data.remote.AuthInterceptor
import com.example.myfoodload.data.remote.FavoritesApiService
import com.example.myfoodload.data.remote.GeocodingApiService
import com.example.myfoodload.data.remote.LlmAnalysisApiService
import com.example.myfoodload.data.local.db.RecommendationDatabase
import com.example.myfoodload.data.remote.RecommendationApiService
import com.example.myfoodload.data.remote.RestaurantApiService
import com.example.myfoodload.data.remote.TokenAuthenticator
import com.example.myfoodload.data.remote.VisitApiService
import com.example.myfoodload.data.remote.YouTubeIngestionApiService
import com.example.myfoodload.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_tokens")

/**
 * Application 수준 의존성 컨테이너.
 *
 * Hilt Gradle Plugin이 AGP 9.x를 지원하면 @Module/@Singleton 방식으로 전환 예정.
 * 에뮬레이터에서 PC 로컬호스트 접근: 10.0.2.2
 */
class AppContainer(context: Context) {
    val recommendationDatabase: RecommendationDatabase =
        RecommendationDatabase.getInstance(context)

    val tokenManager = TokenManager(context.appDataStore)

    private val authInterceptor = AuthInterceptor(tokenManager)

    private val logLevel: HttpLoggingInterceptor.Level =
        if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
        else HttpLoggingInterceptor.Level.NONE

    // AuthApiService는 Authenticator 내부에서 사용하므로 순환 참조 방지를 위해
    // Authenticator 없는 별도 Retrofit 인스턴스로 생성
    private val authOnlyRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BACKEND_URL)
        .client(
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(HttpLoggingInterceptor().apply { level = logLevel })
                .build(),
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val authApiService: AuthApiService = authOnlyRetrofit.create(AuthApiService::class.java)

    private val tokenAuthenticator = TokenAuthenticator(tokenManager, authApiService)

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .authenticator(tokenAuthenticator)
        .addInterceptor(HttpLoggingInterceptor().apply { level = logLevel })
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BACKEND_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val youTubeIngestionApiService: YouTubeIngestionApiService =
        retrofit.create(YouTubeIngestionApiService::class.java)
    val llmAnalysisApiService: LlmAnalysisApiService =
        retrofit.create(LlmAnalysisApiService::class.java)
    val geocodingApiService: GeocodingApiService =
        retrofit.create(GeocodingApiService::class.java)
    val recommendationApiService: RecommendationApiService =
        retrofit.create(RecommendationApiService::class.java)
    val restaurantApiService: RestaurantApiService =
        retrofit.create(RestaurantApiService::class.java)
    val favoritesApiService: FavoritesApiService =
        retrofit.create(FavoritesApiService::class.java)
    val visitApiService: VisitApiService =
        retrofit.create(VisitApiService::class.java)
}
