# MyFoodLoad 출시 준비 보고서

> 작성일: 2026-03-14
> 분석 범위: `:app`, `:backend`, `:shared` 전체 모듈

---

## 1. CRITICAL — 즉시 조치 필수

### 1.1 API 키 관리 (git push 전 주의)

| 파일 | 포함 항목 |
|------|----------|
| `local.properties` | GOOGLE_WEB_CLIENT_ID, JWT_SECRET, KAKAO_NATIVE_APP_KEY, KAKAO_REST_API_KEY, BACKEND_URL |
| `.env` | JWT_SECRET, GOOGLE_CLIENT_ID, GEMINI_API_KEY, KAKAO_API_KEY |

> **현재 상태:** 코드가 온라인(GitHub 등)에 push된 적 없으므로 키 재발급 불필요. 현재 키 그대로 사용 가능.

**조치 사항 (GitHub push 전에 반드시):**
- [ ] `.gitignore`에 `local.properties`, `.env` 포함 여부 재확인
- [ ] push 전 `git filter-branch` 또는 `BFG Repo-Cleaner`로 git 히스토리에서 민감 정보 제거, 또는 새 repo로 시작
- [ ] public repo로 올릴 경우에만 키 재발급 검토

---

### 1.2 AdMob 테스트 ID 하드코딩

| 파일 | 위치 | 현재 값 |
|------|------|---------|
| `app/.../AdmobBanner.kt` | Line 14 | `ca-app-pub-3940256099942544/6300978111` (테스트 배너) |
| `app/.../InterstitialAdManager.kt` | Line 15 | `ca-app-pub-3940256099942544/1033173712` (테스트 전면) |
| `AndroidManifest.xml` | Line 31-33 | `ca-app-pub-3940256099942544~3347511713` (테스트 앱 ID) |

> **참고:** 네이티브 광고(`AdmobNativeCard.kt`)는 사용하지 않음 — 배포 시 제거 대상.

**조치 사항:**
- [ ] Google AdMob에 앱 등록 후 실제 앱 ID 발급
- [ ] 배너 광고 단위 ID + 전면 광고 단위 ID 각각 생성
- [ ] 위 3곳의 테스트 ID를 실제 ID로 교체 (BuildConfig 방식 권장)
- [ ] `AdmobNativeCard.kt` 및 관련 참조 제거

---

### 1.3 Release 키스토어 미설정

| 파일 | 설명 |
|------|------|
| `app/build.gradle.kts` (Lines 63-91) | signingConfigs 구조만 존재, 실제 keystore 파일 없음 |

**조치 사항:**
- [ ] Release keystore 생성: `keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000`
- [ ] `local.properties`에 KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD 설정
- [ ] keystore 파일은 절대 git에 커밋하지 않을 것 (CI/CD 시크릿으로 관리)
- [ ] Google Play App Signing 활성화 권장

---

## 2. HIGH — 출시 전 반드시 해결

### 2.1 BACKEND_URL 에뮬레이터 주소

| 파일 | 현재 값 | 문제 |
|------|---------|------|
| `app/build.gradle.kts` (Line 38) | 기본값 `http://10.0.2.2:8080/` | 에뮬레이터 전용 로컬호스트 |
| `local.properties` (Line 23) | `http://192.168.31.160:8080/` | 개발용 내부 IP |

**조치 사항:**
- [ ] 프로덕션 백엔드 서버 구축 (클라우드 VM 또는 서버리스)
- [ ] 도메인 + SSL 인증서 설정 (HTTPS 필수)
- [ ] `BACKEND_URL`을 `https://api.myfoodload.com/` 형태의 프로덕션 URL로 교체

---

### 2.2 CORS 개발용 도메인 허용

| 파일 | 위치 | 현재 허용 도메인 |
|------|------|-----------------|
| `backend/.../AppConfig.kt` | Lines 19-20 | `http://localhost:3000`, `http://10.0.2.2:8080` |

