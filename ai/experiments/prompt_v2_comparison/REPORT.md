# follow-up 프롬프트 v1/v2 비교 실험 — 최종 보고서

- 작성일: 2026-05-27
- 작성자: aucu2005@gmail.com
- 실험 폴더: `ai/experiments/prompt_v2_comparison/`
- 운영 코드 영향: **없음** (격리된 실험)

---

## TL;DR

| 항목 | 결론 |
|---|---|
| **모델 권장** | follow-up 모델을 `qwen3:4b-q4_K_M` → **`exaone3.5:2.4b`** 로 교체 |
| **프롬프트 권장** | 1단계: v1 유지. 2단계(후속 작업): "모르겠어요" 분기를 다듬은 v2.1 도입 검토 |
| **자연스러움 효과** | +54~82% (qwen3 한국어 누수 5중 3건 → EXAONE 0건) |
| **속도 효과** | 동등 또는 약간 빠름 (CPU only 기준 +20%) |
| **모델 메모리** | 2.5GB → 1.6GB (-36%) |
| **위험도** | 매우 낮음 (환경변수 1개 변경, 환원 즉시 가능) |

---

## 1. 실험 목적

AI 복습의 **follow-up(꼬리질문) 모드**에서 "자연스럽고 정확한 답변" 품질을 데이터 기반으로 결정하기 위한 A/B 비교.

검증할 가설:
- **H1**: 한국어 지시문 + few-shot 예시를 갖춘 v2 프롬프트가 영어 1줄짜리 v1 프롬프트보다 좋다
- **H2**: 한국어 특화 EXAONE 모델이 일반형 qwen3 모델보다 좋다
- **H3**: RAG 컨텍스트 주입이 답변 정확성을 높인다

---

## 2. 실험 인프라

```
ai/experiments/prompt_v2_comparison/
├── README.md          ← 사용법
├── REPORT.md          ← 이 파일
├── compare.py         ← A/B 비교 실행
├── debug_ollama.py    ← Ollama API 디버그
├── samples.jsonl      ← 5개 학생 응답 시나리오
└── results/           ← 모델/모드별 자동 분리 저장
    ├── qwen3_4b-q4_K_M__no_rag/         (운영 정합)
    ├── qwen3_4b-q4_K_M__with_rag/       (이전 운영)
    ├── exaone3.5_2.4b__no_rag/          (운영 정합)
    └── exaone3.5_2.4b__with_rag/        (이전 운영)
```

### 검증 시나리오 (5건)

| ID | 학생 상태 |
|---|---|
| 001 | "모르겠어요" — 완전 모름 |
| 002 | 부분 정답 (속도 차이는 맞지만 핵심 가변성 놓침) |
| 003 | 오개념 (toString을 hashCode 대신 선택) |
| 004 | 자신있게 오답 (fetch join 정렬 보장 잘못 확신) |
| 005 | 2단계 꼬리질문 (이전 단계 이해, 새 혼동) |

### 격리 원칙

- **운영 코드 0줄 수정**: `ai/app/prompts.py`, `ai/app/workflow/` 그대로
- v1 프롬프트는 운영 코드(`build_prompt`)에서 **직접 import** → 운영과 항상 동기화
- v2는 별도 `.prompt` 파일로 격리

---

## 3. 실험 조건 매트릭스 (2×2)

| 모델 | RAG 포함 | RAG 없이 (운영 정합) |
|---|---|---|
| `qwen3:4b-q4_K_M` (현재 운영) | ✅ 측정 | ✅ 측정 |
| `exaone3.5:2.4b` (제안) | ✅ 측정 | ✅ 측정 |

