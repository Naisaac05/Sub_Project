---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI review off-topic free question and repeated follow-up 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI review off-topic free question and repeated follow-up

- 발생 일시: 2026-06-16
- 영역: ai / backend
- 심각도: medium

## 증상

AI 복습 화면에서 `점심 뭐 먹을까?`, `핸드폰 뭐로 바꿀까요?`, `저녁 식사 뭐로 할까요?`처럼 현재 코스 문제와 관련 없는 질문에도 일반 답변이 생성되었다. 답변 뒤에는 질문 종류와 무관하게 `다음 확인 질문` 템플릿이 반복되어 붙었다.

## 원인

free-question 워크플로가 `OFF_TOPIC` intent를 검색 제외 용도로만 쓰고, 답변 생성 단계에서 강제 차단하지 않았다. 또한 생활 추천 질문 일부가 임베딩 분류에서 `concept_definition` 또는 `unknown`으로 빠져 Ollama 생성 경로까지 도달할 수 있었다. 백엔드의 follow-up 생성도 `off_topic` route나 quality flag를 보지 않고 항상 학습용 확인 질문을 붙였다.

## 해결 방법

- `ai/app/workflow/embedding_intent.py`: 식사·휴대폰 구매·오락 추천처럼 명확한 생활 조언 질문은 임베딩 호출 전 `OFF_TOPIC`으로 분류한다.
- `ai/app/workflow/nodes.py`: `free_question_intent.intent == "off_topic"`이면 `off_topic_redirect` template 응답으로 즉시 종료하고 Ollama 생성, v2 fast path, 후보 카드 저장으로 넘어가지 않게 했다.
- `ai/app/workflow/runner.py`: streaming 경로도 같은 off-topic redirect helper를 사용하게 했다.
- `ai/app/knowledge/auto_candidates.py`: `off_topic_redirect` route는 auto candidate로 저장하지 않는다.
- `backend/src/main/java/com/devmatch/service/ai/AiReviewFollowUpSupport.java`: `off_topic_redirect`, `off_topic` answer style, `off_topic` quality flag가 있으면 follow-up을 비워 반복 꼬리질문을 붙이지 않는다.

## 재발 방지 / 메모

무관 질문 차단은 prompt 문구만으로 처리하면 느리고 불안정하다. 먼저 intent/scope gate에서 차단하고, 백엔드는 route/quality flag를 신뢰해 후처리해야 한다. 다음 B안에서는 `course_id -> test_id -> question_id -> card_id` 연결을 기준으로 코스 범위 안 질문과 범위 밖 질문을 더 정교하게 나누는 것이 좋다.
