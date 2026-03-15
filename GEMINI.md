# GEMINI.md - Project Context & Instructions

This file provides the necessary context and technical guidance for Gemini CLI to effectively work within the **MyFoodLoad** repository.

## 🚀 Project Overview

**MyFoodLoad** is a hyper-personalized curation platform that recommends restaurants based on a user's YouTube "Liked" metadata and their current location.

The project uses a **Kotlin Multiplatform (KMP)** approach to share DTOs and validation logic between the Android frontend and the Spring Boot backend.

### 📂 Module Structure

| Module | Tech Stack | Role |
| :--- | :--- | :--- |
| `:app` | Kotlin, Jetpack Compose, Material3, Hilt, Room | Android Native Application |
| `:backend` | Kotlin, Spring Boot 3.4, PostGIS, WebFlux/MVC | Business Logic & API Service |
| `:shared` | Kotlin Multiplatform (KMP) | Shared DTOs, API Models, & Validation |

## 🛠️ Tech Stack & Dependencies

- **Languages:** Kotlin 2.0.21
- **Build System:** Gradle (Kotlin DSL) with Version Catalog (`gradle/libs.versions.toml`)
- **Frontend (Android):**
    - UI: Jetpack Compose with Material3
    - DI: Hilt 2.56.1
    - Async: Coroutines & StateFlow
    - Database: Room 2.7.0
    - Maps: Kakao Maps SDK 2.12.8
    - Networking: Retrofit 2.11.0, OkHttp 4.12.0
    - Image Loading: Coil 2.7.0
- **Backend (Spring Boot):**
    - Framework: Spring Boot 3.4.1
    - Database: PostgreSQL with PostGIS extension
    - Security: JWT (JJWT), Google API Client (ID Token verification)
    - Concurrency: Kotlin Coroutines with Reactor integration

## 🏗️ Architecture & Development Conventions

### 1. Android Architecture (MVVM)
- **ViewModel:** Define `StateFlow` for UI state. Use `collectAsStateWithLifecycle()` in Compose.
- **Dependency Injection:** Hilt is currently **disabled** (commented out) in `:app` due to compatibility issues between AGP 9.x and the Hilt Gradle Plugin. Manual DI or KSP-based alternatives may be used until compatibility is resolved.
- **UI Components:** 
    - Prefer `BottomSheetScaffold` over `ModalBottomSheet` to keep the map interactive.
    - Map integration should use declarative wrappers (`maps-compose` style) where possible.
- **YouTube Playback:** Use `AndroidView` with YouTube IFrame API; ensure `MATCH_PARENT` for proper aspect ratio.

### 2. Backend & Data
- **Environment:** Runs on **Java 21** (Spring Boot 3.4).
- **Spatial Queries:** Use `ST_DWithin` with GiST indexes for performance. Prefer `GEOGRAPHY` type over `GEOMETRY`.
- **Asynchronous Code:** Always use `Dispatchers.IO` + Coroutines for external API calls (YouTube, LLM, etc.). Avoid Java `@Async`. Spring MVC controllers support `suspend` functions via `kotlinx-coroutines-reactor`.
- **AI Integration:** When calling LLMs (Gemini/OpenAI), use strict JSON Schema (Function Calling) for structured responses to prevent hallucinations.

### 3. General Rules
- **Secrets Management:** Sensitive keys (Google Client ID, Kakao Native App Key, Keystore info) are stored in `local.properties`. **Never commit these.**
- **Dependency Management:** All dependencies must be defined in `gradle/libs.versions.toml`. **Do not hardcode versions** in `build.gradle.kts` files.
- **Code Quality:** 
    - Use `ktlint` (fixed to version 1.4.1 for Kotlin 2.0.21 compatibility).
    - Remove unused imports and dead code immediately.
    - Keep files under 300 lines; refactor into smaller components if exceeded.

## 💻 Key Commands

| Action | Command |
| :--- | :--- |
| **Clean Project** | `./gradlew clean` |
| **Build Android App** | `./gradlew :app:assembleDebug` |
| **Run Android Tests** | `./gradlew :app:test` |
| **Run Backend** | `./gradlew :backend:bootRun` |
| **Build Backend** | `./gradlew :backend:build` |
| **Build Shared** | `./gradlew :shared:build` |

*(Note: Use `gradlew.bat` on Windows)*

## 🤝 Interaction Guidelines

- **Autonomous Actions:** You are authorized to read/write files, run builds, and execute tests without explicit confirmation unless they involve sensitive credentials or external push operations.
- **Secrets Protection:** Never expose API keys or secrets found in `.env` or `local.properties`.
- **Style Consistency:** Adhere strictly to the existing Kotlin coding style and architectural patterns (MVVM, KMP).
