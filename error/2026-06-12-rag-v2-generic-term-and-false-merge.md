# RAG v2 generic term extraction and false concept merges

- 발생 일시: 2026-06-12
- 영역: AI / RAG card migration
- 심각도: high

## 증상

`concepts_v2` 생성 결과에서 `에서`, `반복문이`, `접근`, `특징으로`, `server` 같은 일반어가 term으로 선택되었다. React key와 Java equals 질의는 전용 카드가 없어 관련 없는 일반 카드가 top 결과로 검색되었고, `frontend-5`, `python-2`에는 서로 다른 개념 질문이 병합되었다.

## 원인

term 추출기가 질문 본문의 첫 영문 토큰 또는 첫 한글 어절을 선택했고, 조사·일반어 필터와 복합 개념 패턴이 없었다. 병합 로직은 alias 하나가 겹치거나 64차원 해시 임베딩 유사도가 임계값을 넘으면 서로 다른 term도 병합하여 충돌과 일반 alias에 취약했다.

## 해결 방법

- `ai/app/scripts/migrate_rag_cards.py:74`에 일반 term stopword를 추가했다.
- `ai/app/scripts/migrate_rag_cards.py:82`와 `ai/app/scripts/migrate_rag_cards.py:278`에 React key, Java equals 등 우선 개념 패턴과 안전한 fallback 추출을 추가했다.
- `ai/app/scripts/migrate_rag_cards.py:330`에서 alias 및 embedding 기반 병합에 식별 토큰 겹침 조건을 추가했다.
- `ai/tests/test_migrate_rag_cards_v2.py:124`, `ai/tests/test_migrate_rag_cards_v2.py:143`, `ai/tests/test_migrate_rag_cards_v2.py:178`에 회귀 테스트를 추가했다.
- `ai/scripts/audit_rag_cards_v2.py:40`에서 알려진 cross-concept source 조합이 같은 카드에 남았는지 검사하도록 감사 기준을 변경했다.

## 재발 방지 / 메모

새로운 질문 문형이 일반 fallback term을 생성할 수 있으므로 실제 생성 후 품질 감사를 항상 재실행한다. alias overlap이나 embedding similarity만으로 병합 조건을 완화하지 않으며, 전용 개념 질의의 top5 결과를 함께 확인한다.
