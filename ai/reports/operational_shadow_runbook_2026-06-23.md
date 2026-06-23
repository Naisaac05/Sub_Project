---
type: report
category: rag
status: active
updated: 2026-06-23
description: "근거 기반 fallback 운영 Shadow 검증 실행 방법"
---

# 운영 Shadow 검증 실행 방법

## 입력 파일

JSONL 한 줄당 한 케이스를 작성한다.

```json
{"id":"frontend-react-key-list","question":"React key를 실무 리스트에서 사용할 때 주의점을 설명해줘.","expected":"approved"}
{"id":"missing-new-framework","question":"Next.js Server Action 보안 주의점을 설명해줘.","expected":"missing"}
```

필드:

- `id`: 케이스 식별자
- `question`: 실제 사용자 질문 또는 운영 Shadow 샘플 질문
- `expected`: `approved` 또는 `missing`
- `expected_route`: 직접 지정할 경우 `grounded_fallback_generation` 또는 `grounded_fallback_safe_response`

`expected=approved`는 `grounded_fallback_generation`으로 정규화된다. `expected=missing`은 `grounded_fallback_safe_response`로 정규화된다.

샘플 파일:

- `ai/evals/operational_shadow_sample_2026-06-23.jsonl`

## 실행

로컬 확장 검증:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
.\.venv\Scripts\python.exe scripts\evaluate_operational_shadow.py --input evals\operational_shadow_sample_2026-06-23.jsonl
```

운영 트래픽에서 샘플링한 JSONL을 사용하고 serve 전환 판정까지 확인할 때:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
.\.venv\Scripts\python.exe scripts\evaluate_operational_shadow.py --input evals\production_shadow_sample.jsonl --production-validated
```

출력 파일을 지정하려면:

```powershell
.\.venv\Scripts\python.exe scripts\evaluate_operational_shadow.py --input evals\production_shadow_sample.jsonl --production-validated --output reports\operational_shadow_2026-06-23.json
```

## 통과 기준

- 전체 케이스 `gate_passed=true`
- `unsafe_route_count=0`
- 승인 근거 케이스 모델 호출 1회 이상
- 근거 없음 케이스 모델 호출 0회
- 승인 근거 generation 성공률 95% 이상
- `shadow_readiness=READY`

`--production-validated`를 붙인 운영 트래픽 샘플에서 위 조건을 모두 만족하면 `serve_readiness=READY`가 된다. 로컬 샘플 또는 합성 샘플만 사용하면 `production_shadow_not_validated` blocker 때문에 serve 전환은 계속 `NOT_READY`로 남는다.

## 커밋 전 확인

운영 Shadow 스크립트와 fallback 변경을 함께 검증할 때:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
.\.venv\Scripts\python.exe -m unittest tests.test_operational_shadow_verification tests.test_grounded_fallback_live_evaluation tests.test_v2_approved_fast_path -v
.\.venv\Scripts\python.exe scripts\lint_knowledge_cards.py
```
