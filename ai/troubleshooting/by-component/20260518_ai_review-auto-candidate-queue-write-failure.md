---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI review auto candidate queue write failure 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI review auto candidate queue write failure

- 발생 날짜: 2026-05-18
- 영역: ai
- 심각도: medium

## 증상

`AI_REVIEW_AUTO_CANDIDATES_PATH`가 쓰기 불가능한 경로를 가리키면 Python AI workflow 응답 자체가 실패할 수 있었다.
예를 들어 자동 후보 저장 경로가 디렉터리이거나 권한 문제로 열 수 없는 파일이면 `PermissionError`가 전파되어
`run_review_workflow()`가 정상 응답을 반환하지 못했다.

## 원인

자동 후보 저장은 보조 기능인데, `append_auto_candidate()`가 경로가 실제 파일인지 확인하지 않고
기존 후보 ID를 읽거나 append 파일 핸들을 열었다. 이 때문에 저장 실패가 후보 큐에만 머물지 않고
메인 답변 경로까지 깨뜨렸다.

관련 위치:

- `ai/app/workflow/runner.py:58`
- `ai/app/workflow/runner.py:67`
- `ai/app/workflow/runner.py:88`
- `ai/app/knowledge/auto_candidates.py:43`
- `ai/app/knowledge/auto_candidates.py:52`
- `ai/app/knowledge/auto_candidates.py:81`

## 해결 방법

`append_auto_candidate()`에서 저장 경로가 이미 존재하는 디렉터리면 즉시 `False`를 반환하도록 했다.
기존 후보 ID 읽기와 파일 append도 `OSError`를 잡아 `False`로 처리하게 바꿨다.
즉, 자동 후보 큐 저장 실패는 후보 ID를 남기지 않는 것으로 끝나고 AI 답변은 계속 반환된다.

회귀 테스트:

- `ai/tests/test_auto_candidates.py:46`

## 재발 방지·메모

자동 후보 큐는 observability/quality feedback용 보조 경로다. 앞으로도 후보 저장, telemetry 저장,
로그 저장 같은 부가 side effect는 메인 답변 API를 실패시키지 않아야 한다.
