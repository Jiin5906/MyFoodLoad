#!/usr/bin/env python3
"""
MyFoodLoad - Gemini Code Review Script

Claude가 작성한 코드/계획을 Gemini AI가 검토하는 자동화 스크립트.
사용법: python scripts/gemini_review.py <파일경로...> [--focus "검토 집중 사항"]
"""

import sys
import os
import argparse
import subprocess
import shutil
import platform
from pathlib import Path
from datetime import datetime


# ─────────────────────────────────────────────
# 환경 설정
# ─────────────────────────────────────────────

def load_dotenv():
    """프로젝트 루트의 .env 파일을 파싱하여 os.environ에 주입."""
    env_path = Path(__file__).parent.parent / ".env"
    if not env_path.exists():
        return
    with open(env_path, encoding="utf-8") as f:
        for raw in f:
            line = raw.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, value = line.partition("=")
            value = value.strip().strip('"').strip("'")
            os.environ.setdefault(key.strip(), value)


# ─────────────────────────────────────────────
# 콘텐츠 수집
# ─────────────────────────────────────────────

def collect_content(file_paths: list[str], use_stdin: bool) -> str:
    parts: list[str] = []

    for raw_path in file_paths:
        path = Path(raw_path)
        if not path.exists():
            print(f"[WARN] 파일 없음: {raw_path}")
            continue
        suffix = path.suffix.lstrip(".")
        lang = _lang_hint(suffix)
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            print(f"[WARN] UTF-8 텍스트 파일이 아닙니다. 건너뜁니다: {raw_path}")
            continue
        parts.append(f"### 📄 {path}\n```{lang}\n{text}\n```")

    if use_stdin and not sys.stdin.isatty():
        stdin_text = sys.stdin.read().strip()
        if stdin_text:
            parts.append(f"### 📋 직접 입력\n```\n{stdin_text}\n```")

    return "\n\n".join(parts)


def _lang_hint(suffix: str) -> str:
    return {
        "kt": "kotlin", "kts": "kotlin",
        "java": "java",
        "py": "python",
        "sql": "sql",
        "json": "json",
        "yaml": "yaml", "yml": "yaml",
        "xml": "xml",
        "md": "markdown",
        "gradle": "groovy",
        "js": "javascript", "ts": "typescript",
        "sh": "bash", "bat": "batch",
        "properties": "properties", "toml": "toml",
    }.get(suffix, suffix)


# ─────────────────────────────────────────────
# 프롬프트 생성
# ─────────────────────────────────────────────

PROJECT_CONTEXT = """
MyFoodLoad: 유튜브 '좋아요' 메타데이터 분석 → 위치 기반 맛집 추천 초개인화 플랫폼

[아키텍처 필수 규칙]
- Frontend: Kotlin + Jetpack Compose + Material3 (Android)
  · 지도: maps-compose 또는 naver-map-compose (XML 금지)
  · Bottom Sheet: BottomSheetScaffold 사용 (ModalBottomSheet 절대 금지)
  · MVVM + StateFlow + collectAsStateWithLifecycle()
- Backend: Kotlin + Spring WebFlux + PostGIS
  · 공간 쿼리: ST_DWithin + GEOGRAPHY 타입 (ST_Distance, GEOMETRY 금지)
  · 비동기: Dispatchers.IO + Coroutines (GlobalScope, @Async 금지)
  · LLM 응답: JSON Schema (Function Calling) 포맷 강제
- 의존성: 모든 버전은 gradle/libs.versions.toml 중앙 관리
""".strip()

REVIEW_CHECKLIST = """
다음 6개 항목을 검토하고 각각 구체적인 피드백을 제공하세요.
수정이 불필요한 항목은 "✅ 이상 없음"으로 표시하세요.

1. **버그·논리 오류** — NPE, 엣지 케이스, 경쟁 조건
2. **아키텍처 규칙 위반** — ModalBottomSheet·GlobalScope·ST_Distance 사용 여부 등
3. **성능 문제** — 비효율 쿼리, 메모리 누수, 불필요한 recomposition
4. **보안 취약점** — SQL Injection, 민감 데이터 노출, 인증·인가 누락
5. **코드 품질** — 중복, 명명 규칙 불일치, 단일 책임 원칙 위반
6. **개선 제안** — 더 나은 패턴·라이브러리 (코드 예시 포함)

각 이슈마다 [심각도: 높음 | 중간 | 낮음] 태그를 표시하세요.
""".strip()


def build_prompt(content: str, extra_context: str, focus: str) -> str:
    focus_section = f"\n## 집중 검토 사항\n{focus}\n" if focus else ""
    extra_section = f"\n## 추가 컨텍스트\n{extra_context}\n" if extra_context else ""

    return f"""당신은 MyFoodLoad 프로젝트의 시니어 코드 리뷰어입니다.
아래 코드/계획을 검토하고 구체적인 피드백을 한국어로 작성하세요.

## 프로젝트 컨텍스트
{PROJECT_CONTEXT}
{extra_section}
## 검토 대상
{content}
{focus_section}
## 검토 항목
{REVIEW_CHECKLIST}

## 출력 형식
마크다운으로 작성하고, 심각도 높음 이슈는 최상단에 배치하세요.
각 이슈에 수정 전/후 코드 예시를 포함하세요.
"""


# ─────────────────────────────────────────────
# Gemini 호출 (CLI 모드 / API 키 모드 자동 선택)
# ─────────────────────────────────────────────

