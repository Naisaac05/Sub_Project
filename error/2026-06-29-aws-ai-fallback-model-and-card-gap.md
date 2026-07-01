# AWS AI fallback 모델·카드·의도 분류 불일치

- 발생 일시: 2026-06-29
- 영역: ai / backend / docker / AWS
- 심각도: high

## 증상

AWS 배포에서 `hashCode가 뭐지?`처럼 승인된 Java 학습 범위의 질문을 해도 실제 설명 대신 "현재 승인된 학습 근거만으로는 정확한 답변을 제공하기 어렵습니다"라는 안전 fallback이 반환됐다. RAG 카드가 없는 질문도 Ollama가 답하지 않는 것처럼 보였다.

## 원인

네 조건이 겹쳤다.

1. `hashCode`는 `java-equals`, `java-hashmap` 본문에만 존재하고 독립 카드가 없었다. lexical 검색에서 두 카드가 각각 1점으로 동점이라 근거 기준 8점과 margin 기준 1점을 통과하지 못했다.
2. `hashCode가 뭐지?`의 `뭐지`가 명백한 정의 질문 정규식에 없었다. 임베딩 분류 결과가 `concept_definition/related`가 되어 Fast Path에서 `unsupported_intent`로 탈락했다: `ai/app/workflow/embedding_intent.py:273`.
3. 백엔드는 기본적으로 qwen 모델을 요청했지만 AWS 런북은 `exaone3.5:2.4b`만 설치했다. 첫 모델 호출 실패·재시도·timeout 가능성이 생겼다: `backend/src/main/resources/application.yml:94`, `backend/src/main/resources/application.yml:103`.
4. LangGraph는 Fast Path hit 뒤 일반 validation은 건너뛰었지만 `cache_answer`의 semantic judge는 계속 실행했다. 승인 payload에 retrieval context가 없다는 이유로 `contradiction_suspected`가 붙어 최종 답변을 fallback으로 다시 덮었다: `ai/app/workflow/semantic_gate.py:11`.

카드가 없을 때 Ollama를 호출하는 정책 자체는 이미 구현되어 있었다. `missing_approved_evidence`는 관측 플래그이며 즉시 fallback 사유가 아니다: `ai/tests/test_workflow_runner.py:324`.

## 해결 방법

- `java-hashcode` 승인 카드를 추가해 hashCode 정의와 equals 계약을 독립 근거로 제공했다: `ai/app/knowledge/concepts_v2/java/java-hashcode.json:1`.
- 명백한 정의 질문 정규식에 `뭐지`, `뭐죠`를 추가했다: `ai/app/workflow/embedding_intent.py:273`.
- 회귀 테스트로 intent와 근거 선택을 고정했다: `ai/tests/test_embedding_intent.py:30`, `ai/tests/test_grounded_fallback.py:57`.
- Spring Python/Ollama 기본 모델과 운영 템플릿을 `exaone3.5:2.4b`로 통일했다: `backend/src/main/resources/application.yml:94`, `.env.prod.example:28`.
- 승인된 Fast Path payload는 semantic judge의 재평가를 건너뛰도록 했다: `ai/app/workflow/semantic_gate.py:11`, `ai/tests/test_semantic_evaluation.py:11`.
- 추적 파일에 잘못 들어간 실제 비밀값을 제거하고 example을 placeholder로 복구했다.

## 재발 방지·메모

- 배포 전에 모든 승인 카드의 canonical 정의 질문이 Fast Path hit인지 검사한다. 이번 변경 후 목표는 86/86이다.
- 카드 hit와 카드 없는 Ollama 질문을 별도 스모크 테스트로 검사한다.
- Ollama 모델 이름은 Spring 설정, Python 설정, `.env.prod`, 모델 pull 명령에서 동일해야 한다.
- `fallback_used`, `route`, `model_used`, `matched_concept_id`를 함께 확인해야 서버 장애와 품질 게이트를 구분할 수 있다.
- 실제 비밀값은 `.env.prod`에만 저장한다. 이미 노출된 JWT·AI 서비스 토큰·DB 비밀번호는 교체해야 한다.
