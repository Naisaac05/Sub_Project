---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI review free question count included AI prompts 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI review free question count included AI prompts

- 발생 날짜: 2026-05-15
- 영역: backend / frontend
- 심각도: medium

## 증상

스마트 개념 복습에서 사용자가 자유 질문을 2개만 했는데도 상단 카드가 `사용 3/3`, `남은 질문 0개`로 표시되어 추가 질문 버튼이 막혔다.

## 원인

프론트의 질문 사용량 계산이 사용자 자유 질문이 아니라 AI 메시지(`CHECK_QUESTION`, `EXPLANATION`, `NEXT_QUESTION`, `FREE_ANSWER`)를 세고 있었다. 백엔드의 자유 질문 제한도 `FREE_QUESTION` 사용자 메시지가 아니라 AI 응답 개수를 기준으로 검사해, AI의 첫 확인 질문이나 답변까지 제한 횟수에 포함될 수 있었다.

## 해결 방법

프론트 사용량 계산을 `USER + FREE_QUESTION` 메시지만 세도록 변경했다.

- `frontend/src/app/tests/results/[id]/review/page.tsx:104`
- `frontend/src/app/tests/results/[id]/review/page.tsx:275`

백엔드 자유 질문 제한도 `USER + FREE_QUESTION`만 세는 `countFreeQuestions` 기준으로 변경했다.

- `backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java:249`
- `backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java:551`

## 재발 방지 / 메모

질문 제한의 의미를 “AI가 던진 질문 수”가 아니라 “사용자가 누른 자유 질문 수”로 고정한다. CHECK_ANSWER 흐름에서 AI가 이어서 묻는 확인 질문은 학습 단계 진행용 메시지이므로 자유 질문 한도에 포함하지 않는다.