**조치 사항:**
- [ ] 개발용 URL 제거
- [ ] 프로덕션 도메인만 허용하도록 변경
- [ ] 환경별 프로필 분리 권장 (`application-prod.properties`)

---

### 2.3 DB 기본 자격증명

| 파일 | 현재 값 | 문제 |
|------|---------|------|
| `application.properties` (Lines 5-6) | 기본값 `postgres` / `postgres` | 기본 비밀번호 |
| `docker-compose.yml` (Lines 8-9) | `POSTGRES_USER: postgres` / `POSTGRES_PASSWORD: postgres` | 하드코딩 |

**조치 사항:**
- [ ] 프로덕션 DB에 강력한 비밀번호 설정
- [ ] `application.properties`에서 기본값 제거 (환경변수 필수화)
- [ ] `docker-compose.yml`은 개발 전용으로 유지, 프로덕션은 별도 구성

---

### 2.4 Dockerfile 부재

프로젝트 루트에 백엔드 배포용 Dockerfile이 없음.

**조치 사항:**
- [ ] 멀티스테이지 Dockerfile 생성 (Gradle 빌드 → JRE 런타임)
- [ ] docker-compose.prod.yml 또는 Kubernetes 매니페스트 작성
- [ ] 컨테이너 레지스트리(ECR, GCR 등) 구성

---

### 2.5 앱 버전 관리

| 파일 | 현재 값 |
|------|---------|
| `app/build.gradle.kts` (Line 30) | `versionCode = 1` |
| `app/build.gradle.kts` (Line 31) | `versionName = "1.0"` |

**조치 사항:**
- [ ] 첫 출시는 현재 값 사용 가능
- [ ] 업데이트마다 `versionCode` 반드시 증가 (Play Store 필수)
- [ ] 시맨틱 버저닝 정책 수립 (예: 1.0.0 → 1.0.1 → 1.1.0)

---

## 3. MEDIUM — 출시 전 권장

### 3.1 크래시 리포팅 (Firebase Crashlytics)

- [ ] Firebase 프로젝트 생성 및 `google-services.json` 구성
- [ ] `firebase-crashlytics` 의존성 추가
- [ ] 프로덕션 크래시 모니터링 대시보드 구축

### 3.2 환경변수 문서화

- [ ] `.env.example` 파일 생성 (키 이름만 나열, 값 없이)
- [ ] README.md에 필수 환경변수 목록 및 설정 방법 문서화

### 3.3 빌드 시 검증

- [ ] 필수 API 키(Kakao, Google, AdMob) 누락 시 빌드 실패 처리
- [ ] `app/build.gradle.kts`에 `require()` 또는 `error()` 검증 추가

### 3.4 CI/CD 파이프라인

- [ ] GitHub Actions / GitLab CI 구성
- [ ] 자동 빌드 → 테스트 → APK/AAB 서명 → Play Store 배포
- [ ] 시크릿 관리: API 키, keystore를 CI/CD 시크릿으로 관리

---

## 4. 이미 잘 구성된 항목 (변경 불필요)

| 항목 | 파일 | 상태 |
|------|------|------|
| Network Security (Release HTTPS 강제) | `app/src/main/res/xml/network_security_config.xml` | `cleartextTrafficPermitted=false` |
| HTTP 로깅 레벨 | `AppContainer.kt` (Lines 42-44) | Release: `NONE`, Debug: `BODY` |
| 에러 스택트레이스 비활성화 | `application.properties` (Lines 31-32) | `include-stacktrace=never` |
| ProGuard/R8 | `app/build.gradle.kts` + `proguard-rules.pro` | Release에서 minify + shrink 활성화 |
| Hibernate DDL | `application.properties` (Line 16) | `validate` (안전 모드) |
| SQL 로깅 비활성화 | `application.properties` (Lines 17, 21) | `show-sql=false`, `format_sql=false` |
| HikariCP 커넥션 풀 | `application.properties` (Lines 9-14) | 합리적 수치 (max=20, idle=5) |
| JWT 토큰 만료 | `application.properties` (Lines 40-41) | Access 1h, Refresh 7d |

