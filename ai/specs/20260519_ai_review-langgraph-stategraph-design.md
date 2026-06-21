---
type: spec
category: langgraph
status: active
updated: 2026-06-18
description: "AI 리뷰 워크플로우 제어를 위한 LangGraph StateGraph 명세서"

---

# AI 리뷰 LangGraph StateGraph 설계

## 목표

`run_review_workflow()`와 Spring/FastAPI 응답 계약을 안정적으로 유지하면서 AI 리뷰 워크플로를 LangGraph 호환 일반 Python 시퀀스에서 실제 `StateGraph` 실행 경로로 전환합니다.

## 아키텍처

`ai/app/workflow/graph.py`가 그래프 구성을 담당합니다. 빌더 내부에서만 `langgraph.graph.StateGraph`를 가져오므로 선택적 RAG 의존성이 없는 기본 경량 환경도 실행할 수 있습니다. LangGraph가 설치돼 있으면 `run_review_workflow()`가 컴파일된 그래프를 실행하고, 없으면 실행기가 의존성 허용 폴백으로 현재의 순차 동작을 유지합니다.

그래프 노드는 현재 워크플로 이름을 그대로 따르며 명시적인 종료 부수 효과 노드를 추가합니다.

1. `retrieve_context`
2. `rule_evaluate`
3. `generate_answer`
4. `validate_answer`
5. `confidence_gate`
6. `fallback_answer`
7. `cache_answer`
8. `candidate_save`

`generate_answer`는 이미 생성기 예외를 상태에 기록하고 템플릿 폴백으로 라우팅합니다. Phase 18에서는 `error_state`와 `dead_end_state`의 그래프 수준 메타데이터를 추가해 운영자가 정상 폴백과 그래프·노드 실패 또는 불완전 상태를 구분할 수 있게 합니다.

## 데이터 흐름

그래프는 `ReviewWorkflowState`를 받습니다. 기존 노드 함수는 현재 동작과 동일하게 이 상태를 변경해 반환합니다. `cache_answer`는 성공한 모델 답변을 메모리 답변 캐시에 기록합니다. `candidate_save`는 `AI_REVIEW_AUTO_CANDIDATES_PATH`를 읽고 수집 규칙을 평가한 뒤 JSONL 후보를 추가하고 워크플로 상태에 `candidate_id`를 저장합니다. 이후 실행기는 최종 상태로 `AiGenerateResponse`를 만들고 구조화된 관측성 이벤트를 첨부합니다.

## 오류 및 막다른 상태 처리

기존에 처리되는 모델 생성 오류 외의 노드 예외는 그래프 노드 래퍼가 포착합니다. 래퍼는 `state.error`를 기록하고 `state.route`를 `error_state`로 설정한 뒤 `fallback_answer`로 진행합니다. 막다른 상태는 그래프 실행에서 답변이 생성되지 않았거나 신뢰도·검증 단계를 건너뛴 경우를 뜻합니다. 막다른 상태 가드는 `state.route`를 `dead_end_state`로 설정하고 상태를 `fallback_answer`에 전달합니다.

## 테스트

그래프 빌더 구조, 공개 실행기 호환성, 생성기 예외 폴백, 후보 저장 노드 동작, 그래프를 사용할 수 없을 때의 폴백을 테스트합니다. 기존 워크플로·평가기 테스트는 회귀 테스트 모음으로 유지합니다.
