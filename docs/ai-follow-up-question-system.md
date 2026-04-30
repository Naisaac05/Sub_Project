# AI 꼬리질문 기반 개념 정리 시스템 설계

## 목표

실력 테스트에서 틀린 문제를 기반으로 AI가 꼬리질문을 던지고, 사용자가 답변하면서 개념을 다시 정리하도록 돕는다.

이 기능의 목적은 점수만 보여주는 것이 아니라 다음 흐름을 만드는 것이다.

- 사용자가 왜 틀렸는지 스스로 설명하게 한다.
- 틀린 개념을 작은 단위로 다시 확인한다.
- AI가 바로 정답을 알려주기보다 질문으로 사고를 유도한다.
- 최종적으로 개념 요약과 복습 포인트를 남긴다.
- 매칭된 멘토가 사용자의 약점을 확인할 수 있게 한다.

## 현재 테스트 구조와 연결

현재 테스트는 코스별 진단 테스트로 구성되어 있다.

- Java Backend
- Node Backend
- Python Backend
- Frontend
- Android
- iOS
- Flutter
- React Native
- DevOps
- Data Engineer
- ML Engineer
- Game Server
- Kafka Deep Dive
- Distributed Lock Deep Dive

각 테스트는 10문항이며, 사용자의 답변은 `TestResult`, `TestAnswer`로 저장된다.

AI 기능은 새 테스트를 다시 만드는 것이 아니라, 이미 저장된 `TestResult`와 `TestAnswer`를 활용한다.

## 전체 흐름

1. 사용자가 코스별 실력 테스트를 제출한다.
2. 백엔드는 채점 결과와 문항별 정오답을 저장한다.
3. 결과 화면에서 `AI 복습 시작` 버튼을 제공한다.
4. 백엔드는 해당 `testResultId`의 틀린 문제 목록을 조회한다.
5. AI는 첫 번째 틀린 문제를 기반으로 꼬리질문을 생성한다.
6. 사용자가 답변한다.
7. AI가 답변을 평가하고 다음 꼬리질문 또는 개념 힌트를 제공한다.
8. 3~5회 정도의 짧은 대화를 마치면 AI가 개념 요약을 생성한다.
9. 복습 세션 결과를 DB에 저장한다.
10. 멘토는 매칭된 멘티의 테스트 결과와 AI 복습 요약을 확인한다.

## MVP 범위

처음부터 완전한 튜터 시스템을 만들기보다 아래 범위로 시작한다.

- 틀린 문제만 AI 복습 대상으로 사용
- 문제당 꼬리질문 3개까지
- 사용자의 답변을 `이해함 / 부분 이해 / 재학습 필요`로 평가
- 마지막에 한글 요약 제공
- 멘토 확인용 요약 저장

처음 MVP에서는 음성, 실시간 스트리밍, 긴 자유 대화는 제외한다.

## 필요한 데이터

AI에게 넘겨야 하는 정보는 최소화한다.

```json
{
  "courseKey": "java-backend",
  "testTitle": "Java Backend + AI 실력 진단",
  "question": "JPA의 N+1 문제를 줄이기 위한 방법으로 가장 적절한 것은 무엇인가요?",
  "options": ["fetch join 또는 EntityGraph 사용", "..."],
  "correctAnswer": 0,
  "selectedAnswer": 2,
  "score": 0,
  "area": "JPA",
  "userAnswerText": "사용자의 꼬리질문 답변"
}
```

현재 `Question`에는 `area`, `explanation`, `conceptTags`가 없으므로 추후 확장하면 좋다.

## 추천 DB 확장

### ai_review_sessions

AI 복습 세션의 단위다.

| 컬럼 | 설명 |
| --- | --- |
| id | 세션 ID |
| user_id | 사용자 ID |
| test_result_id | 테스트 결과 ID |
| course_key | 코스 키 |
| status | IN_PROGRESS / COMPLETED |
| summary | 최종 요약 |
| weakness_tags | 약점 태그 JSON |
| created_at | 생성일 |
| completed_at | 완료일 |

### ai_review_messages

AI와 사용자 간 메시지를 저장한다.

| 컬럼 | 설명 |
| --- | --- |
| id | 메시지 ID |
| session_id | AI 복습 세션 ID |
| question_id | 연결된 테스트 문제 ID |
| role | USER / AI |
| content | 메시지 내용 |
| evaluation | UNDERSTOOD / PARTIAL / NEEDS_REVIEW |
| created_at | 생성일 |

