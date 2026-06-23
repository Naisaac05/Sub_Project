---
type: spec
category: rag
status: active
updated: 2026-06-22
description: "approved RAG 근거 기반 fallback 생성과 fail-closed 안전 응답 설계"
---

# 근거 기반 fallback 안전 설계

## 문제

free-question의 v2 Fast Path miss는 현재 RAG context를 비운 채 Ollama를 호출한다. 소형 모델은 최신 API를 다른 프레임워크와 혼동하거나 미완결 응답을 반환할 수 있다.

## 설계

1. approved 카드만 fallback 근거 후보로 사용한다.
2. Lexical 점수 8 이상이며 1·2위 차이가 1 이상인 카드만 신뢰한다.
3. 근거가 없으면 Ollama를 호출하지 않고 검증 근거 부족 안전 응답을 반환한다.
4. 근거가 있으면 `CONCEPT_DEFINITION`만 프롬프트에 제공한다.
5. 생성 결과는 한국어, 완결 문장, 질문 주제, 근거 핵심 토큰 겹침을 검증한다.
6. 하나라도 실패하면 생성 원문을 버리고 안전 응답으로 대체한다.
7. 동기 및 스트리밍 경로가 같은 정책과 품질 플래그를 사용한다.

## 성공 기준

- 근거 없는 miss에서 generator 호출 0회
- 근거가 있는 miss에서 prompt에 approved evidence 포함
- 품질 실패 응답이 사용자에게 노출되지 않음
- 안전 응답 route는 `grounded_fallback_safe_response`
- 신규 모델 다운로드 없음