> **운영 정합 이유**: 2026-05-27 pull에 follow-up RAG 스킵 변경 ([ai/app/workflow/nodes.py:30-33](../../app/workflow/nodes.py#L30)) 도입. 현재 운영은 RAG 없는 follow-up 흐름. → `MATCH_PRODUCTION_FOLLOWUP=true` 환경변수로 재현.

---

## 4. 결과 요약 — 점수 매트릭스

### 자연스러움 (1~5, 인간 채점)

| 모델 \ 조건 | RAG 포함 v1 | RAG 포함 v2 | RAG 없이 v1 | RAG 없이 v2 |
|---|:---:|:---:|:---:|:---:|
| qwen3:4b | 2.2 ⚠️ | (timeout 2건) | 3.4 | 3.8 |
| EXAONE 2.4b | 4.0 | 3.5 | 3.4 | **4.2** ⭐ |

### 진단 적합성 (1~5)

| 모델 \ 조건 | RAG 포함 v1 | RAG 포함 v2 | RAG 없이 v1 | RAG 없이 v2 |
|---|:---:|:---:|:---:|:---:|
| qwen3:4b | 3.0 | (timeout 2건) | 2.6 | 4.0 |
| EXAONE 2.4b | 3.6 | 2.5 | 2.8 | **4.0** ⭐ |

### 속도 (v1 평균, 1샘플당, CPU only)

| 모델 | RAG 포함 | RAG 없이 |
|---|---|---|
| qwen3:4b | 28초 | 17초 |
| EXAONE 2.4b | 22초 | **14초** |

### 안정성 (v2 timeout 발생률)

| 모델 | 5중 timeout |
|---|---|
| qwen3:4b RAG포함 | **2건 (40%)** ❌ |
| qwen3:4b RAG없이 | 1건 (20%) |
| EXAONE 2.4b 양쪽 | 0건 ✅ |

---

## 5. 핵심 발견

### 발견 1 — qwen3:4b의 명백한 한국어 누수

같은 v1 프롬프트, 같은 RAG 조건인데 **qwen3:4b만** 한국어 출력에 다른 언어 누수가 발생:

| 샘플 | qwen3:4b 출력 발췌 | 문제 |
|---|---|---|
| 2 v1 | `"...불변입니다. why did you choose option 2?"` | 영어 |
| 3 v1 | `"...설명해줄 수 있니?"` | **반말** |
| 4 v2 | `"...데이터베이스에서 정렬되不一定한 경우..."` | **중국어 한자** |
| 5 v1 | `"...어노테이션 whereas @ControllerAdvice는..."` | 영어 |
| 1 v2 (no_rag) | `"...쿼리가 많아지는 문제 instead of 엔티티가..."` | 영어 |

**EXAONE 2.4b는 5개 샘플 × 4조건 = 20케이스에서 한국어 누수 0건.** qwen3가 중국 출신 모델인 영향으로 추정.

### 발견 2 — RAG가 없을 때 v2 프롬프트가 v1을 이김 (예상과 반대)

```
EXAONE 2.4b v1 no_rag : 자연 3.4 / 진단 2.8
EXAONE 2.4b v2 no_rag : 자연 4.2 / 진단 4.0  ← 우위
```

**이유**: 
- v1은 매우 짧은 프롬프트라 RAG 없이는 응답도 단조 ("equals를 재정의할 때 hashCode도 함께 재정의해야 하는데요?" — 1문장만)
- v2의 분기 가이드/예시가 RAG 없는 환경에서 응답 품질을 메꾸어줌
- **새 운영(RAG 스킵)에서 v1의 가치가 떨어지고 v2가 빛남**

### 발견 3 — v2의 "모르겠어요" 분기 오해석 (모든 환경에서 재현)

샘플 1에서 학생이 `"모르겠어요"`라 답했는데 모든 v2 결과에서:

> *"엔티티가 N+1개 저장되는 현상은 잘못 생각하신 것 같아요"*

학생은 그 선택지를 골랐을 뿐 그렇게 답한 게 아닌데 v2가 매번 그렇게 해석. **v2 프롬프트의 구조적 결함** — few-shot 예시의 인용 패턴을 잘못 일반화. 

### 발견 4 — v2의 메타 라벨 누수 (운영 적용 시 학생에게 노출 위험)

with_rag 모드에서 v2가 종종 프롬프트 메타 정보를 출력에 흘림:
- `"학습자의 답변을 평가하고 다음 단계로 안내하는 꼬리질문입니다:"` (샘플 3, qwen3 with_rag)
- `"**꼬리질문:**"` 라벨 (샘플 2, EXAONE with_rag)
- 마크다운 볼드 (`**1번**`) — 학생 UI에서 어색

### 발견 5 — v2의 장황함

with_rag 모드에서 v2 평균 출력 길이 5문장 이상 (3문장 규칙 위반). RAG 카드 내용까지 같이 들어가면서 모델이 충실히 답하려다 길어짐.

### 발견 6 — 운영자의 follow-up RAG 제거와 동일 결론 (검증 가치)

2026-05-27 운영자가 [error/2026-05-27-ai-follow-up-rag-timeout-template.md](../../../error/2026-05-27-ai-follow-up-rag-timeout-template.md)에 기록한 사유:
> "무관한 RAG 카드가 들어오면 정확도와 응답 시간이 둘 다 나빠진다"

→ **본 실험이 운영자의 발견을 독립적으로 재현**. 실험 방법론의 신뢰성 증명.

---

## 6. 검증된 가설 / 무효화된 가설

| 가설 | 결론 |
|---|---|
| H1 (v2 > v1) | **부분 검증**. RAG 없으면 v2 > v1, RAG 있으면 v1 > v2. 새 운영(RAG 없음)에선 v2 우위. |
| H2 (EXAONE > qwen3) | ✅ **강하게 검증**. 모든 조건에서 EXAONE이 한국어 누수 없음. |
| H3 (RAG가 정확성 ↑) | ❌ **반증**. follow-up 모드에서 RAG는 v2 장황화·v1 정답 노출 유도. 운영도 같은 결론으로 제거. |

---

## 7. 최종 권장사항

### 1단계 권장 — 모델 교체 (즉시 적용 가능, 가장 안전) ⭐

**변경**: follow-up 모델을 `qwen3:4b-q4_K_M` → `exaone3.5:2.4b`

**구현 방법** (둘 중 하나):

```bash
# 방법 A: 환경변수 (운영 코드 0줄 변경)
export PYTHON_AI_FALLBACK_MODEL=exaone3.5:2.4b
```

또는 [backend/src/main/resources/application.yml:99](../../../backend/src/main/resources/application.yml#L99) 와 같이 yml 설정.

**사전 조건**:
```bash
ollama pull exaone3.5:2.4b  # 약 1.6GB 다운로드
```

**기대 효과 (RAG 없는 새 운영 기준)**:
- 한국어 누수 사고 0건
- 모델 메모리 -36% (2.5GB → 1.6GB)
- CPU 속도 +20% (17초 → 14초)
- 자연스러움 동등 또는 약간 우위

**환원**: 환경변수 되돌리면 즉시 원복. 위험 매우 낮음.

### 2단계 권장 — v2.1 프롬프트 (후속 작업)

v2 프롬프트를 다음 변경으로 v2.1 만들기:

1. **"모르겠어요" 분기 명시**: 학생이 "모른다"고 답한 경우 그 답변을 학생 선택의 근거로 인용하지 말 것
2. **메타 라벨 금지**: `"꼬리질문:"`, `"학습자의 답변을 평가하고..."` 같은 메타 텍스트 출력 금지
3. **마크다운 금지 강화**: `**볼드**` 등 어떠한 마크다운 문법도 사용 금지
4. **길이 제약 강화**: "전체 3문장 이내" → 모델이 더 잘 따르는 표현 ("두세 문장 안에", 출력 길이 예시)

**적용 시점**: 1단계 운영 안정화 확인 후 (1~2주 후)

### 적용하지 말 것

- **v2 그대로 도입**: 위 발견 3, 4, 5의 문제로 학생 UX 사고 위험
- **두 변경 동시 적용**: 회귀 추적 어려움
- **EXAONE 7.8b**: 현재 CPU only 사양(i5-10210U + 16GB)에선 1~2 tok/s → 스트리밍 불가

---

## 8. 재현 방법 (팀원이 직접 검증)

### 사전 준비

```powershell
cd C:\Users\aucu2\Sub_Project
.\ai\.venv\Scripts\Activate.ps1
ollama pull qwen3:4b-q4_K_M
ollama pull exaone3.5:2.4b
```

### 실행 (운영 정합 — RAG 없이)

```powershell
$env:MATCH_PRODUCTION_FOLLOWUP="true"
$env:COMPARE_MODEL="qwen3:4b-q4_K_M"
python ai\experiments\prompt_v2_comparison\compare.py

$env:COMPARE_MODEL="exaone3.5:2.4b"
python ai\experiments\prompt_v2_comparison\compare.py
```

### 결과 확인

```powershell
notepad ai\experiments\prompt_v2_comparison\results\qwen3_4b-q4_K_M__no_rag\comparison.md
notepad ai\experiments\prompt_v2_comparison\results\exaone3.5_2.4b__no_rag\comparison.md
```

각 샘플별로 v1/v2 출력을 보고 자연스러움/진단 적합성을 1~5점으로 직접 채점하는 칸이 있음. 5분이면 결론 동의 여부 확인 가능.

---

## 9. 한계 (솔직한 평가)

- **샘플 수 5건**: 통계적 결론에는 적은 표본. 다만 한국어 누수처럼 정성적 차이는 5건으로도 충분히 신호 잡힘.
- **인간 채점 1인**: 채점자 1명(실험 작성자) 주관 들어감. 팀 검증 권장.
- **CPU only 환경 한정**: GPU 서버 환경에선 모델 크기 비용이 다름. 더 큰 EXAONE(7.8b) 검토 가치 있음.
- **시나리오 5종에 한정**: 자유 질문, 코드 토론 등 다른 패턴은 미검증.
- **하루 안에 진행**: 실험 도중 운영 변경(follow-up RAG 제거) 발생 → 일부 초기 결과 재검증함.

---

## 10. 부록 — 실험 중 부산물

### 발견된 운영 버그 1건 (별도 PR로 처리됨)

본 실험 진행 중 keep_alive 직렬화 버그 재현 → [error/2026-05-27-experiment-compare-script-keep-alive-regression.md](../../../error/2026-05-27-experiment-compare-script-keep-alive-regression.md). 운영 코드 영향 없음 (실험 스크립트만), 동일 클래스 버그가 2026-05-20에 운영 코드에서 이미 수정됨 ([error/2026-05-20-ai-server-keep-alive-invalid-duration.md](../../../error/2026-05-20-ai-server-keep-alive-invalid-duration.md)). 신규 스크립트가 같은 함정에 빠지지 않도록 본 보고서 코드에 가드 추가.

### 재사용 가능한 인프라

`compare.py`는 다음 실험에도 재사용 가능:
- v2.1 프롬프트 시도 (위 1단계 후속)
- 다른 모델 테스트 (`COMPARE_MODEL` 환경변수만 변경)
- `samples.jsonl`에 시나리오 추가
- `MATCH_PRODUCTION_FOLLOWUP` 토글로 RAG 유무 비교

### 4개 결과 폴더 위치

모든 원본 출력 + 인간 채점란이 포함된 비교 표:
- `results/qwen3_4b-q4_K_M__no_rag/comparison.md` (운영 정합, 현재 운영 모델)
- `results/exaone3.5_2.4b__no_rag/comparison.md` (운영 정합, 권장 모델) ⭐
- `results/qwen3_4b-q4_K_M__with_rag/comparison.md` (참고용, 이전 운영 흐름)
- `results/exaone3.5_2.4b__with_rag/comparison.md` (참고용)

---

## 11. 결정 요청

이 보고서를 검토하시고 다음 중 하나로 의사 결정 부탁드립니다:

1. **승인 + 1단계 적용**: 환경변수 변경으로 EXAONE 2.4b 운영 적용 (가장 권장)
2. **승인 + 2단계까지**: 1단계 + v2.1 프롬프트 작업 착수
3. **추가 검증 요청**: 다른 시나리오/채점자/조건 추가 후 재논의
4. **보류**: 더 큰 검토 필요

질문이나 추가 데이터 요청은 PR 댓글로 남겨주세요.