### question_concepts

문제별 개념 태그를 관리한다.

| 컬럼 | 설명 |
| --- | --- |
| question_id | 문제 ID |
| course_key | 코스 키 |
| area | 예: JPA, Transaction, React Rendering |
| concept_tags | 예: ["N+1", "fetch join"] |
| explanation | 정답 해설 |

MVP에서는 별도 테이블 없이 `Question`에 필드를 추가해도 된다.

## API 설계

### AI 복습 세션 시작

```http
POST /api/tests/results/{testResultId}/ai-review/start
```

응답:

```json
{
  "sessionId": 1,
  "currentQuestionId": 10,
  "aiMessage": "이 문제에서 N+1이 왜 발생한다고 생각했나요?"
}
```

### 사용자 답변 제출

```http
POST /api/ai-review/sessions/{sessionId}/messages
```

요청:

```json
{
  "questionId": 10,
  "answer": "연관된 엔티티를 조회할 때 쿼리가 반복해서 나가기 때문입니다."
}
```

응답:

```json
{
  "evaluation": "PARTIAL",
  "aiMessage": "좋아요. 그럼 fetch join을 쓰면 어떤 쿼리 변화가 생길까요?",
  "isCompleted": false
}
```

### 세션 완료 요약 조회

```http
GET /api/ai-review/sessions/{sessionId}
```

응답:

```json
{
  "summary": "사용자는 N+1 발생 원인은 이해했지만 fetch join과 EntityGraph의 차이는 추가 복습이 필요합니다.",
  "weaknessTags": ["JPA", "N+1", "fetch join"],
  "recommendations": [
    "JPA 연관관계 로딩 전략 복습",
    "fetch join 사용 시 pagination 제한 확인"
  ]
}
```

## AI 프롬프트 설계

AI는 정답을 바로 알려주는 선생님이 아니라, 사용자가 생각을 꺼내도록 돕는 튜터 역할이어야 한다.

### 시스템 프롬프트

```text
너는 DevMatch의 코스별 실력 테스트 복습 튜터다.
사용자가 틀린 문제를 바탕으로 개념을 정확히 이해하도록 돕는다.

규칙:
- 한국어로 답한다.
- 정답을 바로 길게 설명하지 않는다.
- 먼저 사용자의 생각을 확인하는 짧은 꼬리질문을 한다.
- 한 번에 하나의 질문만 한다.
- 사용자의 답변이 틀렸다면 비난하지 말고 힌트를 준다.
- 사용자의 답변을 UNDERSTOOD, PARTIAL, NEEDS_REVIEW 중 하나로 평가한다.
- 3회 이상 같은 개념을 어려워하면 짧은 개념 정리를 제공한다.
- 마지막에는 멘토가 볼 수 있는 약점 요약을 만든다.
```

### 사용자 컨텍스트 프롬프트

```text
코스: Java Backend + AI
개념 영역: JPA / N+1
문제: JPA의 N+1 문제를 줄이기 위한 방법으로 가장 적절한 것은 무엇인가요?
정답: fetch join 또는 EntityGraph 사용
사용자 선택: 트랜잭션 제거

사용자가 왜 이 선택을 했는지 확인하는 첫 번째 꼬리질문을 만들어줘.
```

### 평가 프롬프트

```text
사용자 답변:
"트랜잭션이 있으면 쿼리가 많이 나가서 그런 것 같습니다."

위 답변을 평가해줘.
반드시 JSON으로만 응답해.

형식:
{
  "evaluation": "UNDERSTOOD | PARTIAL | NEEDS_REVIEW",
  "feedback": "짧은 피드백",
  "nextQuestion": "다음 꼬리질문",
  "weaknessTags": ["태그"]
}
```

## 코스별 AI 꼬리질문 방향

### Backend 계열

Java, Node, Python Backend는 사용자가 원인과 설계 판단을 설명할 수 있는지 확인한다.

예:

- 왜 트랜잭션 경계가 Service 계층에 있는 것이 자연스러운가?
- 이 API에서 동시성 문제가 생긴다면 어떤 데이터가 깨질 수 있는가?
- 비동기 처리를 쓸 때 실패 처리는 어디서 관리해야 하는가?

### Frontend 계열

Frontend, React Native, Flutter는 렌더링과 상태 흐름을 설명하게 한다.

