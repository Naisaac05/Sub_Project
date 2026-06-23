---
type: spec
category: rag
status: active
updated: 2026-06-22
description: "RAG 응답 품질 4.5 달성, 실제 Ollama fallback, 합성 Shadow 검증 설계"
---

# RAG 품질·fallback·Shadow 검증 설계

## 목표

50문항 Shadow 평가의 평균 응답 품질을 4.5/5 이상으로 높이고, 기존 설치 Ollama 모델의 실제 fallback 생성과 `SHADOW_MODE=true` 합성 트래픽을 검증한다.

## 제약

- Hugging Face 모델과 신규 Ollama 모델을 다운로드하지 않는다.
- 기존 approved 카드의 검색 필드와 카드 ID는 변경하지 않는다.
- 실제 운영 트래픽 대신 로컬 합성 Shadow를 사용하며, 실운영 미검증 상태는 별도로 유지한다.
- 커밋하지 않고 현재 브랜치 작업 트리에만 저장한다.

## 설계

1. 50문항 평가에서 4점 이하인 카드의 `CONCEPT_DEFINITION.content`만 구체적인 한국어 설명으로 보강한다.
2. 평가기는 카드별 점수와 저점 카드 ID를 보고서에 명시하고 평균 4.5 기준을 자동 판정한다.
3. 실제 fallback 평가는 approved 카드가 답하지 않는 코스 내 질의를 기존 `exaone3.5:2.4b`에 전달한다.
4. 합성 Shadow는 Fast Path hit, 검색 miss, off-topic, Ollama fallback 표본을 실행해 라우팅과 관측 로그를 기록한다.
5. 최종 readiness는 기술 검증과 실운영 검증을 분리해 판정한다.

## 성공 기준

- 평균 응답 품질 4.5/5 이상
- 실제 Ollama 응답이 비어 있지 않고 한국어·주제 관련성·완결성 기준 통과
- 합성 Shadow Top1 95% 이상, Fast Path 90% 이상, fallback 10% 이하
- 관련 없는 Fast Path hit 0건
- 실운영 트래픽 미검증 상태가 문서에 명시됨
