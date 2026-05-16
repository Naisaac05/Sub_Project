# AI review RAG generic token false positive

- 발생 일시: 2026-05-16
- 영역: ai
- 심각도: low

## 증상

`retrieve_context("완전히 관계없는 우주선 연료 문제")`처럼 RAG 지식과 무관한 질문이 빈 결과를 반환하지 않고 `spring-n-plus-one` concept card를 반환했다.

## 원인

초기 keyword retriever가 모든 2글자 이상 한국어 토큰을 동일하게 점수화했다. 그 결과 `문제`, `이유`, `때문에`처럼 대부분의 학습 질문에 흔히 등장하는 일반 토큰 하나만 겹쳐도 관련 concept로 오탐될 수 있었다.

## 해결 방법

일반 토큰을 `STOPWORDS`로 분리하고, tokenizer 단계에서 제외했다.

- `ai/app/rag/retriever.py:6`
- `ai/app/rag/retriever.py:48`
- `ai/tests/test_rag_retriever.py:13`

## 재발 방지 / 메모

Phase 1의 retriever는 LangChain/BM25/kiwipiepy가 붙기 전의 fallback 검색이므로 점수 기준이 단순하다. concept card가 늘어나면 불용어 목록만으로는 부족할 수 있으니, Phase 3.5 이후에는 BM25 tokenizer와 최소 점수 threshold를 evaluator로 같이 검증해야 한다.

