# AI review missing approved evidence preempted Ollama

- 발생 일시: 2026-06-25
- 영역: ai
- 심각도: high

## 증상
승인 카드가 없는 자유 질문에서 Ollama가 답변하기 전에 `현재 승인된 학습 근거만으로는 정확한 답변을 제공하기 어렵습니다` 안전 문구가 바로 반환됐다. 사용자는 `N+1이 뭐야?`, `Spring Security가 뭐지?`처럼 현재 문제와 관련된 질문을 했지만, 답변 품질 검증 이전에 fallback으로 빠지는 것처럼 보였다.

## 원인
`AI_REVIEW_GROUNDED_FALLBACK_ENABLED=true`일 때 승인 근거 카드가 없으면 `select_grounded_evidence()`가 `None`을 반환했고, 이 값을 곧바로 `_grounded_safe_response(..., "missing_approved_evidence")`로 처리했다. 이 때문에 `missing_approved_evidence`가 “Ollama 생성 후 후보 등록 사유”가 아니라 “Ollama 호출 전 차단 사유”로 동작했다.

## 해결 방법
승인 근거가 없을 때 즉시 fallback하지 않고 `missing_approved_evidence` 품질 플래그만 추가한 뒤 Ollama 생성 경로를 계속 타도록 바꿨다: `ai/app/workflow/nodes.py:130`.

스트리밍 경로도 같은 정책으로 맞춰, 화면에서 쓰는 SSE 응답도 승인 근거 없음만으로 사전 차단하지 않게 했다: `ai/app/workflow/runner.py:388`.

회귀 테스트를 추가해 `CQRS가 뭐야?`처럼 승인 카드가 없는 질문이 `grounded_fallback_safe_response`가 아니라 `generation`으로 가는지 검증했다: `ai/tests/test_workflow_runner.py:257`.

## 재발 방지 / 메모
`missing_approved_evidence`는 fallback 사유가 아니라 후보 등록 및 관측용 플래그로 취급해야 한다. fallback은 Ollama 답변이 비어 있거나, 한국어가 아니거나, 주제를 놓치거나, 명백히 품질 검증을 통과하지 못할 때만 적용한다.

수정 후 conda Python 런타임에서 직접 `run_review_workflow(mode="free-question", user_answer="CQRS가 뭐야?")`를 호출해 `route=generation`, `fallback_used=False`, `quality_flags=["course_scope_applied", "missing_approved_evidence"]`를 확인했다.

단, 2026-06-25 현재 로컬 `8001` 포트에는 오래된 Python AI 프로세스 `PID 31416`이 계속 떠 있었다. 화면에 반영하려면 해당 프로세스를 종료하고 `uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload`로 재시작해야 한다.
