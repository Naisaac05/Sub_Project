---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI review polite concept question missed fast path and off-topic advice leake..."

---

# AI review polite concept question missed fast path and off-topic advice leaked

- 발생 일시: 2026-06-16
- 영역: ai / backend / frontend
- 심각도: medium

## 증상

AI 복습 화면에서 `useEffect가 뭔가요?`가 승인된 v2 카드가 있는데도 약 30초 이상 걸리고, 생성 답변에 코드블록이 섞이면 검은 배경으로 강하게 표시됐다. `css가 뭔가요?`처럼 정의형 질문 뒤에는 비슷한 꼬리질문 문장이 반복됐고, `핸드폰줄 두고 올까요?` 같은 생활 조언 질문도 학습 답변처럼 처리됐다.

## 원인

`ai/app/workflow/embedding_intent.py:272`의 기술 정의 fast rule이 `뭐야`, `무엇인가`, `란` 등만 처리하고 `뭔가요`를 처리하지 못했다. 그 결과 `useEffect가 뭔가요?`가 임베딩 분류기로 넘어가 `sub_intent=practical`로 오판되어 v2 승인 카드 fast path 지원 대상에서 빠졌다. 또한 생활 조언 차단 규칙이 식사·기기 구매 중심이라 `핸드폰줄`, `우산` 같은 비기술 소지품 질문이 follow-up으로 잘못 분류됐다. 꼬리질문은 `backend/src/main/java/com/devmatch/service/ai/AiReviewFollowUpSupport.java:40`에서 definition 계열을 단일 템플릿으로 만들었고, 코드블록은 `frontend/src/app/tests/results/[id]/review/page.tsx:1039`와 `frontend/src/app/tests/results/[id]/review/page.tsx:1076`에서 어두운 `gray-800` 배경으로 고정되어 있었다.

## 해결 방법

- `ai/app/workflow/embedding_intent.py:272`에 `뭔가요`, `뭐예요`, `뭐에요`, `무엇인가요`를 기술 정의 fast rule로 추가해 승인 카드 fast path를 먼저 타게 했다.
- `ai/app/workflow/embedding_intent.py:227`에 생활 소지품·일상 행동 패턴을 추가하고, 강한 기술 신호가 없을 때만 off-topic으로 차단했다.
- `backend/src/main/java/com/devmatch/service/ai/AiReviewFollowUpSupport.java:90`에 주제 해시 기반 definition follow-up 템플릿을 추가해 같은 문장 반복을 줄였다.
- `frontend/src/app/tests/results/[id]/review/page.tsx:1039`와 `frontend/src/app/tests/results/[id]/review/page.tsx:1076`의 코드블록을 밝은 박스형 스타일로 변경했다.

## 재발 방지 / 메모

정의형 질문은 `뭐야`뿐 아니라 높임말 변형까지 규칙 테스트에 포함해야 한다. 생활 조언 차단은 금칙어 하나로 막기보다 “강한 기술 신호가 없는 조언형 문장”을 먼저 판별하는 편이 안전하다. UI markdown 스타일은 생성 답변 품질과 별개로 사용자가 답변을 무겁게 느끼게 만들 수 있으므로, 복습 화면에서는 밝은 코드블록을 기본값으로 유지한다.
