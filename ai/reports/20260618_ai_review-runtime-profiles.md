---
type: report
category: inference
status: active
updated: 2026-06-18
description: "운영 배포 환경에서 어떻게 켜고 조절하는지 정리한다"

---

# AI Review Runtime Profiles

이 문서는 DevMatch AI 리뷰 파이프라인을 노트북 MVP, 고성능 PC, 운영 배포 환경에서 어떻게 켜고 조절하는지 정리한다.

## 기본 원칙

- 기본 생성 모델은 `qwen3:1.7b`다.
- `qwen3:4b-q4_K_M`은 기본 모델이 아니라 fallback 모델이다.
- 현재 노트북 기준에서는 동시 Ollama 생성 요청을 1개로 제한한다.
- small 모델은 상시 유지하고, fallback 4B 모델은 필요할 때만 30분 유지한다.
- vector/reranker dependency는 lazy import라서 설치되지 않은 노트북에서도 BM25 low-resource 경로로 동작해야 한다.

## Low Resource Profile

현재 i5-10210U, 16GB RAM 노트북에서 기본으로 권장하는 프로필이다.

```powershell
$env:AI_REVIEW_RETRIEVER_PROFILE="hybrid:low_resource"
$env:AI_REVIEW_MODEL="qwen3:1.7b"
$env:AI_REVIEW_FALLBACK_MODEL="qwen3:4b-q4_K_M"
$env:AI_REVIEW_OLLAMA_MAX_CONCURRENT="1"
$env:AI_REVIEW_OLLAMA_SMALL_KEEP_ALIVE="-1"
$env:AI_REVIEW_OLLAMA_FALLBACK_KEEP_ALIVE="30m"
$env:AI_REVIEW_MAX_TOKENS="256"
$env:AI_REVIEW_NUM_CTX="1024"
```

## Ollama BGE-M3 Semantic RAG Profile

이 프로필은 Chroma/SentenceTransformer 기반 vector store가 아니라, 로컬 Ollama `/api/embeddings`의 `bge-m3`를 직접 호출해서 knowledge card를 semantic-only cosine ranking으로 정렬한다.

```powershell
$env:AI_REVIEW_RAG_RETRIEVER="bge"
$env:AI_REVIEW_EMBEDDING_MODEL="bge-m3"
$env:AI_REVIEW_EMBEDDING_TIMEOUT_SECONDS="10"
$env:AI_REVIEW_BGE_MIN_SCORE="0.50"
```

운영 보장:

- Normal path: Ollama `bge-m3` semantic-only ranking.
- BM25 and lexical scores are not fused into BGE ranking.
- Failure path: lexical fallback with `fallback_from`, `fallback_reason` metadata and warning log.
- Default remains lexical unless `AI_REVIEW_RAG_RETRIEVER=bge` is set.
- Approved/generated knowledge cards remain the retrieval corpus.

검증 명령:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python -m unittest tests.test_ollama_embeddings tests.test_rag_retriever tests.test_workflow_runner -v
python scripts/lint_knowledge_cards.py
python scripts/smoke_ollama_bge_retriever.py
python evals/retrieval_poc/evaluate.py
```

특징:

- BM25 중심 검색을 먼저 사용한다.
- `kiwipiepy`, `chromadb`, `sentence-transformers`, `flashrank`가 없어도 실행된다.
- RAG context는 concept card를 짧게 유지하고 top-k를 낮게 잡는 쪽이 안정적이다.
- 브라우저와 IDE를 많이 켠 상태에서는 fallback 4B 상시 로딩을 피한다.

## High Performance Profile

더 좋은 PC나 서버에서 bge-m3 + Chroma + BM25 + kiwipiepy + flashrank를 모두 연결할 때 쓰는 프로필이다.

```powershell
$env:AI_REVIEW_RETRIEVER_PROFILE="hybrid:high_performance"
$env:AI_REVIEW_VECTOR_STORE="chroma"
$env:AI_REVIEW_EMBEDDING_MODEL="BAAI/bge-m3"
$env:AI_REVIEW_RERANKER="flashrank"
$env:AI_REVIEW_TOKENIZER="kiwipiepy"
$env:AI_REVIEW_OLLAMA_MAX_CONCURRENT="1"
```

인덱싱 및 smoke test:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python scripts\reindex_knowledge.py --chroma --smoke-query "N+1 fetch join"
```

현재 노트북에 `chromadb`가 없으면 Chroma 단계는 `skipped`로 끝나는 것이 정상이다. manifest changed-only reindex는 계속 실행된다.

## Production Profile

운영에서는 Spring Boot와 FastAPI가 같은 내부 네트워크 안에서 service token을 공유한다.

```powershell
$env:AI_REVIEW_SERVICE_TOKEN="<secret>"
$env:AI_REVIEW_RETRIEVER_PROFILE="hybrid:high_performance"
$env:AI_REVIEW_OLLAMA_MAX_CONCURRENT="1"
$env:AI_REVIEW_OLLAMA_SMALL_KEEP_ALIVE="-1"
$env:AI_REVIEW_OLLAMA_FALLBACK_KEEP_ALIVE="30m"
```

운영 로그/메트릭 수집 대상:

- `ai_review.workflow_completed`
- `ai_review.python_event`
- `fallback_used`
- `retrieval_miss`
- `candidate_captured`
- `ai_review.candidate_backlog`

## Verification Commands

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python -m unittest tests.test_lightweight_evaluator tests.test_promotion_workflow tests.test_chroma_reindex tests.test_rag_retriever tests.test_workflow_runner -v
python scripts\evaluate_lightweight_rag.py

cd C:\Users\User\Desktop\Sub_Project\backend
.\gradlew.bat test

cd C:\Users\User\Desktop\Sub_Project\frontend
npm.cmd run build
```

## Ollama BGE-M3 Intent Profile

The production free-question workflow uses `bge-m3` embedding similarity for intent classification before RAG retrieval.

```powershell
$env:AI_REVIEW_EMBEDDING_MODEL="bge-m3"
$env:AI_REVIEW_INTENT_MIN_SIMILARITY="0.43"
$env:AI_REVIEW_INTENT_MIN_MARGIN="0.005"
```

- Runtime classification does not call the legacy rule classifier.
- Prototype vectors are lazily initialized and cached in `app/vectorstore/intent_centroids.json` and process memory.
- Warm requests use one intent embedding call per free question.
- Embedding failure and low-confidence classification map to `UNKNOWN/general_question`.
- The general-question fallback does not attempt rule-based recovery.
