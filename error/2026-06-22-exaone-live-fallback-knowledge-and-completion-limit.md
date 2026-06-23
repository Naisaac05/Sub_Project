# EXAONE 실제 fallback의 최신 지식 혼동과 응답 잘림

## 증상

로컬 `exaone3.5:2.4b` 실제 fallback 평가에서 필수 표본 2건과 최신 API 진단 표본 2건이 모두 품질 게이트를 통과하지 못했다. 답변은 한국어로 생성됐지만 일부가 256토큰에서 잘렸고, `StructuredTaskScope`를 Spring `@Async`로 설명하거나 Error Boundary에 존재하지 않는 데코레이터를 제시했다.

## 원인

소형 모델이 최신·세부 API 사실을 안정적으로 회상하지 못하고, 4~6문장·500자 이내 지시도 따르지 않아 최대 생성 토큰까지 장황하게 출력했다. 질의를 안정된 개념으로 바꿔도 사실 오류 또는 미완결 응답이 반복돼 프롬프트만으로 해결되지 않았다.

## 해결 방법

실제 응답 원문과 실패 사유를 보고서에 보존하고, 필수 fallback 품질 게이트를 fail-closed로 유지했다. 신규 모델은 설치하지 않았으며 전환 판정을 `NOT_READY`로 유지한다.

- `ai/scripts/evaluate_live_ollama_fallback.py:24`
- `ai/reports/live_ollama_fallback_2026-06-22.json:1`
- `ai/scripts/evaluate_synthetic_shadow_traffic.py:88`

## 재발 방지·메모

근본 원인은 남아 있다. 전환 전에는 검증된 RAG 근거를 fallback 프롬프트에 제공하거나, 더 정확한 기존 허용 모델을 선정하고 동일 표본을 재검증해야 한다.
