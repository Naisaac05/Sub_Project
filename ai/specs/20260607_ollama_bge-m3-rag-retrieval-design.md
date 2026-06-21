---
type: spec
category: rag
status: active
updated: 2026-06-18
description: "Ollama BGE-M3 RAG Retrieval Design 상세 요구사항 및 기능 동작 명세서"

---

# Ollama BGE-M3 RAG Retrieval Design

## Goal

`ai/evals/retrieval_poc/EXPERIMENT.md`에서 가장 높은 정확도를 보인 Ollama `bge-m3` 단독 의미 검색을 실제 AI Review RAG 검색 경로에 연결한다.

이번 단계는 RAG 검색만 구현하고 검증한다. 질문 의도 분류 Embed 연결은 RAG 검색 검증이 끝난 뒤 별도 명세와 구현 단계로 진행한다.

## Experiment Basis

검색 PoC는 한국어 기술 Q&A 지식 카드 검색에서 `bge-m3` 단독 검색이 BM25 및 hybrid보다 일관되게 높은 recall@1을 기록했다고 결론 내렸다.

| 평가 데이터 | BM25 | BGE-M3 | Hybrid |
|---|---:|---:|---:|
| 기존 11개 카드 | 65.1% | 95.3% | 65.1% |
| 목적성 질의 | 65.0% | 97.5% | 85.0% |
| 30개 DENSE corpus | 약 72% | 약 92% | 약 76% |
| 30개 DIVERSE corpus | 약 78% | 약 97% | 약 82% |

따라서 정상 동작 시 검색 결과는 BGE-M3 순위만 사용한다. BM25나 lexical 결과를 BGE 점수와 결합하지 않는다.

## Scope

### In Scope

- Ollama `/api/embeddings`와 `bge-m3`를 사용하는 임베딩 클라이언트
- 지식 카드 임베딩 생성과 프로세스 메모리 캐시
- 정규화된 코사인 유사도 기반 단독 의미 검색기
- `AI_REVIEW_RAG_RETRIEVER=bge` 검색기 선택 경로
- Ollama 또는 임베딩 모델 장애 시 lexical fallback
- BGE와 lexical의 서로 다른 점수 범위를 고려한 컨텍스트 허용 정책
- 단위 테스트, 회귀 테스트, PoC 재평가, 실제 Ollama 연결 스모크 테스트
- 운영 설정과 사용 방법 문서화

### Out of Scope

- 질문 의도 분류 Embed 연결
- BM25/BGE hybrid 검색을 기본 경로로 변경
- reranker 도입
- ChromaDB 또는 별도 벡터 데이터베이스 도입
- 디스크 기반 임베딩 캐시
- 생성 모델 호출 및 프롬프트 구조 변경

## Selected Approach

전용 `OllamaBgeRetrieverAdapter`가 Ollama BGE-M3 의미 검색을 수행한다. 정상 상태에서는 BGE 결과만 반환하며, 임베딩 호출이 실패한 경우에만 기존 `LexicalRetrieverAdapter`로 전환한다.

이 방식은 다음 이유로 선택한다.

- PoC에서 검증한 검색 구조와 동일하다.
- 현재 `ChromaBgeRetrieverAdapter`가 요구하는 ChromaDB 및 `sentence-transformers` 의존성을 피한다.
- 현재 환경에서 확인된 로컬 `BAAI/bge-m3` 로딩 호환성 문제의 영향을 받지 않는다.
- Ollama 장애가 AI Review 전체의 RAG 검색 중단으로 이어지는 것을 막는다.

Lexical fallback은 검색 품질을 높이기 위한 hybrid 결합이 아니다. BGE 경로가 실행 불가능할 때만 사용하는 가용성 보호 장치다.

## Architecture

### Ollama Embedding Client

새 임베딩 클라이언트는 Ollama HTTP 통신만 담당한다.

- 기본 주소: 기존 `OLLAMA_BASE_URL` 설정 사용
- 기본 모델: `bge-m3`
- API: `/api/embeddings`
- 입력: 한 번에 하나의 문자열
- 출력: 유한한 숫자로 구성된 비어 있지 않은 벡터
- 검증 실패 또는 HTTP 실패: 명시적인 임베딩 예외 발생

생성 모델 호출과 임베딩 호출은 API 계약과 오류 처리 방식이 다르므로 별도 단위로 유지한다. 검색기는 주입 가능한 embed 함수에 의존하여 Ollama 없이도 단위 테스트할 수 있어야 한다.

### Ollama BGE Retriever

`OllamaBgeRetrieverAdapter`는 다음 책임만 가진다.

1. 카드 로더에서 현재 지식 카드를 읽는다.
2. 검색에 사용할 카드 문자열을 일관된 형식으로 만든다.
3. 각 카드의 정규화 임베딩을 준비한다.
4. 질의 임베딩을 생성하고 정규화한다.
5. 코사인 유사도 내림차순으로 정렬한다.
6. 상위 `limit`개 결과를 `RetrievedContext`로 반환한다.

