# Task Tracker - AI Review Prompt Artifact Versioning

| Task | Status | Description |
| --- | --- | --- |
| Task 1: Package Restructuring (`app/prompts`) & Hashing | completed | `app/prompts.py` 패키지화 (`__init__.py`, `registry.py`), 결정론적 SHA-256 해시 함수 구현 및 구 파일 제거 |
| Task 2: State & Result Schema 확장 | completed | `ReviewWorkflowState`, `SemanticJudgeResult`, `GroundingResult` 스키마에 프롬프트 메타데이터 필드 추가 |
| Task 3: Nodes & Runner Integration | completed | `nodes.py` 및 `runner.py`에 버전/해시 추출 연동, 3대 Observability 이벤트 스트림 적재 |
| Task 4: Versioning Tests & Verification | completed | `test_prompt_versioning.py` 작성 및 전체 224개 테스트 100% 통과 확인 |
