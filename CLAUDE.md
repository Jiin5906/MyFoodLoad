# CLAUDE.md

This file provides guidance to Claude Code when working with this repository.

## 🚀 프로젝트 개요

**MyFoodLoad**는 사용자의 유튜브 '좋아요' 메타데이터를 분석하여 현재 위치 기반으로 맛집을 추천하는 초개인화 큐레이션 플랫폼이다.

**Kotlin Multiplatform(KMP)** 을 활용하여 Android Native Frontend와 Spring Boot Backend 간의 DTO·Validation 코드를 공유하는 멀티 모듈 구조를 지향한다.

> 현재 상태: 단일 모듈(`:app`) 초기 템플릿 상태. 멀티 모듈 구조로 확장 예정.

## 📂 모듈 구조

| 모듈 | 기술 스택 | 역할 |
|------|-----------|------|
| `:app` | Kotlin + Jetpack Compose + Material3 | Android Native UI |
| `:backend` | Kotlin + Spring WebFlux/MVC + PostGIS | 비즈니스 로직 · API |
| `:shared` | Kotlin Multiplatform (KMP) | DTO · API 모델 · Validation 공유 |

- **패키지**: `com.example.myfoodload`
- **최소 SDK**: 24 (Android 7.0) / **대상 SDK**: 36

## 🛠️ Build & Run Commands

```bash
# Android App
./gradlew :app:assembleDebug
./gradlew :app:test
./gradlew :app:connectedAndroidTest

# Spring Boot Backend
./gradlew :backend:bootRun
./gradlew :backend:build

# KMP Shared 모듈
./gradlew :shared:build

# 빌드 정리
./gradlew clean
```

*(Windows에서는 `./gradlew` 대신 `gradlew.bat` 사용)*

## 🧠 아키텍처 규칙 (필수 준수)

### 1. Frontend — Android + Jetpack Compose

- **MVVM + StateFlow**: ViewModel에 `StateFlow` 정의, Compose에서 `collectAsStateWithLifecycle()`로 구독
- **지도 연동**: XML 금지 → `maps-compose` 또는 `naver-map-compose` 선언형 위젯 사용
- **Bottom Sheet**: `ModalBottomSheet` 금지 → `BottomSheetScaffold` (Persistent) 사용하여 지도가 항상 상호작용 가능하도록 유지
- **유튜브 쇼츠 재생**: `AndroidView` 내 WebView/YouTube IFrame API 내장, 레터박스 방지를 위해 `MATCH_PARENT` 강제

### 2. Backend — Spring Boot + PostGIS

- **비동기 처리**: 외부 API 통신(YouTube API, LLM, Geocoding)은 `Dispatchers.IO` + Kotlin Coroutines 사용. `GlobalScope` 및 Java `@Async` 금지
- **공간 쿼리**: `ST_Distance` 금지 → GiST 인덱스를 활용하는 `ST_DWithin` Native Query 사용. 좌표 타입은 `GEOMETRY` 대신 `GEOGRAPHY` 사용
- **AI 파이프라인**: LLM(OpenAI/Gemini) 호출 시 환각 방지 지침 포함, 응답은 반드시 JSON Schema(Function Calling) 포맷으로 직렬화/역직렬화

### 3. 의존성 관리

- 모든 버전은 `gradle/libs.versions.toml`에서 중앙 관리
- `build.gradle.kts`에 직접 하드코딩 금지 — 반드시 `.toml`에 먼저 정의 후 참조

### 4. 코드 품질

- 사용하지 않는 import·데드 코드 즉시 제거
- `// do something` 식 생략 코드 금지 — 완전하고 즉시 실행 가능한 코드 블록 제공
- 단일 파일 300줄 초과 시 별도 파일로 분리

## 🤝 작업 진행 방식

### 동의 요청 원칙

**동의 없이 즉시 진행**하는 작업:
- 코드 작성·수정·삭제
- 파일 읽기·검색
- 빌드·테스트 실행
- Docker/백엔드 기동 및 재시작
- ADB 명령 실행 (설치, 로그 확인, GPS 설정 등)
- 에뮬레이터 조작

**동의가 필요한 경우** (개인정보·보안 위험이 있을 때만):
- API 키·비밀번호·JWT Secret 등 민감한 자격증명을 외부로 전송하거나 공개 위치에 기록하는 경우
- Git push / PR 생성 등 외부 공개 저장소에 변경사항을 반영하는 경우
- 프로덕션 DB 데이터를 직접 수정·삭제하는 경우

## 🧰 설치된 Claude 스킬 (`.claude/commands/`)

| 명령어 | 용도 |
|--------|------|
| `/android-development` | 클린 아키텍처, MVVM, Hilt, Room 등 Android 개발 표준 |
| `/android-kotlin-development` | Jetpack Compose, Retrofit, 로컬 스토리지 네이티브 개발 |
| `/kotlin-spring-reviewer` | Spring Boot + Kotlin 코드 리뷰 및 Coroutines 패턴 검토 |
| `/java-architect` | Spring Boot 3+ JPA 아키텍처 설계 가이드 |
| `/postgis-geospatial-development` | PostGIS 3.6.1 공간 쿼리 최적화 및 SRID 설계 |
| `/pg-aiguide` | 최신 PostgreSQL 스키마·인덱스·제약 조건 설계 가이드 |

**Spring Boot Automation Hooks** (`.claude/settings.json`): `:backend/` 내 `.kt` 파일 수정 시 자동으로 ktlintCheck 및 단위 테스트 실행.