---

## 5. 프로덕션 필수 환경변수 목록

### 백엔드 서버 측

| 변수명 | 설명 | 기본값 유무 |
|--------|------|------------|
| `DATABASE_URL` | PostgreSQL + PostGIS 연결 URL | 있음 (localhost) — 제거 필요 |
| `DATABASE_USERNAME` | DB 사용자명 | 있음 (postgres) — 제거 필요 |
| `DATABASE_PASSWORD` | DB 비밀번호 | 있음 (postgres) — 제거 필요 |
| `JWT_SECRET` | JWT 서명 키 (256bit 이상) | 없음 (필수) |
| `GOOGLE_CLIENT_ID` | Google OAuth 클라이언트 ID | 없음 (필수) |
| `GEMINI_API_KEY` | Google Gemini API 키 | 빈 문자열 (필수화 필요) |
| `KAKAO_API_KEY` | Kakao Local API 키 | 빈 문자열 (필수화 필요) |
| `YOUTUBE_DATA_API_KEY` | YouTube Data API v3 키 | 빈 문자열 (필수화 필요) |

### 앱 측 (local.properties)

| 변수명 | 설명 |
|--------|------|
| `BACKEND_URL` | 프로덕션 백엔드 HTTPS URL |
| `GOOGLE_WEB_CLIENT_ID` | Google OAuth 웹 클라이언트 ID |
| `KAKAO_NATIVE_APP_KEY` | Kakao 네이티브 앱 키 |
| `KAKAO_REST_API_KEY` | Kakao REST API 키 |
| `KEYSTORE_FILE` | Release keystore 경로 |
| `KEYSTORE_PASSWORD` | Keystore 비밀번호 |
| `KEY_ALIAS` | 키 별칭 |
| `KEY_PASSWORD` | 키 비밀번호 |

---

## 6. 귀하가 직접 수행해야 할 작업

코드 수정이 아닌, 외부 플랫폼에서의 작업:

1. **Google AdMob** — 앱 등록, 앱 ID + 배너 광고 단위 ID + 전면 광고 단위 ID 발급 (네이티브 제외)
2. **Release Keystore** — `keytool` 명령으로 생성, 안전한 곳에 백업
3. **Google Play Console** — 개발자 계정 등록 (일회성 $25)
4. **Play Store 등록 정보** — 스크린샷, 앱 설명, 개인정보처리방침 URL
5. **git push 전** — `.gitignore` 확인 후 히스토리 정리 또는 새 repo로 시작

---

## 7. Claude에게 요청 가능한 코드 작업

위 분석 결과 중 코드 수정이 필요한 항목:

- [ ] AdMob 테스트 ID → BuildConfig 방식으로 전환
- [ ] CORS 설정을 환경별 프로필로 분리
- [ ] DB 기본 자격증명 제거
- [ ] `.env.example` 생성
- [ ] 빌드 시 필수 환경변수 검증 로직 추가
- [ ] Firebase Crashlytics 의존성 추가 및 설정

---

## 8. AWS 서버 배포 — Claude가 SSH로 수행하는 작업

### 8.1 귀하가 사전에 제공해야 할 정보

| # | 항목 | 설명 | 예시 |
|---|------|------|------|
| 1 | **EC2 인스턴스 IP** | 탄력적 IP(Elastic IP) 할당 권장 | `3.35.xx.xx` |
| 2 | **SSH 키 파일 경로** | `.pem` 파일의 로컬 경로 | `C:\Users\User\Downloads\myfoodload-key.pem` |
| 3 | **SSH 사용자명** | EC2 AMI에 따라 다름 | Ubuntu: `ubuntu`, Amazon Linux: `ec2-user` |
| 4 | **도메인** | HTTPS 인증서 발급에 필요 | `api.myfoodload.com` |
| 5 | **환경변수 값들** | 아래 목록 참조 | — |

