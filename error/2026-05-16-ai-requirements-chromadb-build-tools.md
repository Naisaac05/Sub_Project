# AI requirements ChromaDB Build Tools install failure

- 발생 일시: 2026-05-16
- 영역: ai
- 심각도: medium

## 증상

`python -m pip install -r requirements.txt` 실행 중 `chromadb`의 하위 의존성인 `chroma-hnswlib` wheel 빌드가 실패했다.

핵심 오류:

```text
error: Microsoft Visual C++ 14.0 or greater is required.
Failed building wheel for chroma-hnswlib
```

## 원인

Phase 1 구현은 RAG/LangGraph 의존성을 optional integration point로만 준비하는 범위였지만, 기본 서버 실행용 `requirements.txt`에 `chromadb`, `sentence-transformers`, `flashrank`, `kiwipiepy` 같은 무거운 선택 의존성을 함께 넣었다.

Windows + Python 3.13 환경에서는 `chroma-hnswlib`의 호환 wheel이 없으면 로컬 C++ 빌드가 필요하고, Microsoft C++ Build Tools가 없으면 설치가 실패한다. 현재 코드의 fallback retriever는 이 선택 의존성 없이 동작하므로 기본 requirements에 포함될 필요가 없었다.

## 해결 방법

기본 FastAPI 서버 실행 의존성과 선택 RAG 의존성을 분리했다.

- `ai/requirements.txt:1` — `fastapi`, `uvicorn`, `pydantic`만 유지
- `ai/requirements-rag.txt:1` — Chroma/LangChain/RAG 계열 의존성을 별도 파일로 이동
- `docs/superpowers/specs/2026-05-16-ai-review-rag-phase-1-design.md:43` — optional dependency 설치 정책 반영
- `docs/superpowers/plans/2026-05-16-ai-review-rag-phase-1.md:26` — 계획 문서의 requirements 설명 수정

## 재발 방지 / 메모

기본 `requirements.txt`는 서버 부팅에 필요한 최소 의존성만 둔다. Chroma 기반 retrieval을 실제로 켤 때는 `requirements-rag.txt`를 별도로 설치하고, Windows에서는 Python 버전별 prebuilt wheel 여부 또는 Microsoft C++ Build Tools 설치 여부를 먼저 확인한다.

