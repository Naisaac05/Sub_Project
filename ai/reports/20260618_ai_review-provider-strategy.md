---
type: report
category: inference
status: active
updated: 2026-06-18
description: "AI 복습 튜터 도입 전략: OpenAI + Rule-Based Fallback 관련 주요 기능 및 가이드라인"

---

# AI 복습 튜터 도입 전략: OpenAI + Rule-Based Fallback

## 왜 AI를 도입하는가

DevMatch의 실력 테스트는 단순 점수 확인이 아니라, 사용자가 어떤 개념을 정확히 모르는지 찾는 진단 도구다. 여기에 AI를 붙이면 포트폴리오에서 다음 스토리를 만들 수 있다.

- 코스별 테스트 결과를 분석한다.
- 틀린 문제와 개념 태그를 추출한다.
- AI가 사용자의 오개념을 확인하는 꼬리질문을 생성한다.
- 사용자의 답변을 이해도 기준으로 평가한다.
- 최종 약점 요약을 생성한다.
- 멘토가 그 요약을 보고 멘토링 방향을 잡는다.

중요한 점은 "ChatGPT를 붙였다"가 아니라, AI를 서비스 흐름 안에 어떻게 안전하게 연결했는지다.

## 최종 방향

처음부터 OpenAI API만 의존하지 않는다.

```text
OPENAI_API_KEY 있음  -> OpenAI 기반 AI 복습
OPENAI_API_KEY 없음  -> Rule-Based 복습
```

이 구조의 장점:

- 개발 중에는 비용이 들지 않는다.
- 발표나 시연 때만 API 키를 넣어 AI 기능을 보여줄 수 있다.
- API 장애나 결제 문제에도 서비스가 완전히 멈추지 않는다.
- 취업 포트폴리오에서 "AI fallback까지 고려했다"고 설명할 수 있다.

## Provider 구조

```text
AiReviewProvider
├─ OpenAiReviewProvider
└─ RuleBasedReviewProvider
```

현재 설정 뼈대는 아래처럼 잡는다.

```text
AiReviewProviderSelector
└─ OPENAI_API_KEY 존재 여부에 따라 OPENAI 또는 RULE_BASED 선택
```

## 현재 적용한 설정

`backend/src/main/resources/application.yml`에 아래 설정을 추가했다.

```yaml
app:
  ai-review:
    enabled: ${AI_REVIEW_ENABLED:true}
    provider: ${AI_REVIEW_PROVIDER:AUTO}
    openai:
      api-key: ${OPENAI_API_KEY:}
      model: ${OPENAI_MODEL:gpt-4.1-mini}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com/v1/responses}
      temperature: ${OPENAI_TEMPERATURE:0.2}
    rule-based:
      enabled: ${AI_REVIEW_RULE_BASED_ENABLED:true}
    limits:
      max-questions-per-wrong-answer: ${AI_REVIEW_MAX_QUESTIONS_PER_WRONG_ANSWER:3}
      max-questions-per-session: ${AI_REVIEW_MAX_QUESTIONS_PER_SESSION:10}
      max-user-answer-length: ${AI_REVIEW_MAX_USER_ANSWER_LENGTH:700}
```

## Provider 옵션

### AUTO

기본값이다.

```text
OPENAI_API_KEY가 있으면 OpenAI 사용
OPENAI_API_KEY가 없으면 Rule-Based 사용
```

개발과 시연 모두에 가장 안전하다.

### OPENAI

OpenAI 사용을 우선한다.

단, 키가 없으면 서비스 장애가 아니라 Rule-Based로 안전하게 떨어지도록 한다.

### RULE_BASED

비용 없이 고정 꼬리질문과 해설만 사용한다.

```powershell
$env:AI_REVIEW_PROVIDER="RULE_BASED"
```

## 로컬 실행 설정

### 비용 없이 실행

아무 설정도 하지 않으면 기본적으로 `AUTO`다. `OPENAI_API_KEY`가 없으므로 Rule-Based로 동작한다.

명시적으로 고정하려면:

```powershell
$env:AI_REVIEW_PROVIDER="RULE_BASED"
```

### OpenAI로 시연

발표나 면접 준비 때만 API 키를 넣는다.

```powershell
$env:OPENAI_API_KEY="sk-..."
$env:AI_REVIEW_PROVIDER="AUTO"
$env:OPENAI_MODEL="gpt-4.1-mini"
```

이 상태로 백엔드를 실행하면 OpenAI provider를 선택한다.

## 모델 선택

### 추천 기본값: gpt-4.1-mini

이 기능은 긴 창작보다 짧은 튜터링, 답변 평가, JSON 응답 안정성이 중요하다. 그래서 기본 모델은 `gpt-4.1-mini`로 잡는다.

장점:

- 꼬리질문 생성에 충분한 품질
- 답변 평가에 안정적
- JSON 형태 응답에 적합
- 비용과 품질 균형이 좋음

### 비용 최소화: gpt-4o-mini

비용을 더 줄이고 싶으면 아래처럼 바꿀 수 있다.

```powershell
$env:OPENAI_MODEL="gpt-4o-mini"
```

단, 개념 평가 품질은 `gpt-4.1-mini`보다 약할 수 있다.

## 비용 절감 정책

AI 호출은 최대한 줄인다.

