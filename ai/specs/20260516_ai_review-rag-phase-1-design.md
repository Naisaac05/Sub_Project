---
type: spec
category: rag
status: active
updated: 2026-06-18
description: "LangGraph 기반 AI 리뷰 RAG Phase 1 데이터 플로우 및 설계서"

---

# AI 리뷰 RAG Phase 1 설계

## 목표

최종 AI 리뷰 로컬 RAG + LangGraph 아키텍처의 첫 기반 단계를 구현합니다. Python AI 서버 모듈화, 파일 기반 지식 구조, 개념 카드 린트, 의존성을 허용하는 RAG 검색 골격을 포함합니다.

## 실행 순서

이 단계는 `2026-05-16-ai-review-lightweight-phase-47-design.md`보다 먼저 실행해야 합니다.

고정 순서:

```text
Phase 1: rag-phase-1
→ Phase 4.7: lightweight-phase-47
```

두 단계를 동시에 실행하지 마세요. 두 단계 모두 `ai/app/service.py`를 수정하므로 병렬 실행하면 서비스 오케스트레이션 변경을 덮어쓰거나 무효화할 수 있습니다.

## 범위

이 단계는 최종 아키텍처 문서의 구현 단계 1~3.5를 다룹니다.

- 현재 Python AI 서비스를 역할별 모듈로 분리합니다.
- 기본 소형 모델을 `qwen3:1.7b`로 바꾸고 `qwen3:4b-q4_K_M`을 폴백으로 유지합니다.
- 개념, 승인 QA, 후보, 프롬프트를 위한 `ai/app/knowledge/` 폴더 구조를 추가합니다.
- 초기 Java/Spring 개념 카드를 추가합니다.
- 개념 카드 메타데이터와 필수 섹션을 검증하는 린트 스크립트를 추가합니다.
- 개념 카드를 읽고 Markdown 섹션으로 분할하며 한국어를 토큰화하고 로컬 키워드 폴백 검색을 수행하는 RAG 검색 패키지를 추가합니다.
- 해당 패키지가 아직 설치되지 않아도 서버 시작이 실패하지 않도록 LangChain, Chroma, BM25, `kiwipiepy`, `flashrank`의 선택적 통합 지점을 추가합니다.

## 제외 범위

- 이 단계에서는 LangGraph 워크플로를 실행하지 않습니다.
- Spring Boot DTO를 마이그레이션하지 않습니다.
- 후보 승인 DB/API 또는 관리자 UI를 추가하지 않습니다.
- 평가기 골든 세트를 추가하지 않습니다.
- Redis, Prometheus, Langfuse, PGVector를 통합하지 않습니다.

## 아키텍처

기존 FastAPI 엔드포인트의 요청·응답 계약을 유지합니다. `service.py`는 `/api/review/*`의 오케스트레이션 진입점으로 남지만, Ollama 전용 코드는 `app/ollama/client.py`, 프롬프트 구성은 `app/prompts.py`, 답변 정리·폴백 도우미는 `app/validation/text.py`, RAG 도우미는 `app/rag/`로 이동합니다.

RAG 계층은 의도적으로 선택적 의존성을 허용합니다. Markdown 개념 카드와 내장 토크나이저·검색 구현을 기반으로 작은 `retrieve_context(query, limit)` 인터페이스를 제공합니다. 이후 LangChain 등 무거운 패키지를 설치하면 엔드포인트 코드를 바꾸지 않고 어댑터 동작을 확장할 수 있습니다.

## 파일

- `ai/app/schemas.py`: update default model and response metadata fields.
- `ai/app/service.py`: keep endpoint orchestration, delegate prompt, Ollama, and validation helpers.
- `ai/app/ollama/client.py`: Ollama request and warm-up client.
- `ai/app/prompts.py`: compact prompt builders.
- `ai/app/validation/text.py`: Korean ratio, sentence limiting, PII masking, fallback responses.
- `ai/app/rag/documents.py`: concept card loader and section splitter.
- `ai/app/rag/retriever.py`: dependency-tolerant retrieval interface.
- `ai/scripts/lint_knowledge_cards.py`: concept/approved QA lint command.
- `ai/tests/`: unit tests for linting, RAG loading, retrieval, and schema defaults.
- `ai/app/knowledge/`: initial knowledge files and prompts.
- `ai/requirements.txt`: keep only base FastAPI runtime dependencies.
- `ai/requirements-rag.txt`: keep optional RAG/LangGraph dependencies for later phases.

## 테스트

새 테스트 의존성을 추가하기 전에도 동작을 검증할 수 있도록 Python `unittest`를 사용합니다.

기본 명령:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python -m unittest discover -s tests
```

린트 스크립트도 통과해야 합니다.

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python scripts/lint_knowledge_cards.py
```

## 적용 참고사항

이 단계는 기존 API를 유지하면서 LangGraph와 평가기 작업을 위한 코드 기반을 준비합니다. 선택적 RAG 의존성이 아직 설치되지 않아도 서버는 시작되며 폴백 검색기를 테스트와 로컬 개발에 사용할 수 있습니다. Chroma/LangChain 기반 검색을 활성화할 때만 `requirements-rag.txt`를 설치하세요.
