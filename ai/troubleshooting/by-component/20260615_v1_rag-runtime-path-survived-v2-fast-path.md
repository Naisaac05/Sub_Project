---
type: troubleshooting
category: rag
status: active
updated: 2026-06-18
description: "v2 Fast Path 도입 후에도 v1 RAG 운영 경로가 남음 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# v2 Fast Path 도입 후에도 v1 RAG 운영 경로가 남음

- 발생 일시: 2026-06-15
- 영역: AI workflow / RAG
- 심각도: high

## 증상
v2 승인 Fast Path가 실패하면 Ollama로 바로 진행해야 했지만, free-question은 먼저 기본 v1 카드 검색을 수행하고 lightweight/static 답변을 반환할 수 있었다. follow-up 검증도 특정 조건에서 v1 카드를 다시 조회했다.

## 원인
기본 `load_concept_cards()` 루트와 `retrieve_context_node`, lightweight 카드 답변, follow-up 키워드 검증이 v1 저장소를 계속 참조했다. v2 설정도 shadow 10%여서 v2가 유일한 운영 경로가 아니었다.

## 해결 방법
free-question의 v1 context 검색과 lightweight/static 경로를 제거하고 v2 승인 Fast Path miss가 Ollama로 이어지도록 수정했다. follow-up의 v1 재조회를 제거하고 기본 카드 루트를 `concepts_v2`로 변경했다. v2를 serve 100%로 설정하고 v1 카드 폴더·비교 스크립트·오래된 index manifest 항목을 삭제했다.

관련 파일: `ai/app/workflow/nodes.py:48`, `ai/app/workflow/runner.py:302`, `ai/app/workflow/v2_approved_fast_path.py:20`, `ai/app/rag/documents.py:9`, `ai/app/config/rag_parallel.json:1`

## 재발 방지·메모
운영 정책 검증 시 v2 hit뿐 아니라 v2 miss에서 v1 retriever와 lightweight resolver가 호출되지 않는지도 테스트한다. 구형 v1/static 정책 테스트는 새 운영 정책에 맞게 별도 정리해야 한다.
