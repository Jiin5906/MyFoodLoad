---
name: gemini-review
description: >
  Claude가 작성한 코드/계획을 Gemini AI가 검토하고,
  Claude가 그 피드백을 반영하여 코드를 개선하는 2단계 리뷰 워크플로우.
  코드 작성 후 품질 검증이 필요할 때 실행하세요.
---

# Gemini 코드 리뷰 워크플로우

이 명령어는 **Claude(1차 구현) → Gemini(2차 검토) → Claude(3차 반영)** 3단계 파이프라인을 자동으로 실행합니다.

## 사용법

```
/gemini-review                              # 현재 대화의 최근 수정 파일 자동 탐지
/gemini-review app/src/main/.../Screen.kt   # 특정 파일 지정
/gemini-review File1.kt File2.kt            # 여러 파일 동시 검토
/gemini-review --focus "성능 최적화"        # 특정 항목 집중 검토
```

---

## 실행 단계 (아래 순서를 반드시 지키세요)

### ① 검토 대상 확인

- 사용자가 파일 경로를 지정했으면 해당 파일을 사용합니다.
- 지정하지 않았으면 **현재 대화에서 가장 최근에 생성/수정한 파일들**을 파악합니다.
- 검토 대상 목록을 사용자에게 한 줄로 보고합니다.

### ② Gemini 검토 실행

아래 명령을 Bash 도구로 실행합니다. (파일이 여러 개면 공백으로 나열)

```bash
python scripts/gemini_review.py <파일경로1> [파일경로2 ...] \
  --focus "$FOCUS_ARG" \
  --output .claude/gemini_feedback.md
```

> `--focus` 인자는 사용자가 지정한 경우에만 포함합니다.

실행 전 사전 점검:
- `scripts/gemini_review.py` 파일 존재 여부 확인
- `google-generativeai` 패키지 미설치 시 → `pip install google-generativeai` 안내
- `.env` 파일의 `GEMINI_API_KEY` 미설정 시 → `.env.example`을 참조하도록 안내

### ③ 검토 결과 분석

`.claude/gemini_feedback.md` 파일을 읽고, 다음 형식으로 사용자에게 요약 보고합니다.

```
## Gemini 검토 요약

### 🔴 높음 (즉시 수정 필요)
- [이슈 제목]: 한 줄 설명

### 🟡 중간 (개선 권장)
- [이슈 제목]: 한 줄 설명

### 🟢 낮음 (선택적 개선)
- [이슈 제목]: 한 줄 설명

✅ 이상 없음 항목: [목록]
```

### ④ 피드백 반영

심각도에 따라 다음 기준으로 처리합니다.

| 심각도 | 처리 방침 |
|--------|-----------|
| **높음** | 사용자 확인 없이 즉시 수정 후 변경 내용 보고 |
| **중간** | 수정 계획 제시 → 사용자 승인 후 수정 |
| **낮음** | 목록 제공 → 사용자가 원하는 항목만 선택하여 수정 |

모든 수정 완료 후 **변경된 파일 목록과 수정 내용 요약**을 제공합니다.

---

## 오류 처리

| 상황 | 조치 |
|------|------|
| `scripts/gemini_review.py` 없음 | "스크립트 파일이 없습니다. 프로젝트 루트에 `scripts/` 디렉토리를 확인하세요." 출력 |
| `google-generativeai` 미설치 | `pip install google-generativeai` 명령어 안내 |
| API 키 없음 | `.env.example` → `.env` 복사 및 `GEMINI_API_KEY` 입력 안내 |
| 검토 대상 파일 없음 | 사용자에게 파일 경로를 직접 지정해달라고 요청 |

---

## 전체 흐름 요약

```
사용자: /gemini-review
  │
  ▼
[Claude] 검토 대상 파일 파악
  │
  ▼
[Claude → Bash] python scripts/gemini_review.py <파일들>
  │
  ▼
[Gemini API] 코드 분석 → .claude/gemini_feedback.md 저장
  │
  ▼
[Claude] 피드백 요약 보고
  │
  ▼
[Claude] 심각도별 코드 수정 → 변경 사항 요약
  │
  ▼
사용자: 최종 결과 확인
```
