# RAG BM25 코스 질의 별칭 누락

## 증상

Lexical 검색에서는 Java 문자열 비교와 React 리스트 속성 질의가 기대 카드로 검색됐지만 BM25 평가에서는 Top1 적중률이 0%였다.

## 원인

코스 문장형 질의를 `equals`, `key` 토큰으로 확장하는 로직이 Lexical 어댑터에만 연결되고 BM25 질의 토큰화에는 적용되지 않았다.

## 해결 방법

기본 BM25 질의 토크나이저에도 `tokenize_query`를 적용하고 두 질의를 BM25 회귀 테스트와 평가 데이터셋에 추가했다.

- `ai/app/rag/retriever.py:117`
- `ai/tests/test_rag_retriever.py:291`
- `ai/evals/rag_card_expansion_gaps_2026-06-21.jsonl:1`

## 재발 방지·메모

검색 별칭 변경은 Lexical과 BM25를 함께 검증한다. 모델 다운로드가 필요한 dense 검색은 이번 범위에서 실행하지 않았다.
