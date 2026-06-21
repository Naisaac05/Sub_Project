# AI 시스템 문서 인덱스

**2026-06-18 기준 최종 정리 완료**

AI 리뷰 시스템, RAG, LangGraph, Ollama 기반 파이프라인 등 **모든 AI 관련 문서**를 체계적으로 정리한 메인 인덱스입니다.  
AI 담당자가 빠르게 원하는 문서를 찾을 수 있도록 구성했습니다.

## 🌟 핵심 문서 Top 5
가장 자주 참조되거나 시스템의 뼈대가 되는 핵심 문서들입니다. 처음 오셨다면 이 문서들부터 읽어보세요.

1. 📄 **[AI Review Local RAG + LangGraph Architecture (최종 구현용)](./architecture/20260618_ai_review-local-rag-langgraph-architecture-final.md)**  
   *LangGraph 기반 Local RAG 아키텍처 최종 설계서*
2. 📄 **[AI Ollama Streaming Baseline](./architecture/20260526_ai_ollama-streaming-baseline-qwen3-1-7b.md)**  
   *Qwen3-1.7B 기반 Ollama Streaming Baseline 설계 및 구조*
3. 📄 **[로컬 AI 실행 가이드 (Ollama)](./reports/20260618_ollama_local-ai-run-guide.md)**  
   *로컬 AI를 사용하는 방법 가이드라인*
4. 📄 **[빠르고 정확한 답변 구조 설계](./specs/20260601_ai_review-fast-accurate-architecture-design.md)**  
   *RAG 기반 DevMatch AI 복습 아키텍처 설계서*
5. 📄 **[RAG Cards v2 Isolated Migration](./plans/20260612_rag_cards-v2-isolated-migration.md)**  
   *RAG v2 마이그레이션 전략 및 도입 계획*
## 📌 주요 섹션

### 1. 설계 및 아키텍처
- **[analysis/](./analysis/current_model_selection.md)**  
  현재 AI 시스템 모델 현황, 비교 평가 내용 및 아키텍처 최종 선택 이유 분석 리포트
- **[architecture/](./architecture/index.md)**  
  LangGraph 기반 RAG 아키텍처, Inference Orchestration, Provider 전략, Fast Path 설계 등 **핵심 시스템 설계 문서** 모음

### 2. 계획 및 상세 설계
- **[plans/](./plans/index.md)**  
  Phase별 작업 계획, RAG v2 마이그레이션, Ollama 도입 계획 등 **앞으로 할 일** 관련 문서
- **[specs/](./specs/index.md)**  
  기능별 상세 설계서 (Semantic Evaluation, Approval Fast Path, Candidate Approval v2 등)

### 3. 평가 및 실험
- **[evals/](./evals/index.md)**  
  Intent Detection, Retrieval, ExaOne Live E2E 등 **성능 평가 및 POC 리포트**
- **[experiments/](./experiments/index.md)**  
  프롬프트 버전 비교, 모델 테스트 등 **실험 결과** 모음
- **[reports/](./reports/index.md)**  
  구현 리포트, 최적화 보고서, Streaming 개선 등 실제 적용 결과

### 4. 지식 베이스
- **[knowledge/](./knowledge/index.md)**  
  RAG에서 사용하는 Java, Spring, Concept Card 등 **지식 베이스 마크다운 파일** 모음

### 5. 변경 이력 및 트러블슈팅
- **[changes/](./changes/index.md)**  
  기술 변경 이력 (RAG v1 → v2, 모델 교체 등)
- **[troubleshooting/](./troubleshooting/index.md)**  
  Ollama 타임아웃, RAG 검색 실패, V2 버그 해결 등 **실제 발생했던 문제와 해결 기록**

### 6. 기타
- **[prompts/](./prompts/index.md)** — 시스템 프롬프트, 리뷰어 프롬프트 라이브러리
- **[pipeline/](./pipeline/index.md)** — AI 파이프라인, 워크플로우, 오케스트레이션
- **[testing/](./testing/index.md)** — AI 테스트 및 Smoke Test 관련 문서

## 빠른 링크
- **[README.md](./README.md)** — AI 시스템 전체 개요 및 아키텍처 요약
- **[ROADMAP.md](./ROADMAP.md)** — AI 개발 로드맵 (있을 경우)

---

**문서 이용 팁**
- 파일명은 대부분 `YYYYMMDD_주제_설명.md` 형식입니다.
- 각 문서 상단에 YAML Frontmatter(`type`, `category`, `status`)가 들어있어 검색이 용이합니다.
- 자주 사용하는 문서는 즐겨찾기(Obsidian, VS Code 등) 추천

필요한 문서를 빠르게 찾으려면 **Ctrl + K** (또는 Command + K)로 검색하세요.
