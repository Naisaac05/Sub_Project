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
