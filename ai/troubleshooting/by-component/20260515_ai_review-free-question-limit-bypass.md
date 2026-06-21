---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI review free question limit bypass 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI review free question limit bypass

- 발생 일시: 2026-05-15
- 영역: backend / frontend
- 심각도: medium

## 증상

스마트 개념 복습에서 한 문제당 질문을 3개까지만 의도했지만, 사용자가 "궁금한 점 질문하기"를 계속 눌러 추가 질문을 할 수 있었다.

## 원인

백엔드의 3회 제한은 AI 꼬리질문 모드인 `CHECK_QUESTION`, `EXPLANATION`, `NEXT_QUESTION`만 세고 있었다. 자유 질문에 대한 AI 답변인 `FREE_ANSWER`는 제한 카운트에 포함되지 않아 `FREE_QUESTION` API를 반복 호출할 수 있었다. 프론트엔드도 남은 질문 수를 계산해 표시만 하고, "궁금한 점 질문하기" 버튼의 disabled 조건에는 반영하지 않았다.

## 해결 방법

백엔드 제한 카운트에 `FREE_ANSWER`를 포함하고, 자유 질문 처리 전에 현재 문제의 AI 응답 수가 3개 이상이면 `InvalidSessionStateException`으로 차단했다.

- `backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java:23`
- `backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java:195`
- `backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java:249`
- `backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java:542`

프론트엔드에서는 `FREE_ANSWER`도 사용 횟수에 포함하고, 남은 횟수가 0이면 자유 질문 버튼을 비활성화하며 안내 문구를 보여주도록 했다.

- `frontend/src/app/tests/results/[id]/review/page.tsx:50`
- `frontend/src/app/tests/results/[id]/review/page.tsx:54`
- `frontend/src/app/tests/results/[id]/review/page.tsx:275`
- `frontend/src/app/tests/results/[id]/review/page.tsx:287`
- `frontend/src/app/tests/results/[id]/review/page.tsx:700`

## 재발 방지 / 메모

UI에 제한 수를 보여주는 기능은 반드시 동일한 기준의 서버 검증을 함께 둔다. 카운트 대상 모드가 늘어날 때는 프론트 표시용 카운트와 백엔드 차단용 카운트를 같이 업데이트한다.
