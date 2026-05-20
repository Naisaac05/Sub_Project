# AI review layer free-question fallback

- 발생 날짜: 2026-05-19
- 범위: backend
- 심각도: medium

## 증상

AI 복습 화면에서 `궁금한 점 질문하기`로 "계층은 어떻게 있나요??"처럼 현재 문제의 핵심 개념을 물으면, Controller/Service/Repository/Entity 설명 대신 "질문으로 이해했어요" 계열의 일반 fallback 답변이 반환됐다.

## 원인

`RuleBasedAiReviewService.buildFreeQuestionFallback()`이 주제별 fallback을 고를 때 사용자 자유 질문 텍스트만 `topicSpecificFallback()`에 넘겼다. 이 함수는 Git tag, idempotent, network 정도만 처리했고, 현재 문제 본문/선택지에 있는 layered architecture 맥락은 보지 못했다. 그 결과 Python/Ollama가 비어 있을 때 계층 질문이 일반 템플릿으로 떨어졌다.

## 해결 방법

`backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java:478`에서 주제 판별 입력을 사용자 질문 + 현재 문제 본문 + 선택지로 확장했다. 또한 `계층`, `layer`, `Controller`, `Service`, `Repository`, `Entity`가 감지되면 계층 구조와 transaction boundary를 직접 설명하는 fallback을 반환하도록 추가했다.

회귀 테스트는 `backend/src/test/java/com/devmatch/service/ai/RuleBasedAiReviewServiceTest.java:245`에 추가했다. AI 클라이언트가 모두 빈 응답을 반환해도 `FREE_QUESTION` 답변에 `Controller`, `Service`, `Repository`, `Entity`, `transaction`이 포함되는지 확인한다.

## 재발 방지 / 메모

자유 질문 fallback은 사용자 질문만 보면 짧은 한국어 질문에서 의도를 잃기 쉽다. 새 주제별 fallback을 추가할 때는 반드시 현재 문제 본문과 선택지도 함께 판별 입력에 포함해야 한다.