정상 BGE 결과의 metadata에는 `retriever=ollama_bge_m3`와 사용 모델명을 기록한다.

### Card Embedding Cache

카드 임베딩은 프로세스 메모리에 캐시하여 매 질의마다 같은 카드를 다시 임베딩하지 않는다.

캐시 키는 최소한 카드 식별자와 검색 대상 문자열의 내용 해시를 포함한다. 같은 카드의 내용이 바뀌면 새 임베딩을 생성하고, 삭제된 카드의 캐시는 다음 카드 목록 동기화 시 제거한다.

질의 임베딩은 캐시하지 않는다. 현재 범위에서는 카드 임베딩 재사용이 가장 큰 비용을 줄이며, 질의 캐시는 별도 정책과 크기 제한 없이 도입하지 않는다.

### Retriever Selection

`select_retriever_adapter()`는 다음 선택 값을 지원한다.

- `lexical`: 기존 lexical 검색
- `hybrid:*`: 기존 호환 경로 유지
- `bge`, `bge_m3`, `semantic`: Ollama BGE-M3 단독 검색과 lexical 장애 fallback

기본 선택값은 기존 호환성을 위해 `lexical`로 유지한다. BGE를 사용할 환경은 `AI_REVIEW_RAG_RETRIEVER=bge`를 명시한다. 이를 통해 Ollama나 `bge-m3`가 준비되지 않은 환경에서 예기치 않은 외부 호출이 발생하지 않는다.

기존 `ChromaBgeRetrieverAdapter`와 hybrid 경로는 제거하지 않지만, 이번 기능의 정상 검색 경로에는 포함하지 않는다.

## Data Flow

```text
AI Review workflow
  -> retrieve_context(query, limit=3)
  -> select_retriever_adapter()
  -> OllamaBgeRetrieverAdapter
      -> load current knowledge cards
      -> reuse or create normalized card embeddings
      -> create normalized query embedding
      -> cosine similarity ranking
      -> return top contexts with BGE metadata
  -> source-aware context acceptance
  -> accepted contexts enter answer generation
```

임베딩 단계에서 오류가 발생하면 데이터 흐름은 다음과 같이 전환된다.

```text
OllamaBgeRetrieverAdapter
  -> embedding failure
  -> warning log
  -> LexicalRetrieverAdapter
  -> attach fallback metadata
  -> existing lexical score acceptance
```

## Score Acceptance Policy

현재 워크플로는 모든 검색 결과에 `MIN_WORKFLOW_CONTEXT_SCORE = 5.0`을 적용한다. 이 값은 lexical 점수에는 의미가 있지만, 약 `0.0`부터 `1.0` 범위인 정규화 코사인 유사도 결과를 전부 제거한다.

워크플로는 단일 숫자 비교 대신 검색기 출처를 확인하는 공통 컨텍스트 허용 함수를 사용한다.

- `retriever=ollama_bge_m3`: `AI_REVIEW_BGE_MIN_SCORE` 이상일 때 허용
- lexical 및 기존 검색기: 기존 `MIN_WORKFLOW_CONTEXT_SCORE = 5.0` 정책 유지
- BGE 장애 후 lexical fallback 결과: lexical 점수 정책 적용

`AI_REVIEW_BGE_MIN_SCORE`의 기본값은 `0.50`으로 고정한다. 구현 검증에서는 golden dataset의 정답 및 비관련 질의 점수 분포를 확인한다. 기본값 변경이 필요하면 평가 결과와 변경 이유를 문서에 기록하고 테스트 기대값도 함께 변경한다.

이 허용 함수는 초기 검색 노드, fallback 재검색, 승인 후보 선택 등 현재 직접 `MIN_WORKFLOW_CONTEXT_SCORE`를 비교하는 모든 RAG 소비 경로에서 동일하게 사용한다.

## Error Handling And Observability

다음 오류는 BGE 검색 실패로 분류하고 lexical fallback을 실행한다.

- Ollama 연결 실패 및 timeout
- `bge-m3` 모델 부재 또는 Ollama 오류 응답
- 잘못된 JSON 응답
- 비어 있거나 숫자가 아닌 임베딩
- 카드 임베딩과 질의 임베딩의 차원 불일치
- 코사인 유사도 계산 중 검증 오류

fallback 시 경고 로그에는 이벤트명, 모델명, 오류 종류를 남긴다. 원문 지식 카드나 사용자 질문 전체는 로그에 남기지 않는다.

fallback 결과 metadata에는 다음을 기록한다.

- `fallback_from=ollama_bge_m3`
- `fallback_reason=<안전하게 정규화한 오류 종류>`

fallback 자체도 실패하면 빈 결과를 반환하며, RAG 컨텍스트 없이 답변하는 기존 degraded-mode 동작을 유지한다. 검색 오류가 API 요청 전체를 실패시키면 안 된다.

## Configuration