- 맞힌 문제는 AI 복습 대상에서 제외
- 틀린 문제만 대상
- 문제당 최대 3개 꼬리질문
- 세션 전체 최대 10개 질문
- 사용자 답변 길이 최대 700자
- 최종 요약은 세션 종료 시 1회만 생성
- 문제 해설과 정답은 DB 기준으로 제공

## 꼬리질문 정책

문제 하나당 최대 3단계로 제한한다.

1. 원인 확인
   - 왜 이 답을 골랐는지 확인한다.

2. 핵심 개념 확인
   - 사용자가 놓친 개념을 직접 묻는다.

3. 적용 판단
   - 실제 상황에 적용할 수 있는지 확인한다.

사용자가 충분히 이해하면 조기 종료한다. 계속 어려워하면 더 질문하지 않고 개념 요약으로 전환한다.

## Rule-Based Fallback 방식

Rule-Based는 AI 없이도 동작해야 한다.

문제마다 아래 데이터를 가진다.

```json
{
  "conceptTags": ["JPA", "N+1", "fetch join"],
  "explanation": "N+1은 연관 데이터를 지연 로딩하면서 추가 쿼리가 반복되는 문제입니다.",
  "followUpQuestions": [
    "N+1은 어떤 상황에서 쿼리가 반복해서 발생하나요?",
    "fetch join을 쓰면 SQL 관점에서 무엇이 달라질까요?",
    "목록 조회에서 fetch join을 무조건 써도 괜찮을까요?"
  ]
}
```

사용자가 답하면 간단히 키워드 포함 여부로 이해도를 판정한다.

```text
핵심 키워드 2개 이상 포함 -> UNDERSTOOD
핵심 키워드 1개 포함 -> PARTIAL
핵심 키워드 없음 -> NEEDS_REVIEW
```

정교하진 않지만 무료로 안정적인 시연이 가능하다.

## OpenAI Provider 방식

OpenAI 사용 시에는 반드시 구조화된 JSON으로 받는다.

예상 응답:

```json
{
  "evaluation": "PARTIAL",
  "feedback": "N+1이 쿼리 반복 문제라는 점은 이해했지만 원인을 트랜잭션과 혼동하고 있습니다.",
  "nextQuestion": "N+1은 트랜잭션 때문에 발생하나요, 연관 로딩 방식 때문에 발생하나요?",
  "weaknessTags": ["JPA", "N+1", "lazy loading"],
  "shouldContinue": true
}
```

## 시스템 프롬프트

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

## 멘토에게 보여줄 정보

멘토에게 AI 대화 전문을 기본 노출하지 않는다. 대신 요약만 보여준다.

- 테스트 점수
- 약점 태그
- AI 복습 완료 여부
- 이해도 평가
- 3~5줄 요약
- 멘토 추천 질문 2~3개

예시:

```text
약점 태그:
JPA, N+1, fetch join

이해도:
부분 이해

요약:
멘티는 N+1이 쿼리 반복 문제라는 점은 이해했지만,
fetch join이 어떤 방식으로 연관 데이터를 함께 조회하는지는 아직 불명확합니다.
트랜잭션과 N+1의 원인을 혼동하는 경향이 있습니다.

멘토 추천 질문:
1. N+1은 트랜잭션 때문에 생기는 문제일까요, 연관 로딩 때문에 생기는 문제일까요?
2. fetch join을 쓰면 SQL 관점에서 무엇이 달라질까요?
3. 목록 조회에서 fetch join을 무조건 써도 괜찮을까요?
```

## 구현 순서

1. AI 설정 추가
   - 완료: `AiReviewProperties`
   - 완료: `AiReviewProviderSelector`
   - 완료: `application.yml` 설정

2. 문제 데이터 확장
   - `Question` 또는 별도 테이블에 `area`, `conceptTags`, `explanation`, `followUpQuestions` 추가

3. 복습 세션 테이블 추가
   - `ai_review_sessions`
   - `ai_review_messages`

4. Rule-Based provider 구현
   - API 비용 없이 먼저 완성

5. OpenAI provider 구현
   - `OPENAI_API_KEY`가 있을 때만 사용
   - JSON 응답 파싱 실패 시 Rule-Based로 fallback

6. 프론트 화면 추가
   - 결과 화면에 `AI 복습 시작`
   - 복습 대화 화면
   - 완료 요약 화면

7. 멘토 화면 연결
   - 매칭 상세에서 AI 요약 표시

## 포트폴리오 설명 문장

면접에서 이렇게 설명하면 좋다.

```text
코스별 진단 테스트에서 틀린 문제를 기반으로 AI 복습 튜터를 설계했습니다.
OpenAI API를 사용해 꼬리질문과 이해도 평가를 생성하고,
API 키가 없거나 장애가 발생할 경우 rule-based fallback으로 동작하도록 구성했습니다.
또한 멘토가 전체 대화 로그를 읽지 않아도 되도록 약점 태그와 요약을 따로 저장해 멘토링 방향 설정에 활용했습니다.
```

## 현재 설정된 파일

- `backend/src/main/resources/application.yml`
- `backend/src/main/java/com/devmatch/config/AiReviewProperties.java`
- `backend/src/main/java/com/devmatch/service/ai/AiReviewProviderSelector.java`
- `backend/src/main/java/com/devmatch/service/ai/AiReviewProviderType.java`
