# AI review chat fallback and unicode rendering

- 발생 일자: 2026-05-19
- 영역: ai / backend / frontend
- 심각도: medium

## 증상

AI 복습 채팅 화면에서 응답 시간 라벨이 `3.4\uCD08 \uB3D9\uC548 생각함`처럼 raw unicode escape로 노출됐다.

짧은 개념 질문인 `Git tag는 뭔데?`, `idempotent 설계가 뭔데?`, 네트워크 자동 연결 질문에 대해 실제 주제를 설명하지 못하고 `정의, 쓰이는 상황, 헷갈리는 개념과의 차이`를 보라는 일반 fallback 문장이 반복됐다.

## 원인

프론트 채팅 렌더링에서 JSX 텍스트 노드에 `\uCD08 \uB3D9\uC548` escape가 문자열 리터럴이 아닌 일반 텍스트로 들어가 React가 그대로 화면에 출력했다. 관련 파일: `frontend/src/app/tests/results/[id]/review/page.tsx:800`

AI workflow fallback은 free-question의 주제를 추출하더라도 공통 템플릿만 반환했고, Git tag / idempotent / network auto-connect처럼 자주 묻는 짧은 개념 질문을 주제별로 보완하지 못했다. 관련 파일: `ai/app/workflow/nodes.py:374`

Spring fallback 경로도 Python/Ollama가 모두 실패했을 때 사용자 질문을 fallback 생성에 넘기지 않아 동일한 일반 답변으로 떨어질 수 있었다. 관련 파일: `backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java:463`

## 해결 방법

프론트의 raw unicode escape 텍스트를 실제 한국어 텍스트로 바꿔 응답 시간 라벨이 `초 동안 생각함`으로 렌더링되게 했다. 관련 파일: `frontend/src/app/tests/results/[id]/review/page.tsx:800`

Python workflow에 주제별 fallback을 추가해 Git tag, idempotent 설계, network auto layout / auto connect 질문은 일반 템플릿보다 먼저 구체 답변을 반환하게 했다. 회귀 테스트도 추가했다. 관련 파일: `ai/app/workflow/nodes.py:374`, `ai/tests/test_workflow_runner.py:374`

Spring fallback에도 사용자 질문을 전달하고 동일한 주제별 fallback을 추가했다. Python/Ollama가 모두 비어 있어도 일반 문구가 반복되지 않도록 테스트를 추가했다. 관련 파일: `backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java:463`, `backend/src/test/java/com/devmatch/service/ai/RuleBasedAiReviewServiceTest.java:149`

## 재발 방지 / 메모

JSX 텍스트 노드에서는 `\uXXXX` escape를 직접 쓰지 않는다. escape가 필요하면 JS 문자열 리터럴 안에서만 사용하거나 실제 UTF-8 텍스트로 저장한다.

free-question fallback은 "주제를 못 알아들었을 때의 마지막 안전망"이어야 하며, 자주 나오는 짧은 개념 질문은 테스트로 고정한다.

검증 결과:

- `python -m unittest tests.test_workflow_runner.WorkflowRunnerTest.test_topic_specific_fallbacks_explain_common_short_questions tests.test_workflow_runner.WorkflowRunnerTest.test_low_quality_generated_answer_reports_missing_topic_flag -v`
- `.\gradlew.bat test --tests com.devmatch.service.ai.RuleBasedAiReviewServiceTest.submitAnswer_usesTopicSpecificFallbackWhenAiClientsAreUnavailable`
- `npm.cmd run build`

참고: backend 전체 `.\gradlew.bat test`는 MySQL 연결 거부로 실패했다. 실패 원인은 `Communications link failure`와 `Unable to determine Dialect without JDBC metadata`이며, 이번 AI review fallback 수정 단위 테스트는 통과했다.