#### 필요한 환경변수 값 (채팅으로 전달)

```
JWT_SECRET=현재 사용 중인 값
GOOGLE_CLIENT_ID=현재 사용 중인 값
GEMINI_API_KEY=현재 사용 중인 값
KAKAO_API_KEY=현재 사용 중인 값
YOUTUBE_DATA_API_KEY=현재 사용 중인 값
DB_PASSWORD=새로 정할 강력한 비밀번호
```

> DB_PASSWORD는 프로덕션용으로 새로 정하시면 됩니다. 나머지는 `.env`에 있는 기존 값 그대로 사용 가능.

---

### 8.2 귀하가 AWS 콘솔에서 미리 해야 할 작업

#### Step 1: EC2 인스턴스 생성

1. AWS Console → EC2 → "인스턴스 시작"
2. **AMI**: Ubuntu Server 24.04 LTS (프리 티어 가능)
3. **인스턴스 유형**: `t3.small` (2 vCPU, 2GB RAM) 권장
   - `t3.micro` (1GB RAM)도 가능하나 PostGIS + Spring Boot 동시 구동 시 메모리 부족 위험
4. **키 페어**: 새로 생성 → `.pem` 파일 다운로드 후 안전한 곳에 보관
5. **스토리지**: 20GB 이상 (Docker 이미지 + DB 데이터)

#### Step 2: 보안 그룹 설정

