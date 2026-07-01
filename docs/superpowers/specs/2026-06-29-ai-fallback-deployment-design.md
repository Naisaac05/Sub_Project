# AI Fallback Deployment Design

## 목표

AWS 운영 배포에서 승인된 RAG 개념은 즉시 근거 답변하고, 승인 카드가 없는 질문은 Ollama가 답변하며, 실제 모델 오류나 품질 실패에만 fallback을 노출한다.

## 확인된 원인

- `hashCode가 뭐지?`는 `java-equals`와 `java-hashmap`에 각각 1점으로 동점이며, 승인 근거 기준 8점과 후보 간격 1점을 통과하지 못한다.
- Spring 백엔드 기본 모델은 `qwen3:1.7b`/`qwen3:4b-q4_K_M`이지만 배포 런북은 `exaone3.5:2.4b`만 설치한다.
- EC2에서 `bge-m3` pull이 완료되지 않았다.
- 카드가 없을 때 Ollama를 호출하는 경로는 이미 존재하며 `ai/tests/test_workflow_runner.py`에서 검증한다. 따라서 전역 threshold 완화나 grounded fallback 비활성화는 하지 않는다.

## 설계

1. `java-hashcode` 승인 카드를 추가해 `hashCode` 정의 질문에 단일 강한 후보를 제공한다.
2. Spring과 Python의 운영 기본 생성 모델을 `exaone3.5:2.4b`로 통일하고 `.env.prod.example`에 명시한다.
3. 승인 카드 질문, 카드 없는 질문, 모델 목록을 검사하는 배포 검증 명령을 런북에 기록한다.
4. `hashCode` 회귀 테스트와 기존 카드 없는 Ollama 호출 테스트로 정책을 고정한다.
5. 실제 비밀값은 `.env.prod`에만 두고 추적되는 example 파일에는 남기지 않는다.

## 성공 조건

- `hashCode가 뭐지?`가 `java-hashcode`를 선택하고 fallback 없이 답한다.
- 승인 카드 86개의 canonical 정의 질문이 모두 Fast Path hit다.
- 카드가 없는 `CopyOnWriteArrayList` 질문이 Ollama를 호출하고 정상 한국어 답변이면 fallback 없이 반환된다.
- EC2의 모델 목록에 `exaone3.5:2.4b`, `bge-m3`가 존재한다.
- AI 내부 스모크 테스트 응답의 `fallback_used`와 `model_used`가 기대값을 만족한다.

