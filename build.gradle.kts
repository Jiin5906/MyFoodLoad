// Top-level build file — 모든 서브 프로젝트/모듈 공통 설정
plugins {
    // Android
    alias(libs.plugins.android.application)                    apply false
    alias(libs.plugins.android.library)                        apply false
    alias(libs.plugins.android.kotlin.multiplatform.library)   apply false

    // Kotlin
    alias(libs.plugins.kotlin.android)              apply false
    alias(libs.plugins.kotlin.compose)              apply false
    alias(libs.plugins.kotlin.jvm)                  apply false
    alias(libs.plugins.kotlin.multiplatform)        apply false
    alias(libs.plugins.kotlin.plugin.spring)        apply false
    alias(libs.plugins.kotlin.plugin.jpa)           apply false

    // KSP & Hilt
    alias(libs.plugins.ksp)                         apply false
    alias(libs.plugins.hilt)                        apply false

    // Spring Boot
    alias(libs.plugins.spring.boot)                 apply false
    alias(libs.plugins.spring.dependency.management) apply false

    // Firebase
    alias(libs.plugins.google.services)              apply false
    alias(libs.plugins.firebase.crashlytics.plugin)  apply false

    // 코드 품질
    alias(libs.plugins.ktlint)                      apply false
}
