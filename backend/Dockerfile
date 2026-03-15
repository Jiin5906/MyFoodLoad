# Stage 1: Gradle 빌드
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

# Gradle wrapper + 설정 파일 먼저 복사 (캐시 레이어 활용)
COPY gradlew gradlew
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle/libs.versions.toml gradle/libs.versions.toml

# 서브 모듈 build.gradle.kts
COPY backend/build.gradle.kts backend/build.gradle.kts
COPY shared/build.gradle.kts shared/build.gradle.kts
COPY app/build.gradle.kts app/build.gradle.kts

# local.properties 더미 (Android 빌드 스킵용)
RUN echo "sdk.dir=/nonexistent" > local.properties

# 의존성 다운로드 (소스 변경 시 캐시 유지)
RUN chmod +x gradlew && ./gradlew :backend:dependencies --no-daemon || true

# 소스 코드 복사
COPY shared/src/ shared/src/
COPY backend/src/ backend/src/

# bootJar 빌드 (테스트 스킵 — 프로덕션 배포 속도)
RUN ./gradlew :backend:bootJar --no-daemon -x test -x ktlintCheck

# Stage 2: 런타임 (경량 JRE)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# t3.micro 메모리 최적화: JVM 힙 제한
ENV JAVA_OPTS="-Xms128m -Xmx384m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m"

COPY --from=builder /build/backend/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
