---
type: experiment
category: rag
status: active
updated: 2026-06-18
description: "v1 vs v2 프롬프트 비교 결과 (RAG 포함) 모델 성능 평가 및 POC 실험 결과 분석"

---

# v1 vs v2 프롬프트 비교 결과 (RAG 포함)

- 모델: `qwen3:4b-q4_K_M`
- 샘플 수: 5
- 생성 시각: 2026-05-27 17:43:19
- RAG 포함: 검색 (limit=3, score≥5.0) → 프롬프트 컨텍스트 주입

## 평가 방법
각 샘플별로 v1/v2 출력을 나란히 보고, 아래 기준으로 직접 채점하세요:
- **자연스러움**: 한국어 톤이 자연스러운가? (영어 단어 섞임/번역체 없음)
- **진단 적합성**: 학습자의 상태에 맞는 꼬리질문인가?
- **형식 준수**: 3문장 이내 + 물음표로 끝남
- **컨텍스트 활용**: RAG 카드 내용을 답변에 잘 녹였는가?

---

## 샘플 followup-001-dont-know — 학습자가 모른다고 답함

**문제**: JPA의 N+1 문제가 무엇인가요?
**학습자 답변**: 모르겠어요

**RAG 검색 결과**:
- ✅ `spring-n-plus-one` (N+1 문제) — score: 20.5
- ✅ `spring-fetch-join` (Fetch Join) — score: 15.0
- ⚠️ 점수미달 `auto-review-hashcode` (hashCode) — score: 1.0

### v1 출력 (36166ms)
```
N+1 문제는 연관 엔티티를 조회할 때 추가 쿼리가 발생하는 문제입니다. 학습자가 이 개념을 모르고 있습니다. N+1 문제는 어떤 상황에서 발생하는지 설명해주세요.
```

### v2 출력 (62063ms)
```
[오류] TimeoutError: timed out
```

### 채점 (직접 작성)
- 자연스러움: v1 _/5, v2 _/5
- 진단 적합성: v1 _/5, v2 _/5
- 컨텍스트 활용: v1 _/5, v2 _/5
- 메모: 

---

## 샘플 followup-002-partial — 부분적으로 맞음

**문제**: 리스트와 튜플의 가장 큰 차이는 무엇인가요?
**학습자 답변**: 튜플이 리스트보다 빠르다고 들어서 속도 차이라고 골랐어요

**RAG 검색 결과**:
- ⚠️ 점수미달 `auto-review-hashcode` (hashCode) — score: 1.0

### v1 출력 (15115ms)
```
정답은 가변성 차이입니다. 튜플은 리스트보다 가변성이 적고 불변입니다. why did you choose option 2?
```

### v2 출력 (50886ms)
```
속도 차이도 사실은 리스트와 튜플의 특성에서 나온 결과일 뿐이에요. 그보다 더 중요한 차이가 하나 있는데, 하나는 값을 바꿀 수 있고 다른 하나는 바꿀 수 없는 점이에요. 그럼 튜플은 값을 바꿀 수 있는지, 아니면 바꿀 수 없는지 한 번 생각해볼까요?
```

### 채점 (직접 작성)
- 자연스러움: v1 _/5, v2 _/5
- 진단 적합성: v1 _/5, v2 _/5
- 컨텍스트 활용: v1 _/5, v2 _/5
- 메모: 

---

## 샘플 followup-003-wrong-concept — 잘못된 개념 적용

**문제**: equals를 재정의할 때 함께 재정의해야 하는 것은?
**학습자 답변**: 객체를 문자열로 표현할 때 같이 쓰니까 toString도 같이 재정의해야 한다고 생각했어요

**RAG 검색 결과**:
- ✅ `java-equals` (equals) — score: 10.5
- ✅ `auto-review-hashcode` (hashCode) — score: 7.5
- ⚠️ 점수미달 `spring-fetch-join` (Fetch Join) — score: 3.0

### v1 출력 (28874ms)
```
equals를 재정의하면 hashCode도 함께 재정의해야 하는 이유를 설명해줄 수 있니?
```

### v2 출력 (62044ms)
```
[오류] TimeoutError: timed out
```

### 채점 (직접 작성)
- 자연스러움: v1 _/5, v2 _/5
- 진단 적합성: v1 _/5, v2 _/5
- 컨텍스트 활용: v1 _/5, v2 _/5
- 메모: 

---

## 샘플 followup-004-confident-wrong — 자신감 있게 틀린 답

**문제**: fetch join을 컬렉션에 사용할 때 가장 주의해야 할 점은?
**학습자 답변**: fetch join을 쓰면 데이터베이스에서 정렬되니까 결과 순서가 보장된다고 확신해요

**RAG 검색 결과**:
- ✅ `spring-fetch-join` (Fetch Join) — score: 11.0
- ⚠️ 점수미달 `spring-n-plus-one` (N+1 문제) — score: 2.0

### v1 출력 (29794ms)
```
fetch join은 데이터베이스에서 정렬되지만, 결과 순서는 항상 보장되지 않습니다. 페이징이 메모리에서 처리됨은 fetch join 사용 시 주의해야 할 점입니다. 페이징이 메모리에서 처리되는 이유를 설명해주세요.
```

### v2 출력 (59536ms)
```
학습자의 설명은 결과 순서가 보장된다고 생각한 부분이 있어요. 하지만 fetch join은 데이터베이스에서 정렬되不一定한 경우가 많아요. 그렇다면 페이징이 메모리에서 처리되는 이유는 무엇일까요?
```

### 채점 (직접 작성)
- 자연스러움: v1 _/5, v2 _/5
- 진단 적합성: v1 _/5, v2 _/5
- 컨텍스트 활용: v1 _/5, v2 _/5
- 메모: 

---

## 샘플 followup-005-second-step — 두 번째 꼬리질문 단계

**문제**: @ControllerAdvice의 주요 역할은?
**학습자 답변**: 이전 꼬리질문에서 예외를 한 곳에 모아 처리한다는 건 이해했어요. 그런데 @ExceptionHandler랑 어떻게 다른지 헷갈려요

**RAG 검색 결과**:
- ✅ `java-backend-controlleradvice` (@ControllerAdvice) — score: 16.0
- ⚠️ 점수미달 `auto-review-hashcode` (hashCode) — score: 1.0
- ⚠️ 점수미달 `auto-review-recyclerview` (RecyclerView) — score: 1.0

### v1 출력 (26767ms)
```
@ExceptionHandler는 특정 컨트롤러만에서 예외를 처리하는 어노테이션 whereas @ControllerAdvice는 전역적으로 여러 컨트롤러에서 공통 예외를 처리하는 어노테이션인가요?
```

### v2 출력 (54967ms)
```
학습자의 설명은 전역 예외 처리에 대한 이해가 있는 것 같아요. 그런데 @ControllerAdvice와 @ExceptionHandler의 관계에 대해 더 구체적으로 생각해볼까요?
```

### 채점 (직접 작성)
- 자연스러움: v1 _/5, v2 _/5
- 진단 적합성: v1 _/5, v2 _/5
- 컨텍스트 활용: v1 _/5, v2 _/5
- 메모: 

---
