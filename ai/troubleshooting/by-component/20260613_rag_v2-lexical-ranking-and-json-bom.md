---
type: troubleshooting
category: rag
status: active
updated: 2026-06-18
description: "RAG v2 lexical ranking ties and JSON BOM 발생 원인 분석 및 트러블슈팅 해결 기록"

---

# RAG v2 lexical ranking ties and JSON BOM

- 발생 일시: 2026-06-13
- 영역: AI / RAG retrieval
- 심각도: high

## 증상

Shadow 검증에서 `extends`와 `with`가 더 구체적인 카드와 동점이 되어 파일 순서로 오검색되었고, `Spring cache`는 관련 카드 대신 Spring bean scope 카드를 반환했다. Retrieval 필드 수정 중 PowerShell 저장 결과에 UTF-8 BOM이 추가되어 해당 JSON 카드가 loader에서 제외되었다.

## 원인

`ai/app/rag/retriever.py`의 exact phrase 판정이 실제 구문 일치가 아니라 query와 title/concept token 하나의 교집합만 확인했다. 또한 기존 Spring CacheEvict 카드에 영어 cache alias가 없었다. PowerShell `Set-Content -Encoding UTF8`은 이 환경에서 BOM을 기록했고 Pydantic JSON parser는 BOM으로 시작하는 문자열을 거부했다.

## 해결 방법

- `ai/app/rag/retriever.py:537`에서 term, alias, boost keyword의 실제 ordered phrase 포함 여부에 따라 차등 가중하도록 변경했다.
- `ai/app/knowledge/concepts_v2/spring/spring-spring-question-59.json`의 retrieval alias, embedding text, boost keyword를 Spring cache 개념으로 보정했다.
- JSON 카드는 BOM 없는 `UTF8Encoding(false)`로 다시 저장했다.
- `ai/tests/test_rag_retriever.py`에 exact term 및 alias phrase ranking 회귀 테스트를 추가했다.

## 재발 방지 / 메모

JSON을 PowerShell로 구조화 수정할 때 `Set-Content -Encoding UTF8`을 사용하지 않고 BOM 없는 UTF-8 writer를 사용한다. Ranking calibration은 threshold나 질문별 예외 대신 field별 phrase 가중치로 수행하고 Shadow Top1~3를 확인한다.
