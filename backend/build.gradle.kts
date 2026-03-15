plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.kotlin.plugin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.ktlint)
}

group = "com.example.myfoodload"
version = "0.0.1-SNAPSHOT"

// JDK 21 사용 (Spring Boot 3.4 권장)
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    compilerOptions {
        // Spring proxy를 위한 null-safety 엄격 모드
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

dependencies {
    // ── :shared 모듈 (공유 DTO) ───────────────────────────────────────────────
    implementation(project(":shared"))

    // ── Spring Boot Web ────────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-web")

    // ── Spring Data JPA + PostgreSQL/PostGIS ──────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.postgresql:postgresql")
    implementation("org.hibernate.orm:hibernate-spatial")   // PostGIS GEOGRAPHY 타입 지원

    // ── DB Migration ──────────────────────────────────────────────────────────
    implementation("org.flywaydb:flyway-database-postgresql")

    // ── Kotlin 필수 ───────────────────────────────────────────────────────────
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // ── Coroutines (Dispatchers.IO 사용 — GlobalScope 금지) ───────────────────
    implementation(libs.kotlinx.coroutines.core)
    // Spring MVC에서 suspend 컨트롤러 지원 (reactivestreams.Publisher 제공)
    implementation(libs.kotlinx.coroutines.reactor)

    // ── Spring Security ───────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-security")

    // ── JJWT (JWT 생성/검증) ──────────────────────────────────────────────────
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    // ── Google API Client (ID Token 검증) ─────────────────────────────────────
    implementation(libs.google.api.client)

    // ── Caffeine Cache ──────────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation(libs.caffeine)

    // ── 테스트 ────────────────────────────────────────────────────────────────
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// .env 파일의 환경변수를 bootRun에 자동 주입 (GEMINI_API_KEY 등)
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    val envFile = rootProject.file(".env")
    if (envFile.exists()) {
        envFile.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
            .forEach { line ->
                val (key, value) = line.split("=", limit = 2)
                environment(key.trim(), value.trim())
            }
    }
}

// ktlint-gradle 12.1.2 + Kotlin 2.0.21 호환성:
// kotlin-compiler-embeddable 버전 충돌 방지 → ktlint 1.4.1 고정
configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    version.set("1.4.1")
    filter {
        exclude("**/*.kts")   // Gradle 빌드 스크립트는 제외
    }
}
