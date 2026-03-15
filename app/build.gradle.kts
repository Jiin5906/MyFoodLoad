import java.util.Properties

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // KSP: Room 어노테이션 처리 (Hilt Gradle Plugin과 무관하게 동작)
    alias(libs.plugins.ksp)
    // Hilt Gradle Plugin: AGP 9.x BaseExtension 호환성 문제로 보류
    // alias(libs.plugins.hilt)

    // Firebase: google-services.json 존재 시에만 활성화
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics.plugin) apply false
}

// google-services.json이 존재하면 Firebase 플러그인 활성화
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.plugin.get().pluginId)
}

android {
    namespace = "com.example.myfoodload"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.myfoodload"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 백엔드 서버 URL — 프로덕션 배포 시 local.properties에 HTTPS URL 설정 필수
        buildConfigField(
            "String",
            "BACKEND_URL",
            "\"${localProperties.getProperty("BACKEND_URL", "http://10.0.2.2:8080/")}\"",
        )

        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${localProperties.getProperty("GOOGLE_WEB_CLIENT_ID", "")}\"",
        )

        // Kakao 네이티브 앱 키 — local.properties에 KAKAO_NATIVE_APP_KEY 설정
        buildConfigField(
            "String",
            "KAKAO_NATIVE_APP_KEY",
            "\"${localProperties.getProperty("KAKAO_NATIVE_APP_KEY", "")}\"",
        )

        // Kakao REST API 키 — 카카오 모빌리티 길찾기 API 호출용
        buildConfigField(
            "String",
            "KAKAO_REST_API_KEY",
            "\"${localProperties.getProperty("KAKAO_REST_API_KEY", "")}\"",
        )
    }

    // ── 필수 환경변수 검증 (Release 빌드 시 누락되면 빌드 실패) ──────────────────
    afterEvaluate {
        tasks.matching { it.name.contains("Release") && it.name.startsWith("assemble") || it.name.startsWith("bundle") }.configureEach {
            doFirst {
                val required = listOf("GOOGLE_WEB_CLIENT_ID", "KAKAO_NATIVE_APP_KEY", "KAKAO_REST_API_KEY")
                val missing = required.filter { key ->
                    localProperties.getProperty(key, "").isBlank()
                }
                if (missing.isNotEmpty()) {
                    error("Release 빌드에 필수 환경변수가 누락되었습니다: ${missing.joinToString()}\nlocal.properties에 설정하세요.")
                }
            }
        }
    }

    signingConfigs {
        create("release") {
            // local.properties에 키스토어 정보 설정 필요:
            // KEYSTORE_FILE=app/release.jks
            // KEYSTORE_PASSWORD=your_password
            // KEY_ALIAS=your_alias
            // KEY_PASSWORD=your_key_password
            val keystoreFile = localProperties.getProperty("KEYSTORE_FILE", "")
            if (keystoreFile.isNotEmpty()) {
                storeFile = file(keystoreFile)
                storePassword = localProperties.getProperty("KEYSTORE_PASSWORD", "")
                keyAlias = localProperties.getProperty("KEY_ALIAS", "")
                keyPassword = localProperties.getProperty("KEY_PASSWORD", "")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseConfig = signingConfigs.findByName("release")
            if (releaseConfig?.storeFile != null) {
                signingConfig = releaseConfig
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // ── :shared 모듈 ──────────────────────────────────────────────────────────
    implementation(project(":shared"))

    // ── AndroidX Core & Lifecycle ─────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // ── Jetpack Compose ───────────────────────────────────────────────────────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // ── Navigation ────────────────────────────────────────────────────────────
    implementation(libs.androidx.navigation.compose)

    // ── Hilt: Gradle Plugin이 AGP 9.x와 호환되면 활성화 ──────────────────────
    // implementation(libs.hilt.android)
    // ksp(libs.hilt.compiler)
    // implementation(libs.hilt.navigation.compose)

    // ── Credential Manager + Google Sign-In ───────────────────────────────────
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)

    // ── Google Identity Services (YouTube OAuth Authorization) ────────────────
    implementation(libs.play.services.auth)

    // ── Google Location Services (FusedLocationProviderClient) ───────────────
    implementation(libs.play.services.location)

    // ── Kakao Maps ────────────────────────────────────────────────────────────
    implementation(libs.kakao.maps)

    // ── Room (오프라인 캐시) ────────────────────────────────────────────────
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ── Splash Screen ─────────────────────────────────────────────────────────
    implementation(libs.androidx.splashscreen)

    // ── ViewModel Compose ─────────────────────────────────────────────────────
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // ── DataStore ─────────────────────────────────────────────────────────────
    implementation(libs.datastore.preferences)

    // ── Coroutines ────────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // ── Retrofit + OkHttp ─────────────────────────────────────────────────────
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)

    // ── Coil (이미지 로딩) ────────────────────────────────────────────────────
    implementation(libs.coil.compose)

    // ── AdMob (광고) ────────────────────────────────────────────────────────
    implementation(libs.play.services.ads)

    // ── Firebase (google-services.json 추가 후 활성화) ──────────────────────
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)

    // ── 테스트 ────────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