def call_gemini_cli(prompt: str, model_name: str) -> str:
    """Gemini CLI (OAuth 인증) 호출 — API 키 불필요.

    프롬프트를 stdin으로 전달한다.
    Windows에서 npm 전역 설치 명령(.cmd 래퍼)을 실행하려면 shell=True 필요.
    """
    gemini_cmd = shutil.which("gemini")
    if not gemini_cmd:
        raise FileNotFoundError(
            "gemini CLI를 찾을 수 없습니다.\n"
            "  설치: npm install -g @google/gemini-cli\n"
            "  인증: gemini auth login"
        )

    # Windows에서 .cmd/.bat 래퍼는 cmd /c를 통해 실행해야 인자가 올바르게 전달됨.
    # shutil.which()로 실제 경로를 확보 후 shell=False로 통일 → Command Injection 방지.
    if platform.system() == "Windows" and gemini_cmd.lower().endswith((".cmd", ".bat")):
        cmd_args = ["cmd", "/c", gemini_cmd, "--model", model_name]
    else:
        cmd_args = [gemini_cmd, "--model", model_name]

    result = subprocess.run(
        cmd_args,
        input=prompt,
        capture_output=True,
        text=True,
        encoding="utf-8",
        timeout=180,
        shell=False,
    )

    if result.returncode != 0:
        raise RuntimeError(
            f"Gemini CLI 오류 (종료 코드 {result.returncode}):\n"
            + (result.stderr.strip() or result.stdout.strip() or "출력 없음")
        )
    return result.stdout.strip()


def call_gemini_api(api_key: str, prompt: str, model_name: str) -> str:
    """google-genai SDK + API 키 호출."""
    try:
        from google import genai
    except ImportError:
        print("[ERROR] google-genai 패키지가 없습니다.")
        print("  설치: pip install google-genai")
        sys.exit(1)

    client = genai.Client(api_key=api_key)
    response = client.models.generate_content(model=model_name, contents=prompt)
    return response.text


def call_gemini(api_key: str | None, prompt: str, model_name: str) -> str:
    """인증 방식 우선순위:
    1. gemini CLI 설치됨 → CLI (OAuth) 우선 — API 키 무시
    2. gemini CLI 없음 + api_key 있음 → google-genai SDK
    3. 둘 다 없음 → 오류
    """
    if shutil.which("gemini"):
        print("[Gemini Review] 인증 방식: Gemini CLI (OAuth) ← gemini 설치 감지")
        return call_gemini_cli(prompt, model_name)

    if api_key:
        print("[Gemini Review] 인증 방식: API 키 ← gemini CLI 미설치 폴백")
        return call_gemini_api(api_key, prompt, model_name)

    raise RuntimeError(
        "Gemini CLI가 없고 API 키도 없습니다.\n"
        "  방법 1: npm install -g @google/gemini-cli && gemini auth login\n"
        "  방법 2: .env에 GEMINI_API_KEY 설정 (https://aistudio.google.com/app/apikey)"
    )


# ─────────────────────────────────────────────
# 결과 저장
# ─────────────────────────────────────────────

def save_result(result: str, output_path: str):
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    header = f"# 🤖 Gemini 코드 리뷰 결과\n\n> 생성 시각: {timestamp}\n\n---\n\n"
    out = Path(output_path)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(header + result + "\n", encoding="utf-8")


# ─────────────────────────────────────────────
# 진입점
# ─────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Gemini AI로 Claude 작성 코드를 검토하는 도구",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("files", nargs="*", help="검토할 파일 경로 (여러 개 가능)")
    parser.add_argument("--stdin", action="store_true", help="stdin에서 내용 읽기")
    parser.add_argument("--context", default="", metavar="TEXT", help="추가 프로젝트 컨텍스트")
    parser.add_argument("--focus", default="", metavar="TEXT", help="검토 집중 사항")
    parser.add_argument(
        "--model",
        default="gemini-3.1-pro-preview",
        metavar="MODEL",
        help="Gemini 모델명 (기본값: gemini-3.1-pro-preview)",
    )
    parser.add_argument(
        "--output",
        default=".claude/gemini_feedback.md",
        metavar="PATH",
        help="결과 저장 경로 (기본값: .claude/gemini_feedback.md)",
    )
    args = parser.parse_args()

    # API 키는 선택 사항 — 없으면 Gemini CLI(OAuth) 모드 사용
    load_dotenv()
    api_key = os.environ.get("GEMINI_API_KEY") or os.environ.get("GOOGLE_API_KEY") or None
    if not api_key:
        print("[Gemini Review] API 키 없음 → Gemini CLI(OAuth) 모드로 전환")

    content = collect_content(args.files, args.stdin)

    if not content.strip():
        print("[ERROR] 검토할 내용이 없습니다.")
        print("  파일 경로를 인자로 지정하거나 --stdin 옵션을 사용하세요.")
        print("  예시: python scripts/gemini_review.py app/src/main/java/.../Screen.kt")
        sys.exit(1)

    print(f"[Gemini Review] 모델    : {args.model}")
    print(f"[Gemini Review] 입력 크기: {len(content):,} 문자")
    print("[Gemini Review] 검토 요청 중...\n")

    prompt = build_prompt(content, args.context, args.focus)
    result = call_gemini(api_key, prompt, args.model)

    save_result(result, args.output)

    print("=" * 60)
    # Windows CP949 터미널에서 이모지 출력 시 UnicodeEncodeError 방지
    sys.stdout.buffer.write((result + "\n").encode("utf-8", errors="replace"))
    sys.stdout.buffer.flush()
    print("=" * 60)
    print(f"\n[Gemini Review] 결과 저장됨 → {args.output}")


if __name__ == "__main__":
    main()