예:

- 이 state가 바뀌면 어떤 컴포넌트가 다시 렌더링될까?
- 왜 key를 index로 쓰면 문제가 생길 수 있을까?
- 로딩/에러/빈 상태를 나눠야 하는 이유는 무엇일까?

### Mobile 계열

Android, iOS는 생명주기와 권한, 네이티브 기능을 중심으로 묻는다.

예:

- 앱이 백그라운드로 갔다가 돌아올 때 어떤 상태가 유지되어야 할까?
- 권한 거부 시 사용자는 어떤 흐름을 경험해야 할까?
- 로컬 저장소에 저장하면 안 되는 데이터는 무엇일까?

### Infra / Data / AI 계열

DevOps, Data Engineer, ML Engineer는 운영 상황과 장애 대응 사고를 확인한다.

예:

- 이 파이프라인이 재실행되면 데이터가 중복될 수 있을까?
- 모델 성능이 갑자기 떨어졌다면 어떤 지표를 먼저 볼까?
- 배포 후 장애가 발생하면 어떤 순서로 롤백할까?

### Deep Dive 계열

Kafka, Distributed Lock은 실무 시나리오 판단 중심으로 묻는다.

예:

- consumer가 같은 메시지를 두 번 처리하면 어떤 문제가 생길까?
- offset commit을 언제 해야 안전할까?
- 락 TTL이 너무 길거나 짧으면 각각 어떤 문제가 생길까?

## 프론트 화면 구성

### 결과 화면

테스트 제출 후 결과 화면에 버튼을 추가한다.

- `AI로 틀린 문제 복습하기`
- 틀린 문제가 없으면 `AI 복습이 필요한 문제가 없습니다` 표시

### AI 복습 화면

경로 예시:

```text
/tests/results/{resultId}/review
```

화면 구성:

- 왼쪽: 틀린 문제 목록
- 오른쪽: AI 대화 영역
- 상단: 현재 개념 태그
- 하단: 답변 입력창
- 완료 후: 개념 요약 카드

### 멘토 확인 화면

멘토가 매칭된 멘티 상세에서 확인할 정보:

- 테스트 점수
- 틀린 문제 수
- 약점 태그
- AI 복습 완료 여부
- AI 요약
- 멘토가 이어서 질문하면 좋은 추천 질문

## 구현 순서

1. `Question`에 `area`, `conceptTags`, `explanation` 필드 추가
2. 기존 seed 문제에 코스별 태그와 해설 추가
3. `TestResult` 기준 틀린 문제 조회 API 추가
4. AI 복습 세션 테이블 추가
5. AI 호출 서비스 추가
6. 결과 화면에 `AI 복습 시작` 버튼 추가
7. AI 복습 대화 화면 구현
8. 복습 완료 요약 저장
9. 멘토 매칭 상세 화면에 요약 노출

## AI 호출 서비스 구조

```text
AiReviewService
├─ startReview(testResultId, userId)
├─ submitAnswer(sessionId, userId, answer)
├─ buildPrompt(...)
├─ callAi(...)
├─ parseEvaluation(...)
└─ completeSession(...)
```

## 주의할 점

- AI가 틀린 해설을 할 수 있으므로 문제의 정답/해설은 백엔드 데이터 기준으로 제공한다.
- AI 응답은 JSON 형식을 강제하고 파싱 실패 시 재시도한다.
- 민감한 개인정보는 프롬프트에 넣지 않는다.
- 멘토에게 보여줄 요약은 너무 길지 않게 3~5줄로 제한한다.
- 사용자가 답변을 못하면 AI가 정답을 바로 말하기보다 힌트를 1회 제공한다.

## MVP 완료 기준

- 틀린 문제 1개 이상이면 AI 복습 세션을 시작할 수 있다.
- AI가 문제당 최소 1개 이상의 꼬리질문을 생성한다.
- 사용자 답변에 대해 이해도 평가가 저장된다.
- 복습 완료 후 요약이 저장된다.
- 멘토가 매칭 상세에서 요약을 볼 수 있다.

## 이후 확장

- 틀린 문제와 비슷한 추가 문제 자동 생성
- 개념별 복습 노트 자동 생성
- 멘토가 AI 요약에 코멘트 추가
- 재시험 시 이전 약점 태그와 비교
- 코스별 취약 영역 대시보드 제공
