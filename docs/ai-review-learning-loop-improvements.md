# AI Review Learning Loop Improvements

작성일: 2026-05-12

## 목표

스마트 개념 복습 페이지를 단순한 AI 채팅 화면이 아니라, 틀린 문제의 오개념을 짧은 단계로 회복하는 학습 루프로 개선한다.

핵심 방향은 세 가지다.

- 첫 화면과 첫 질문은 빠르게 보여준다.
- AI는 꼭 필요한 피드백과 자유 질문에 집중한다.
- 사용자는 현재 단계, 약점, 진행률, 다음 행동을 즉시 이해한다.

## 이번 구현 내용

### 1. 첫 꼬리 질문 템플릿화

기존에는 첫 꼬리 질문도 Python AI 또는 Ollama 호출 대상이 될 수 있었다. 이 구조는 세션 시작부터 응답 지연이 발생하고, 로컬 AI 서버 상태에 따라 첫 화면이 흔들릴 수 있다.

이번 변경에서는 첫 꼬리 질문을 규칙 기반 템플릿으로 즉시 생성하도록 바꿨다.

관련 파일:
- backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java

효과:
- 복습 세션 시작 속도 개선
- 첫 질문 생성에 들어가는 토큰 비용 제거
- AI 서버가 느려도 학습 시작이 막히지 않음

### 2. AI 응답 길이 제한 강화

Python AI와 Ollama 프롬프트 모두 후속 질문과 자유 질문 응답을 더 짧게 제한했다.

관련 파일:
- ai/app/service.py
- backend/src/main/java/com/devmatch/service/ai/OllamaAiReviewClient.java

적용한 규칙:
- 후속 피드백은 3문장 이하
- 한 번에 다음 질문은 하나만
- 자유 질문 응답도 3문장 이하
- 표 형태 답변 금지

효과:
- 응답 시간 감소
- 토큰 사용량 감소
- 사용자가 긴 설명에 묻히지 않고 바로 답변 가능

### 3. 학습 상태 패널 추가

복습 화면 상단에 현재 학습 상태를 보여주는 3개 패널을 추가했다.

관련 파일:
- frontend/src/app/tests/results/[id]/review/page.tsx

추가된 정보:
- 현재 점검 개념
- 학습 단계
- 진행률

효과:
- 사용자가 지금 어떤 문제를 복습 중인지 바로 이해
- 채팅 화면이 아니라 학습 도구처럼 보임
- 완료된 문제 수와 전체 진행률이 드러남

### 4. 제출 실패 메시지 세분화

기존에는 대부분의 실패가 "답변을 제출하지 못했습니다."로만 표시됐다. 이제 타임아웃, 422 검증 오류, 500 계열 오류를 구분해 안내한다.

관련 파일:
- frontend/src/app/tests/results/[id]/review/page.tsx

효과:
- 개발 중 원인 파악이 빨라짐
- 사용자가 서버 재시작이 필요한 상황을 더 쉽게 이해
- 로컬 AI 장애와 일반 제출 실패를 구분 가능

### 5. Python AI null 입력 방어

백엔드 데이터 중 `options`, `question`, `correct_answer`, `selected_answer`, `evaluation` 등이 null로 들어와도 Python AI 서버가 422로 거절하지 않도록 정규화했다.

관련 파일:
- ai/app/schemas.py

효과:
- 일부 문제 데이터가 비어 있어도 복습 흐름 유지
- FastAPI 요청 검증 실패 감소
- 로컬 개발 중 데이터 품질 문제에 더 강해짐

### 6. 문제별 학습 화면 분리

기존 화면은 모든 질문과 답변을 한 채팅 흐름에 계속 누적해서 보여줬다. 이 방식은 문제를 이동해도 이전 대화가 계속 이어져 보여서 사용자가 현재 어떤 문제를 학습 중인지 놓치기 쉽다.

이번 변경에서는 현재 활성 문제의 메시지만 본문에 보여주고, 이전 문제들은 짧은 요약 카드로 접어둔다.

관련 파일:
- frontend/src/app/tests/results/[id]/review/page.tsx

효과:
- 질문이 바뀌면 사실상 새 문제 페이지처럼 보임
- 현재 문제에 집중하기 쉬움
- 이전 문제 대화를 처음부터 끝까지 다시 읽지 않아도 됨
- 긴 세션에서도 화면 정보량이 안정적으로 유지됨

현재 요약은 프론트에서 메시지 상태를 기준으로 만든 가벼운 UI 요약이다. 장기적으로는 백엔드에 `learning_state` 또는 `question_summary`를 저장해 AI 호출에도 같은 요약을 재사용하는 편이 좋다.

### 7. 자유 선택형 질문 복습

