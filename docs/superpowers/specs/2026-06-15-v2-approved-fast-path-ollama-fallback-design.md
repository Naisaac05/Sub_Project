# v2 Approved Fast Path와 Ollama Fallback 단일 운영 설계

## 목표

코스별 테스트 예상 질문에 대해 `concepts_v2`의 승인 카드가 답할 수 있으면 즉시 응답하고, 승인 카드가 답하지 못하면 v1 카드나 정적 답변을 거치지 않고 Ollama로 넘긴다.

카드 품질 보강을 계속 확대하는 대신 실제 질문에서 Fast Path가 실패한 경우만 관찰하고 필요한 v2 카드를 보강한다.

## 최종 운영 흐름

```text
free-question
  -> 질문 및 intent 해석
  -> concepts_v2 approved 카드 Fast Path 검색
     -> 승인된 payload + 점수/anchor 통과: 즉시 답변
     -> 실패: Ollama 생성 또는 기존 Ollama 생성 캐시 응답
  -> Fast Path 실패 사유와 Ollama 사용 여부 기록
```

다음 경로는 free-question 운영 흐름에서 제외한다.

- v1 `ai/app/knowledge/concepts` RAG 검색 결과를 Ollama prompt context로 전달
- v1 카드 기반 lightweight/static 답변
- v2 `draft` 카드 검색 또는 payload 사용
- `approved_locked` 상태 도입과 승격 작업

`follow-up`과 기존 평가 모드는 이번 변경 범위에서 제외한다. 해당 모드의 기존 동작은 유지한다.

## 지식 저장소 역할

### 운영 저장소

`ai/app/knowledge/concepts_v2`

- `review.card_status == approved` 카드만 Fast Path 검색에 참여한다.
- 요청 intent에 해당하는 `review.payload_status`도 `approved`여야 한다.
- `draft` 카드는 운영 검색에서 제외한다.
- 현재 승인 카드 61개를 운영 카드 집합으로 사용한다.

### 제거된 저장소

기존 `ai/app/knowledge/concepts` v1 저장소는 삭제한다.

- free-question과 follow-up은 v1 카드를 읽지 않는다.
- v1 비교·검색 전용 스크립트와 오래된 index manifest 항목도 제거한다.
- `concepts_v2`가 유일한 운영 카드 저장소다.

## 런타임 정책

### v2 Fast Path 성공

다음 조건을 모두 만족하면 승인 payload를 즉시 반환한다.

1. Fast Path가 활성화되어 있다.
2. 카드가 v2 운영 승인 집합에 포함된다.
3. 카드 상태가 `approved`이다.
4. 검색 점수와 구체 anchor 검사를 통과한다.
5. 요청 intent에 대응하는 payload 상태가 `approved`이다.
6. 렌더링된 payload가 비어 있지 않다.

응답 메타데이터:

- `route=v2_approved_fast_path`
- `model_used=v2-approved-payload`
- `fallback_used=false`
- v1 검색 context 없음

### v2 Fast Path 실패

`disabled`, `unsupported_intent`, `retrieval_miss`, `score_gate`, `anchor_miss`, `payload_not_approved`, `payload_empty`, `loader_error`를 포함한 모든 실패는 Ollama 경로로 이어진다.

- free-question에서 v1 RAG context를 조회하거나 prompt에 넣지 않는다.
- lightweight/static 답변을 반환하지 않는다.
- 기존 Ollama 생성 결과 캐시는 사용할 수 있다.
- 캐시 miss이면 Ollama를 호출한다.
- Fast Path 실패는 사용자 오류로 처리하지 않는다.

응답 메타데이터에는 다음을 남긴다.

- Fast Path 실패 이유
- 후보 카드 ID가 있으면 해당 ID
- 검색 점수
- 최종 route와 model

## 코드 경계

### v2 검색과 승인 정책

`ai/app/workflow/v2_approved_fast_path.py`

- v2 승인 카드 검색과 payload 렌더링만 담당한다.
- 하드코딩 allowlist를 파일의 실제 승인 상태와 일치시키는 단일 로더로 교체한다.
- v1 카드를 읽지 않는다.

### free-question 오케스트레이션

`ai/app/workflow/nodes.py`

