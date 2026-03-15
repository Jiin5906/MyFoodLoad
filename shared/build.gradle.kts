// Phase 0: 순수 Kotlin JVM 라이브러리로 시작
// → :app(Android)과 :backend(Spring Boot) 양쪽에서 동일하게 사용 가능
//
// Phase 1 (Shared KMP) 마이그레이션 계획:
// AGP 9.x의 com.android.kotlin.multiplatform.library 플러그인이 안정화되면
// kotlin.multiplatform + androidTarget() + jvm() 구조로 전환
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// KMP 전환 시 디렉토리 이름을 그대로 유지하기 위해 소스셋 경로를 명시
sourceSets {
    main {
        kotlin.srcDirs("src/commonMain/kotlin")
    }
    test {
        kotlin.srcDirs("src/commonTest/kotlin")
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
