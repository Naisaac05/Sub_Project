---
type: report
category: rag
status: active
updated: 2026-06-23
description: "운영 Shadow 반복 missing 질문 기반 신규 RAG 카드 후보 추출 준비 결과"
---

# 운영 Shadow missing 후보 추출 결과

## 목적

운영 Shadow에서 실제로 반복되는 `missing` 질문만 신규 RAG 카드 후보로 올리기 위한 추출 경로를 준비했다. 로컬 샘플이나 수동 추정으로 후보를 만들지 않도록 기본 게이트를 보수적으로 설정했다.

## 적용 기준

- 입력은 `scripts/evaluate_operational_shadow.py`가 만든 JSON 리포트다.
- `production_traffic_validated=true`인 리포트만 기본 추출 대상이다.
- `route`와 `expected_route`가 모두 `grounded_fallback_safe_response`인 행만 missing으로 본다.
- 같은 기술어가 최소 2회 이상 반복될 때만 후보가 된다.
- 생성되는 후보는 `approved=false`, `review_status=pending`, `definition_status=needs_human_review` 상태로 남긴다.

## 추가된 파일

- `scripts/extract_operational_missing_candidates.py`: 운영 Shadow 리포트에서 반복 missing 후보를 추출한다.
- `tests/test_operational_missing_candidates.py`: 반복 missing 추출, 운영 검증 플래그, JSON 로딩을 검증한다.
- `evals/operational_missing_repeated_fixture_2026-06-23.json`: 로컬 dry-run용 반복 missing fixture다.
- `reports/operational_missing_candidates_dryrun_2026-06-23.jsonl`: 현재 운영 샘플 기준 dry-run 결과다.
- `reports/operational_missing_candidates_fixture_2026-06-23.jsonl`: 로컬 fixture에서 후보 포맷을 확인한 결과다.

## 실행 결과

현재 워크스페이스의 `reports/operational_shadow_sample_2026-06-23.json`은 `production_traffic_validated=false`라서 실제 후보 큐에는 올리지 않았다.

```text
candidate_count=0
production_traffic_validated=false
allow_local=false
```

후보 포맷 검증용 fixture는 `--allow-local` 옵션에서만 1건을 생성한다. 이 결과는 실제 승인 후보가 아니라 스크립트 동작 확인용이다.

```text
candidate_count=1
candidate_terms=["CopyOnWriteArrayList"]
allow_local=true
```

## 운영 데이터 투입 명령

실제 운영 Shadow 리포트가 준비되면 아래 명령으로 후보 큐를 생성한다.

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
.\.venv\Scripts\python.exe scripts\extract_operational_missing_candidates.py --report reports\operational_shadow_<date>.json --output app\knowledge\candidates\operational_missing_candidates.jsonl
```

`production_traffic_validated=true`가 아닌 리포트는 후보 0건으로 처리된다. 로컬 검증 목적일 때만 `--allow-local`을 붙인다.

## 판정

실제 운영 Shadow 반복 missing 데이터가 아직 워크스페이스에 없으므로 신규 카드는 추가하지 않았다. 대신 반복 missing만 후보화하는 스크립트, 테스트, dry-run 보고서를 준비했고, 운영 검증 리포트가 들어오면 같은 기준으로 후보 큐를 만들 수 있다.
