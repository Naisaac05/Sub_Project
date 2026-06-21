---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "AI review free-question 특정 의문사 질문이 정의 템플릿으로 비응답 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# AI review free-question 특정 의문사 질문이 정의 템플릿으로 비응답

- 발생 일시: 2026-05-20
- 영역: ai (Python free-question intent/fast-path)
- 심각도: medium

## 증상

"equals를 재정의하면 무엇을 함께 재정의해야 해?"라고 물으면, 정답 기준인
`hashCode` 언급 없이 일반 정의("equals는 객체의 논리적 동등성을 비교하기 위해
재정의하는 메서드다.")만 돌아옴. 질문 의도(무엇을 함께 해야 하는가)를 무시한
비응답(non-responsive) 답변.

실측 평가에서 `answer_contains_required_keywords`가 0.8333(6건 중 1건 실패)로 드러남.

## 원인

`classify_free_question`의 `DEFINITION_MARKERS`에 `"무엇"`이 포함돼 있어,
**목적격 의문사 `"무엇을"`이 들어간 질문도 `concept_definition`으로 분류**됨
([ai/app/workflow/intent.py](ai/app/workflow/intent.py)).

그 결과 `resolve_lightweight_answer`의 `generated_card_fast_path`가
매칭된 카드의 `핵심 설명`(=정의)을 **질문과 무관하게 반환**함
([ai/app/workflow/lightweight_answers.py:250-252](ai/app/workflow/lightweight_answers.py)).
즉 "무엇을 함께 ~해야"(특정 내용·행위를 묻는 질문)가 정의 fast-path로 오라우팅됨.

## 해결 방법

`intent.py`에 `specific_guidance` intent를 추가
([ai/app/workflow/intent.py](ai/app/workflow/intent.py)):
`"무엇을"/"무엇과"/"무엇이랑"/"무엇부터"/"뭘"`(단, `의미`/`개념`이 없을 때)을
정의가 아닌 "특정 내용 질문"으로 분류. 이 intent는 lightweight 허용 집합
`{concept_definition, comparison, related_concept, practical_application}`에 없으므로
fast-path를 자동으로 건너뛰고, RAG로 검색된 카드를 근거로 LLM이 직접 답하게 됨
(`resolve_lightweight_answer`는 손대지 않음).

검증:
- `python scripts/evaluate_lightweight_rag.py --real`(Ollama 실생성):
  `answer_contains_required_keywords` 0.8333 → **1.0**, `intent_accuracy` 0.9706 무회귀,
  `workflow_context_accuracy` 1.0 유지.
- 실측: equals 질문이 `route=rag_generation`, `model=qwen3:1.7b`로 라우팅되어
  "equals를 재정의하면 hashCode를 함께 재정의해야 한다…"로 정상 응답.
- 전체 unittest 111건: 기존 실패 3건 외 신규 실패 없음.

## 재발 방지 / 메모

- 구조적 잔여 리스크: lightweight fast-path(정적 `_ANSWERS` + `generated_card`)는
  모두 **'정의'를 반환**한다. 그래서 comparison/related/practical 등 비정의 intent에도
  정의를 줄 여지가 남아 있음. 이번엔 `specific_guidance`만 우회 처리했고,
  비정의 intent 전반의 fast-path 적정성은 후속 과제.
- 평가의 `--real` 모드를 추가했지만, in-domain 개념 질문 대부분은 여전히 fast-path
  (lightweight-template)로 답해 **모델 자체 품질은 부분 측정**됨. 모델 품질을 제대로
  재려면 fast-path를 타지 않는 질문(specific_guidance/비정의/카드 없는 개념) 케이스를 늘려야 함.
- `required_keywords`는 substring 검사라 표기 변형(띄어쓰기 등)에 취약. distinctive
  토큰만 최소로 쓸 것.
- 관련: [2026-05-20 게이트 약한 겹침](2026-05-20-ai-review-workflow-context-gate-weak-overlap.md),
  [2026-05-16 RAG generic token false positive](2026-05-16-ai-review-rag-generic-token-false-positive.md).
