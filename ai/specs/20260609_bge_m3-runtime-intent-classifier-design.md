---
type: spec
category: inference
status: active
updated: 2026-06-18
description: "Ollama 기반 BGE-M3 런타임 의도 분류기(Intent Classifier) 구조 설계서"

---

# BGE-M3 런타임 의도 분류기 설계

## 목표

프로덕션의 규칙 기반 자유 질문 의도 분류를 Ollama `bge-m3` 임베딩 분류기로 교체합니다. 테스트는 프로덕션과 동일한 분류기 계약을 검증해야 합니다. 규칙 기반 분류는 격리된 PoC 비교 대상으로만 유지합니다.

## 런타임 흐름

1. 학습자 질문을 해석합니다.
2. Ollama `bge-m3`로 질문을 임베딩합니다.
3. 10개 클래스 의도 분류 체계의 캐시된 의도 프로토타입 벡터와 비교합니다.
4. 선택한 10개 클래스 레이블을 기존 `FreeQuestionIntent` 워크플로 계약으로 변환합니다.
5. 변환된 의도를 RAG 정책, 프롬프트 선택, 답변 형식, 관측성에 사용합니다.

지원하는 레이블은 다음과 같습니다.

- `ANSWER_REASON`
- `WRONG_ANSWER_REASON`
- `CONCEPT_DEFINITION`
- `COMPARISON`
- `EXAMPLE_REQUEST`
- `PRACTICAL_USAGE`
- `DEBUG_OR_ERROR`
- `FOLLOW_UP`
- `OFF_TOPIC`
- `UNKNOWN`

## 실패 및 낮은 신뢰도 정책

분류기는 기존 규칙 기반 분류기를 폴백으로 호출해서는 안 됩니다.

Ollama 실패, 타임아웃, 잘못된 벡터, 설정 임계값보다 낮은 유사도, 불충분한 점수 차이가 발생하면 다음을 반환합니다.

```text
UNKNOWN -> FreeQuestionIntent(
  intent="general_question",
  rag_policy="original_context_mixed",
  context_dependent=False,
  sub_intent="general"
)
```

## 성능

의도 프로토타입 벡터는 지연 계산하고 모델명·프로토타입 해시와 함께 디스크에 캐시한 뒤 메모리에도 캐시합니다. 워밍업 후에는 분류할 질문마다 임베딩 요청 한 번만 필요합니다. 분류기는 런타임에 평가 데이터셋을 읽거나 NumPy에 의존하지 않습니다.

## 호환성 매핑

- 답변 이유 의도는 `wrong_answer_explanation`과 원래 문제 컨텍스트를 사용합니다.
- 정의, 비교, 예시, 실무, 디버깅 의도는 `concept_definition`과 최신 질문을 사용합니다.
- 후속 질문은 `follow_up`과 원래 컨텍스트를 사용합니다.
- 주제 이탈과 알 수 없음은 `general_question`을 사용합니다.

주제 추출은 별도의 질문 처리 책임으로 유지하며 의도 레이블을 결정해서는 안 됩니다.

## 검증

- 임베딩 순위, 캐시 재사용, 낮은 신뢰도, 임베딩 실패를 단위 테스트합니다.
- 워크플로가 BGE 분류기를 호출하고 규칙 분류기는 호출하지 않는지 확인합니다.
- 프롬프트·RAG 정책이 매핑된 의도를 받는지 확인합니다.
- 의도, 워크플로, 프롬프트, 임베딩, RAG 집중 테스트를 실행한 뒤 전체 AI 테스트를 실행합니다.