| 포트 | 프로토콜 | 소스 | 용도 |
|------|---------|------|------|
| 22 | TCP | 내 IP | SSH 접속 |
| 80 | TCP | 0.0.0.0/0 | HTTP (Let's Encrypt 인증 + HTTPS 리다이렉트) |
| 443 | TCP | 0.0.0.0/0 | HTTPS (실제 API 트래픽) |

> **주의:** 포트 8080은 열지 않습니다. Nginx가 443→8080 내부 프록시 처리.

#### Step 3: 탄력적 IP 할당

1. EC2 → 탄력적 IP → "할당" → 인스턴스에 연결
2. 이 IP를 도메인 DNS의 A 레코드로 설정

#### Step 4: 도메인 DNS 설정

- 도메인 관리 페이지에서 A 레코드 추가:
  ```
  타입: A
  이름: api (또는 @)
  값: [탄력적 IP]
  TTL: 300
  ```
- DNS 전파 완료까지 최대 24시간 소요 (보통 수 분)

---

### 8.3 Claude가 SSH로 수행할 작업 (상세)

귀하가 위 준비를 마치고 정보를 알려주면, Claude가 SSH를 통해 아래 작업을 **순서대로** 수행합니다.

#### Phase A: 서버 초기 설정

```
A-1. 시스템 패키지 업데이트 (apt update && apt upgrade)
A-2. Docker Engine + Docker Compose 설치
A-3. 방화벽(ufw) 설정: 22, 80, 443만 허용
A-4. 스왑 메모리 설정 (t3.small 기준 2GB 스왑 추가)
```

#### Phase B: 프로덕션 파일 생성 및 전송

로컬에서 아래 파일들을 작성한 뒤 `scp`로 서버에 전송합니다.

```
B-1. backend/Dockerfile (멀티스테이지 빌드)
     - Stage 1: Gradle 빌드 (bootJar)
     - Stage 2: eclipse-temurin:21-jre-alpine 런타임
     - 최종 이미지 크기 약 200MB 이내

B-2. docker-compose.prod.yml
     - postgres: postgis/postgis:15-3.4, 프로덕션 자격증명, 볼륨 마운트
     - backend: Dockerfile 기반 빌드, 환경변수 주입, postgres 의존
     - nginx: nginx:alpine, 80/443 포트, SSL 인증서 마운트

B-3. nginx/nginx.conf
     - 80 → 443 리다이렉트 (HTTP → HTTPS 강제)
     - 443에서 SSL 종료 후 backend:8080으로 리버스 프록시
     - proxy_pass http://backend:8080
     - SSL 인증서 경로: /etc/letsencrypt/live/{도메인}/

B-4. .env.prod (서버 전용, git에 포함 안 됨)
     - 귀하가 제공한 환경변수 값으로 구성
```

#### Phase C: SSL 인증서 발급 (Let's Encrypt)

```
C-1. Certbot 설치 (snap install certbot)
C-2. Nginx 임시 기동 (80 포트, HTTP 전용)
C-3. certbot certonly --webroot 로 인증서 발급
C-4. 인증서 자동 갱신 cron 등록 (90일마다 자동)
C-5. Nginx 재기동 (443 포트, HTTPS 활성화)
```

#### Phase D: 컨테이너 빌드 및 기동

```
D-1. 프로젝트 소스를 서버에 전송 (scp 또는 git clone)
D-2. docker compose -f docker-compose.prod.yml build
D-3. docker compose -f docker-compose.prod.yml up -d
D-4. Flyway 마이그레이션 자동 실행 확인 (Spring Boot 기동 시)
D-5. 헬스체크: curl https://{도메인}/api/health 응답 확인
```

#### Phase E: 검증 및 마무리

```
E-1. HTTPS 접속 테스트 (curl + 브라우저)
E-2. PostgreSQL + PostGIS 정상 동작 확인
E-3. API 엔드포인트 응답 테스트 (/api/restaurants 등)
E-4. 앱 local.properties의 BACKEND_URL을 https://{도메인}/ 으로 변경
E-5. 앱 Release 빌드 후 프로덕션 서버 연결 테스트
```

---

### 8.4 최종 서버 구성도

```
┌─────────────────────────────────────────────────┐
│  AWS EC2 (Ubuntu 24.04, t3.small)               │
│                                                 │
│  ┌─────────────────────────────────────────┐    │
│  │  Docker Compose                         │    │
│  │                                         │    │
│  │  ┌───────────┐    ┌──────────────────┐  │    │
│  │  │  Nginx    │    │  Spring Boot     │  │    │
│  │  │  :80/:443 │───→│  :8080 (내부)    │  │    │
│  │  │  SSL 종료  │    │  backend 컨테이너 │  │    │
│  │  └───────────┘    └───────┬──────────┘  │    │
│  │                           │             │    │
│  │                   ┌───────▼──────────┐  │    │
│  │                   │  PostgreSQL      │  │    │
│  │                   │  + PostGIS       │  │    │
│  │                   │  :5432 (내부)    │  │    │
│  │                   │  볼륨: pgdata    │  │    │
│  │                   └──────────────────┘  │    │
│  └─────────────────────────────────────────┘    │
│                                                 │
│  /etc/letsencrypt/  ← SSL 인증서 (자동 갱신)     │
└─────────────────────────────────────────────────┘

         ▲
         │ HTTPS (443)
         │
┌────────┴────────┐
│  Android 앱     │
│  BACKEND_URL=   │
│  https://api.   │
│  myfoodload.com │
└─────────────────┘
```

---

### 8.5 예상 비용 (AWS)

| 항목 | 사양 | 월 예상 비용 (서울 리전) |
|------|------|------------------------|
| EC2 t3.small | 2 vCPU, 2GB RAM | ~$19/월 |
| EC2 t3.micro | 1 vCPU, 1GB RAM | ~$9/월 (프리 티어 12개월 무료) |
| EBS 스토리지 | 20GB gp3 | ~$1.6/월 |
| 탄력적 IP | 인스턴스 연결 시 | 무료 (미연결 시 $3.6/월) |
| 데이터 전송 | 월 100GB 아웃바운드 기준 | ~$9/월 |
| **합계 (t3.small)** | | **~$30/월** |
| **합계 (t3.micro, 프리 티어)** | | **~$0/월 (첫 12개월)** |

> **참고:** 도메인 비용은 별도 (Route 53: 연 $12~, 외부 도메인 등록 가능)

---

## 9. 배포 완료 보고 (2026-03-14)

### 9.1 Claude가 완료한 작업

| # | 작업 | 상태 | 비고 |
|---|------|------|------|
| 1 | 스왑 메모리 2GB 설정 (`/swapfile`) | **완료** | `/etc/fstab` 영구 등록 |
| 2 | Docker Engine + Docker Compose 설치 | **완료** | Docker 28.2.2 |
| 3 | 방화벽(ufw) 설정: 22, 80, 443 | **완료** | |
| 4 | `backend/Dockerfile` 생성 (멀티스테이지) | **완료** | JVM 힙 128~384MB 제한 (t3.micro 최적화) |
| 5 | `docker-compose.prod.yml` 생성 | **완료** | PostgreSQL shared_buffers=64MB, max_connections=30 |
| 6 | `nginx/nginx.conf` 생성 | **완료** | 80→443 리다이렉트, SSL 종료, proxy_read_timeout 120s |
| 7 | `.env.prod` 생성 (서버 전용) | **완료** | `.gitignore`에 추가됨 |
| 8 | Let's Encrypt SSL 인증서 발급 | **완료** | 만료: 2026-06-12, 자동 갱신 cron 등록 |
| 9 | 소스 전송 + Docker Compose 빌드 | **완료** | BUILD SUCCESSFUL |
| 10 | 3개 컨테이너 기동 (postgres, backend, nginx) | **완료** | 모두 정상 |
| 11 | 헬스체크 통과 | **완료** | `curl https://api.myfoodload.shop/api/health` → `{"status":"UP"}` |
| 12 | AdMob 테스트 ID → 실제 ID 교체 | **완료** | 앱 ID, 배너, 전면 광고 3곳 |
| 13 | CORS 프로덕션 도메인 변경 | **완료** | `https://api.myfoodload.shop` |
| 14 | `local.properties` BACKEND_URL 변경 | **완료** | `https://api.myfoodload.shop/` |
| 15 | `application.properties` DB 기본 비밀번호 제거 | **완료** | 환경변수 필수화 |

### 9.2 서버 현재 상태

- **URL**: `https://api.myfoodload.shop`
- **EC2**: t3.micro (1 vCPU, 1GB RAM + 2GB 스왑)
- **메모리**: RAM ~717MB + 스왑 ~141MB 사용 (안정)
- **컨테이너**: postgres(healthy), backend(running), nginx(running)
- **SSL**: Let's Encrypt, 매주 월요일 03시 자동 갱신

### 9.3 서버 관리 명령어

```bash
# SSH 접속
ssh -i "C:\Users\User\Downloads\myfoodload-key.pem" ubuntu@13.125.32.70

# 서비스 상태 확인
sudo docker ps
sudo docker logs myfoodload-backend --tail 50

# 서비스 재시작
cd /home/ubuntu/myfoodload
sudo docker compose -f docker-compose.prod.yml --env-file .env.prod restart

# 코드 업데이트 후 재배포
# 1. 로컬에서 소스를 tar로 전송
# 2. sudo docker compose -f docker-compose.prod.yml --env-file .env.prod build backend
# 3. sudo docker compose -f docker-compose.prod.yml --env-file .env.prod up -d backend

# 메모리 확인
free -h

# 로그 실시간 확인
sudo docker logs -f myfoodload-backend
```

### 9.4 남은 작업 (귀하가 직접 수행)

| # | 작업 | 설명 |
|---|------|------|
| 1 | **Release Keystore 생성** | `keytool` 명령으로 생성 후 `local.properties`에 경로/비밀번호 설정 |
| 2 | **앱 빌드 및 실기기 테스트** | `gradlew.bat :app:assembleDebug` → 설치 → 프로덕션 서버 연결 확인 |
| 3 | **Google Play Console 등록** | 개발자 계정 등록 ($25), 앱 등록 정보 작성 |
| 4 | **개인정보처리방침 URL** | Play Store 필수, 간단한 웹페이지 준비 |
| 5 | **AdmobNativeCard.kt 제거** | 사용하지 않는 네이티브 광고 파일 정리 (Claude에게 요청 가능) |
| 6 | **git push 전 히스토리 정리** | `.gitignore` 확인, 새 repo 시작 권장 |


● Google Play Store 출시 과정

1단계: Google Play Console 등록

- https://play.google.com/console 접속
- 개발자 계정 등록 (일회성 등록비 $25)
- 본인 인증 완료

2단계: 앱 만들기

- Play Console → "앱 만들기" 클릭
- 앱 이름, 기본 언어, 앱/게임 유형, 유료/무료 선택

3단계: 스토어 등록 정보 작성

- 앱 아이콘: 512x512px PNG
- 기능 그래픽: 1024x500px (스토어 상단 배너)
- 스크린샷: 최소 2장 (폰), 태블릿은 선택
- 앱 설명: 짧은 설명 (80자) + 전체 설명 (4000자)
- 카테고리: 음식 및 음료
- 개인정보처리방침 URL: 필수 (위치정보, Google 로그인 사용하므로)

4단계: 앱 콘텐츠 설정 (필수 선언)

- 개인정보처리방침: URL 입력
- 광고 포함 여부: "예" (AdMob 사용)
- 콘텐츠 등급: 설문 작성 → 자동 등급 부여
- 타겟 연령층: 13세 이상 (Google 로그인, 위치 사용)
- 데이터 안전: 수집하는 데이터 종류 선언 (위치, 이메일, YouTube 데이터 등)
- 정부 앱 여부: 아니오

5단계: 테스트 트랙 (권장)

- 내부 테스트 → 최대 100명, 심사 없이 바로 배포
- 비공개 테스트 → 지정된 사용자 그룹
- 공개 테스트 → 누구나 참여 가능
- AAB 업로드 → 테스터에게 배포 → 피드백 수집

6단계: 프로덕션 출시

- 프로덕션 트랙 → "새 버전 만들기"
- app-release.aab 업로드
- 버전 이름/노트 작성
- "검토를 위해 출시" 클릭

7단계: Google 심사

- 신규 앱: 보통 3일 ~ 7일 소요
- 위치 권한, YouTube API 사용 등으로 추가 심사 가능
- 거부 시 사유 확인 후 수정/재제출

8단계: 출시 후

- AdMob 콘솔에서 앱 연결 → 광고 심사 통과 → 실제 광고 노출 시작
- Play Console에서 충돌 보고서, 사용자 리뷰 모니터링

  ---
현재 앱에서 출시 전 준비가 필요한 항목

┌─────────────────────────┬───────────┐
│          항목           │   상태    │
├─────────────────────────┼───────────┤
│ 릴리스 AAB 빌드         │ ✅ 완료   │
├─────────────────────────┼───────────┤
│ 서명 키 설정            │ ✅ 완료   │
├─────────────────────────┼───────────┤
│ ProGuard 적용           │ ✅ 완료   │
├─────────────────────────┼───────────┤
│ 개인정보처리방침 페이지 │ ❌ 필요   │
├─────────────────────────┼───────────┤
│ 스토어 스크린샷         │ ❌ 필요   │
├─────────────────────────┼───────────┤
│ 앱 아이콘 512x512       │ 확인 필요 │
├─────────────────────────┼───────────┤
│ versionCode 업데이트    │ 확인 필요 │
└─────────────────────────┴───────────┘

개인정보처리방침 페이지 작성을 도와드릴까요?
