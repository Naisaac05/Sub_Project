---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI review auto candidate default queue disabled 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI review auto candidate default queue disabled

- 발생 날짜: 2026-05-20
- 영역: ai / backend
- 심각도: medium

## 증상

관리자 AI 지식 후보 화면에서 새 자동 후보를 불러오려고 해도 새 후보가 보이지 않았다. 실제 로컬 파일도 `ai/app/knowledge/candidates/auto_candidates.jsonl`이 생성되어 있지 않았다.

## 원인

Python workflow의 `candidate_save_node()`가 `AI_REVIEW_AUTO_CANDIDATES_PATH` 환경변수가 없으면 바로 return했다. Spring Boot는 `app.ai-review.auto-candidates-path` 기본값으로 `../ai/app/knowledge/candidates/auto_candidates.jsonl`을 읽도록 되어 있지만, Python 쪽이 기본 파일을 만들지 않으므로 관리자 import가 가져올 JSONL queue 자체가 없었다.

관련 위치:

- `ai/app/workflow/graph.py:112`
- `backend/src/main/resources/application.yml:68`
- `backend/src/main/java/com/devmatch/service/AiReviewCandidateApprovalV2Service.java:85`

## 해결 방법

`ai/app/workflow/graph.py`에 기본 자동 후보 큐 경로를 추가했다. 이제 `AI_REVIEW_AUTO_CANDIDATES_PATH`가 없어도 Python workflow는 `ai/app/knowledge/candidates/auto_candidates.jsonl`에 후보를 저장한다.

`ai/tests/test_workflow_runner.py`에 환경변수가 없는 상태에서도 `candidate_save_node()`가 기본 queue 경로로 후보를 저장하려는지 검증하는 회귀 테스트를 추가했다.

## 재발 방지 / 메모

Spring import 경로와 Python append 경로는 항상 같은 queue를 바라봐야 한다. 운영에서는 `AI_REVIEW_AUTO_CANDIDATES_PATH`를 명시해도 되지만, 로컬 MVP에서는 환경변수를 빼도 기본 queue가 동작해야 관리자 화면에서 새 후보를 확인할 수 있다.