- free-question의 `retrieve_context_node`는 v1 RAG context를 만들지 않는다.
- `generate_answer_node`는 v2 Fast Path 성공 시 즉시 반환한다.
- v2 Fast Path 실패 시 lightweight/static 답변을 건너뛰고 Ollama 생성 경로로 진행한다.
- follow-up과 다른 모드는 기존 정책을 유지한다.

`ai/app/workflow/runner.py`

- streaming 경로도 동일한 분기 순서를 사용한다.
- v2 miss 후 v1/lightweight 응답 없이 Ollama streaming을 수행한다.

### 관찰 가능성

기존 `v2_fast_path_decision` 메타데이터를 유지하고 다음을 기준으로 측정한다.

- v2 Fast Path hit 수
- 실패 이유별 miss 수
- Ollama fallback 수
- 카드 ID별 hit/miss 수

새 카드 자동 생성이나 자동 승인은 하지 않는다. miss 기록은 후속 수동 카드 보강의 입력으로만 사용한다.

## 설정 정책

운영 기본값은 v2 Fast Path serve 모드로 설정한다.

- v2 Fast Path 비활성화 시 free-question은 Ollama로 간다.
- 승인 카드 ID 수동 환경변수는 긴급 차단용 선택 기능으로만 유지한다.
- 설정되지 않은 경우 파일에서 읽은 모든 v2 `approved` 카드를 사용한다.
- shadow 모드는 비교 평가에서만 사용하며 운영 응답에는 사용하지 않는다.

## 오류 처리

- v2 카드 JSON 로드 실패: 해당 요청은 Ollama로 진행하고 loader 실패 사유를 기록한다.
- 승인 payload 누락: Ollama로 진행하고 `payload_not_approved` 또는 `payload_empty`를 기록한다.
- Ollama 실패: 기존 timeout 및 품질 fallback 응답 정책을 유지한다.
- v1 저장소 문제: free-question 운영 경로에 영향을 주지 않아야 한다.

## 테스트 전략

### 단위 테스트

- 승인 v2 카드와 승인 payload가 있으면 Fast Path가 답한다.
- draft 카드와 미승인 payload는 답하지 않는다.
- Fast Path miss 시 Ollama generator가 호출된다.
- Fast Path miss 시 v1 retriever와 lightweight/static resolver가 호출되지 않는다.
- Fast Path loader 오류 시 Ollama generator가 호출된다.
- allowlist 환경변수가 없으면 실제 approved 카드 집합을 사용한다.

### 스트리밍 테스트

- v2 hit은 승인 payload를 stream 응답으로 반환한다.
- v2 miss는 Ollama stream 응답으로 이어진다.
- v2 miss에서 v1/static 응답이 끼어들지 않는다.

### 회귀 테스트

- follow-up 기존 동작을 유지한다.
- approved 카드만 Fast Path 대상이다.
- v1 파일을 제거한 임시 테스트 환경에서도 free-question의 v2 hit와 Ollama fallback이 동작한다.

### E2E 완료 검증

실제 코스 질문 샘플을 실행해 다음을 보고한다.

- 전체 질문 수
- v2 Fast Path hit 수와 비율
- Ollama fallback 수와 비율
- 실패 이유 분포
- 응답 실패 수

## 정리 정책

이번 구현에서 v1 카드와 v1 비교 전용 스크립트는 삭제한다. 과거 v2 배치 보고서와 `concepts_v2` 백업은 유지하며 후속 정리 작업에서 다음으로 분류한다.

- `active`: 운영·검증에 필요한 도구
- `archive`: 과거 마이그레이션·배치·보고서
- `backup`: 복원 전용 데이터

## 완료 기준

1. free-question은 v2 approved Fast Path를 가장 먼저 시도한다.
2. v2 Fast Path 실패 시 v1 RAG와 static/lightweight 답변 없이 Ollama로 진행한다.
3. streaming과 non-streaming이 같은 정책을 사용한다.
4. v1 저장소는 운영 free-question 응답에 사용되지 않는다.
5. 승인 상태와 카드 payload는 이번 전환 과정에서 수정하지 않는다.
6. 관련 단위·스트리밍·회귀 테스트와 코스 질문 E2E가 통과한다.
