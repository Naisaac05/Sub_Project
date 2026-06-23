# 🤖 AI 시스템 전체 개요 (AI Knowledge Base)

## RAG v2 최신 상태 (2026-06-21)

- approved 82장, draft 62장
- 50문항 Shadow Top1 100%, Fast Path 100%, fallback 0%
- 평균 응답 품질 4.52/5와 합성 Shadow 라우팅은 통과했습니다.
- 근거 기반 fallback 운영 Shadow 확장 검증 8/8 통과로 `shadow_readiness=READY`입니다.
- 실제 운영 트래픽 Shadow 미검증으로 serve 전환 판정은 `serve_readiness=NOT_READY`입니다.
- 상세 근거: [v2 전환 준비도 재판정](./specs/20260621_v2-replacement-readiness.md)
- 근거 기반 fallback은 approved 근거가 있을 때만 모델을 호출하며, 품질 실패 원문은 승인 근거 추출 답변으로 복구하고 근거가 없을 때는 안전 응답으로 대체합니다.
- 운영 Shadow 실행 방법은 [operational shadow runbook](./reports/operational_shadow_runbook_2026-06-23.md)을 따릅니다.
- 커밋 전 변경 묶음은 [precommit change sets](./reports/precommit_change_sets_2026-06-23.md)에 정리했습니다.

이 폴더는 프로젝트 내의 모든 AI 관련 기술 문서(아키텍처, 파이프라인, 프롬프트, 트러블슈팅 등)를 모아놓은 중앙 저장소입니다.
LLM, RAG, Ollama, Qwen, Exaone 등 AI/ML 기능과 관련된 모든 작업 내역과 설계가 이곳에서 관리됩니다.

## 📂 현재 문서 구조
- **[전체 인덱스 (index.md)](./index.md)**: 전체 문서 탐색용 트리 구조
- **architecture/**: AI 시스템 전체 설계 및 아키텍처 의사결정 기록
- **pipeline/**: RAG 데이터 수집, 정제, 프롬프트 체인 등 파이프라인 흐름
- **prompts/**: 실제 사용되는 시스템 프롬프트 및 스킬 정의서
- **evals/** & **experiments/**: 모델 성능 평가, 벤치마크, POC 기록
- **troubleshooting/**: AI 모델이나 파이프라인 연동 중 발생한 에러와 해결책 (`by-issue/` 기반 분류)
- **changes/**: AI 관련 대규모 변경 내역

## 📅 문서 관리 현황
- **최종 정리일**: 2026-06-18
- **정리 범위**: 프로젝트 전역에 흩어져 있던 `docs/`, `error/`, `.agent/` 내의 모든 AI 문서를 단일 진실 공급원(SSOT)으로 통합.

## 📖 문서 이용 가이드
1. **파일명 규칙**: `YYYYMMDD_[주제]_[설명].md` 포맷을 따릅니다.
2. **메타데이터 (Frontmatter)**: 모든 마크다운 파일 최상단에는 YAML 메타데이터(`type`, `category`, `status`, `updated`)가 존재하여 스크립트로 파싱 및 검색이 가능합니다.
3. **검색 팁**: 특정 오류를 찾을 때는 `troubleshooting/index.md`를 우선 참고하고, 구조적인 흐름을 이해하려면 `architecture/`를 먼저 확인하세요.

## 📞 담당자 정보
- AI Core Team (Team Lead / RAG Engineer)

---

> 🎉 **[2026-06-18] 전체 AI 문서 아키텍처 재정렬 및 Description 전면 업데이트 완료**
> AI 문서 정리 작업을 모두 마무리했습니다! 이제 `ai/` 폴더는 AI 시스템의 완벽한 SSOT(Single Source of Truth)로 작동합니다.