순차적으로 다음 문제만 따라가는 방식은 학습 흐름을 통제하기 쉽지만, 사용자가 궁금한 문제로 바로 돌아가 질문하기 어렵다. 이번 변경에서는 왼쪽 틀린 문제 목록에서 원하는 문제를 클릭하면 해당 문제의 전체 대화를 보고, 그 문제에 바로 답변 또는 자유 질문을 할 수 있게 했다.

관련 파일:
- frontend/src/app/tests/results/[id]/review/page.tsx
- frontend/src/lib/ai-review.ts
- backend/src/main/java/com/devmatch/dto/aireview/AiReviewSubmitRequest.java
- backend/src/main/java/com/devmatch/controller/AiReviewController.java
- backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java

효과:
- 사용자가 원하는 틀린 문제를 자유롭게 선택해 질문 가능
- 이전 문제도 읽기 전용이 아니라 추가 질문 가능
- 백엔드가 `questionId`를 받아 선택한 문제 기준으로 평가/답변
- 단, `다음 문제로`는 순차 복습 흐름을 유지하기 위해 현재 진행 문제에서만 활성화

## 사용성 측면 개선

### 현재 단계가 보임

사용자는 더 이상 "AI가 무슨 질문을 하는지"만 보는 것이 아니라, 현재 단계가 선택 이유 설명인지, 개념 보정인지, 자유 질문 확인인지 알 수 있다.

### 다음 행동이 명확해짐

입력창 아래 안내 문구와 버튼 구분으로 사용자는 다음 중 무엇을 해야 하는지 쉽게 고를 수 있다.

- 확인 질문에 답하기
- 궁금한 점 질문하기
- 다음 문제로 넘어가기

### 실패 상황이 덜 막막함

422, 타임아웃, 서버 오류를 구분해 보여주므로 "AI가 안 됨"으로 뭉뚱그리지 않는다.

## 효율성 측면 개선

### 첫 질문 AI 호출 제거

첫 질문은 개인화 필요성이 낮기 때문에 템플릿으로 처리했다. 가장 비용 대비 효과가 큰 개선이다.

### 짧은 프롬프트 응답

AI 응답 문장 수를 제한해서 로컬 모델의 생성 시간을 줄이고, 토큰 사용량도 낮췄다.

### fallback 안정성

Python AI가 실패하더라도 Ollama로 내려갈 수 있도록 Ollama 클라이언트의 AUTO 모드 실행 조건을 완화했다.

## 다음 단계 로드맵

### 1. 스트리밍 응답

Ollama `stream: true` 응답을 Python AI 서버에서 받아 SSE로 프론트에 전달한다. 실제 처리 시간이 같아도 사용자는 답변이 바로 시작된다고 느낀다.

권장 구현:
- Python AI: Ollama stream 읽기
- Backend 또는 Python AI: SSE endpoint 제공
- Frontend: 응답 chunk를 메시지에 누적 표시

### 2. 문제별 캐싱

반복 생성할 필요가 없는 콘텐츠를 캐싱한다.

캐싱 후보:
- 첫 꼬리 질문
- 기본 개념 설명
- 정답/오답 비교 설명
- 대표 코드 예시
- 변형 문제

### 3. 학습 상태 저장

세션별로 압축된 학습 상태를 저장한다.

예시:

```text
학습자는 Java String 비교에서 ==와 equals 차이를 혼동함.
equals가 값 비교라는 점은 이해했지만, ==가 참조 비교라는 설명이 부족함.
```

효과:
- 매번 전체 대화 기록을 보내지 않아도 됨
- 토큰 절약
- 긴 세션에서도 피드백 일관성 유지

권장 저장 단위:
- session_id
- question_id
- misconception_summary
- latest_evaluation
- last_user_answer_summary
- updated_at

### 4. 변형 문제 생성

사용자가 `UNDERSTOOD`로 평가되면 비슷한 변형 문제를 하나 제공한다. 단순 설명 이해가 아니라 전이 학습 여부를 확인할 수 있다.

### 5. 복습 카드 저장

세션 종료 시 약점 개념을 카드로 저장한다.

카드 구성:
- 개념
- 내가 헷갈린 부분
- 정리 문장
- 코드 예시
- 다음 복습일

## 운영 메모

로컬 개발 중에는 다음 서버를 모두 확인해야 한다.

- Backend: Spring Boot 8080
- Frontend: Next.js 3000
- Python AI: FastAPI 8001
- Ollama: 11434

Java 백엔드 변경은 백엔드 서버 재시작이 필요하다. Python AI 변경은 `--reload`로 실행 중이면 자동 반영될 수 있지만, 확실히 하려면 Python AI 서버도 재시작한다.
