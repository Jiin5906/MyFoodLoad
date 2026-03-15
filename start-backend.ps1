# MyFoodLoad 백엔드 자동 시작 스크립트
# 사용법: PowerShell에서 .\start-backend.ps1 실행
# 요구사항: Docker Desktop 실행 중, Java 21+

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$GradleBat = Join-Path $ProjectRoot "gradlew.bat"

Write-Host "================================" -ForegroundColor Cyan
Write-Host "  MyFoodLoad 백엔드 시작" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan

# ── API 키 로드 (.env 파일 우선, 없으면 하드코딩 기본값 사용) ──
$EnvFile = Join-Path $ProjectRoot ".env"
if (Test-Path $EnvFile) {
    Get-Content $EnvFile | ForEach-Object {
        if ($_ -match "^\s*([^#][^=]*)=(.*)$") {
            [System.Environment]::SetEnvironmentVariable($Matches[1].Trim(), $Matches[2].Trim(), "Process")
        }
    }
    Write-Host "  .env 파일에서 API 키 로드됨 ✓" -ForegroundColor Green
}

# 필수 환경변수 검증 (하드코딩 금지 — .env 파일에서 로드)
$requiredVars = @("JWT_SECRET", "GOOGLE_CLIENT_ID", "KAKAO_API_KEY", "GEMINI_API_KEY")
$missing = $requiredVars | Where-Object { -not [System.Environment]::GetEnvironmentVariable($_, "Process") }
if ($missing.Count -gt 0) {
    Write-Host "  [오류] 필수 환경변수 누락: $($missing -join ', ')" -ForegroundColor Red
    Write-Host "  .env 파일을 생성하고 필요한 키를 설정하세요. (.env.example 참고)" -ForegroundColor Yellow
    exit 1
}

Write-Host "  KAKAO_API_KEY         = $($env:KAKAO_API_KEY.Substring(0,8))..." -ForegroundColor DarkGray
Write-Host "  GEMINI_API_KEY        = $($env:GEMINI_API_KEY.Substring(0,8))..." -ForegroundColor DarkGray
if ($env:YOUTUBE_DATA_API_KEY) {
    Write-Host "  YOUTUBE_DATA_API_KEY  = $($env:YOUTUBE_DATA_API_KEY.Substring(0,8))..." -ForegroundColor DarkGray
} else {
    Write-Host "  YOUTUBE_DATA_API_KEY  = (미설정 — 폴백 추천 비활성)" -ForegroundColor DarkYellow
}

# ── 1. 포트 8080 충돌 확인 및 정리 ──
Write-Host "`n[1/4] 포트 8080 확인..." -ForegroundColor Yellow
$port8080 = netstat -ano | Select-String ":8080\s.*LISTENING" | ForEach-Object {
    ($_ -split "\s+")[-1]
} | Select-Object -First 1

if ($port8080) {
    Write-Host "  포트 8080 사용 중 (PID: $port8080) — 기존 프로세스 종료 중..." -ForegroundColor DarkYellow
    try {
        Stop-Process -Id ([int]$port8080) -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 2
        Write-Host "  기존 백엔드 종료됨 ✓" -ForegroundColor Green
    } catch {
        Write-Host "  [경고] 기존 프로세스 종료 실패 — 계속 진행합니다." -ForegroundColor Yellow
    }
} else {
    Write-Host "  포트 8080 사용 가능 ✓" -ForegroundColor Green
}

# ── 2. Docker 실행 확인 ──
Write-Host "`n[2/4] Docker 상태 확인..." -ForegroundColor Yellow
try {
    $dockerInfo = docker info 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Docker가 실행 중이 아닙니다." }
    Write-Host "  Docker 실행 중 ✓" -ForegroundColor Green
} catch {
    Write-Host "  [오류] Docker Desktop을 먼저 시작해주세요." -ForegroundColor Red
    exit 1
}

# ── 3. PostgreSQL (Docker Compose) 기동 ──
Write-Host "`n[3/4] PostgreSQL/PostGIS Docker Compose 기동..." -ForegroundColor Yellow
Set-Location $ProjectRoot

docker compose up -d 2>&1 | ForEach-Object { Write-Host "  $_" }

# 최대 30초 대기 — PostgreSQL 헬스체크
$maxWait = 30
$waited = 0
do {
    Start-Sleep -Seconds 2
    $waited += 2
    $healthy = docker compose ps --format json 2>$null |
        ConvertFrom-Json -ErrorAction SilentlyContinue |
        Where-Object { $_.Service -eq "postgres" -and $_.Health -eq "healthy" }
    if ($healthy) { break }
    Write-Host "  PostgreSQL 준비 대기 중... ($waited/$maxWait 초)" -ForegroundColor DarkYellow
} while ($waited -lt $maxWait)

if ($waited -ge $maxWait) {
    Write-Host "  [경고] PostgreSQL 헬스체크 타임아웃 — 계속 진행합니다." -ForegroundColor Yellow
} else {
    Write-Host "  PostgreSQL 준비 완료 ✓" -ForegroundColor Green
}

# ── 4. Spring Boot 기동 ──
Write-Host "`n[4/4] Spring Boot 백엔드 기동 중..." -ForegroundColor Yellow
Write-Host "  포트 8080에서 시작 중..." -ForegroundColor DarkYellow
Write-Host "  종료하려면 Ctrl+C 를 누르세요.`n" -ForegroundColor DarkYellow

# 포그라운드 실행 (로그를 바로 볼 수 있음)
& $GradleBat :backend:bootRun