| 환경 변수 | 기본값 | 역할 |
|---|---|---|
| `AI_REVIEW_RAG_RETRIEVER` | `lexical` | `bge` 설정 시 Ollama BGE-M3 검색 활성화 |
| `AI_REVIEW_EMBEDDING_MODEL` | `bge-m3` | Ollama 임베딩 모델 |
| `AI_REVIEW_EMBEDDING_TIMEOUT_SECONDS` | `10` | 단일 임베딩 요청 timeout |
| `AI_REVIEW_BGE_MIN_SCORE` | `0.50` | BGE 결과의 워크플로 허용 최소 코사인 유사도 |
| `OLLAMA_BASE_URL` | 기존 프로젝트 기본값 | Ollama 서버 주소 |

`AI_REVIEW_VECTOR_ENABLED`는 기존 Chroma 경로의 설정으로 남긴다. 새 Ollama BGE 경로의 활성화 여부에는 사용하지 않는다.

## Testing Strategy

구현은 테스트 주도 개발로 진행한다. 각 동작의 실패 테스트를 먼저 작성하고, 해당 테스트를 통과시키는 최소 구현을 추가한다.

### Unit Tests

- `bge`, `bge_m3`, `semantic` 선택값이 Ollama BGE 검색기를 선택한다.
- 주입한 가짜 임베딩으로 의미상 가까운 카드가 1위가 된다.
- 정규화 코사인 유사도 순위와 반환 점수가 올바르다.
- 같은 카드 내용은 여러 질의에서 한 번만 임베딩된다.
- 카드 내용이 바뀌면 해당 카드 임베딩을 다시 생성한다.
- 비어 있는 카드 목록과 비어 있는 질의를 안전하게 처리한다.
- HTTP 오류, timeout, 잘못된 벡터, 차원 불일치 시 lexical fallback이 실행된다.
- fallback metadata가 기록된다.
- reranker를 전달했을 때 기존 인터페이스 계약을 유지한다.

### Workflow And Regression Tests

- `0.50` 이상의 BGE 결과는 점수가 `5.0` 미만이어도 워크플로 컨텍스트로 허용된다.
- `0.50` 미만의 BGE 결과는 제외된다.
- fallback lexical 결과에는 기존 `5.0` 기준이 적용된다.
- 모든 RAG 소비 경로가 같은 허용 함수를 사용한다.
- 기존 lexical, BM25, hybrid, Chroma 관련 테스트가 계속 통과한다.
- RAG를 비활성화하거나 Ollama를 실행하지 않은 기존 개발 환경이 계속 동작한다.

### Evaluation And Live Verification

1. 관련 RAG 및 workflow 단위 테스트를 실행한다.
2. 전체 AI 테스트 스위트를 실행한다.
3. 지식 카드 lint를 실행한다.
4. Ollama와 `bge-m3`가 준비된 환경에서 실제 임베딩 스모크 테스트를 실행한다.
5. `ai/evals/retrieval_poc` 평가를 다시 실행하여 BGE recall@1이 기존 보고서 결과와 크게 어긋나지 않는지 확인한다.
6. Ollama를 중단하거나 잘못된 모델을 설정해 lexical fallback을 검증한다.

환경 또는 corpus 변경으로 PoC 수치를 정확히 재현하지 못하면 실패 질의와 차이를 기록하고 원인을 분석한다. 테스트 통과만으로 검색 품질 검증을 완료한 것으로 간주하지 않는다.

## Rollout

1. 개발 및 테스트 환경에서 `AI_REVIEW_RAG_RETRIEVER=bge`를 명시하여 기능을 검증한다.
2. 실제 Ollama 스모크 테스트와 retrieval 평가가 통과한 뒤 대상 실행 환경에 같은 설정을 적용한다.
3. 배포 후 warning 로그의 fallback 빈도를 확인한다.
4. fallback이 반복되면 Ollama 상태, 모델 설치 여부, timeout 설정을 먼저 확인한다.

기능 활성화 전제 조건은 Ollama 서버 실행과 `bge-m3` 모델 설치다. 전제 조건이 충족되지 않아도 lexical fallback 덕분에 AI Review 요청은 계속 처리되어야 한다.

## Acceptance Criteria

- `AI_REVIEW_RAG_RETRIEVER=bge`에서 Ollama `bge-m3` 의미 검색 결과가 실제 workflow 컨텍스트로 사용된다.
- 정상 상태에서는 BM25나 lexical 결과가 BGE 순위에 결합되지 않는다.
- 같은 카드 내용은 프로세스 수명 동안 반복 임베딩되지 않는다.
- 카드 내용 변경은 캐시에 반영된다.
- Ollama 및 임베딩 오류는 요청 실패 대신 관찰 가능한 lexical fallback으로 처리된다.
- BGE와 lexical 결과에 각각 올바른 점수 허용 정책이 적용된다.
- 관련 단위 테스트, 전체 AI 테스트, 지식 카드 lint가 통과한다.
- 실제 Ollama 연결과 retrieval PoC 재평가 결과가 문서화된다.
- 질문 의도 분류 Embed 코드는 이번 단계에서 변경되지 않는다.
