# 검색 비교 PoC 리포트 — `hybrid_queries.jsonl`

> 자동 생성. `python evals/retrieval_poc/evaluate.py --dataset ...`

- 코퍼스: 지식카드 11장 / 평가 질의 40개 (전체 40)
- recall@k = 정답 개념 중 하나라도 상위 k 안에 있으면 hit. MRR = 첫 정답 순위 역수.

## 1. 전체 결과

| 리트리버 | recall@1 | recall@3 | MRR |
|---|---:|---:|---:|
| bm25 (lexical, 단어) | 65.0% | 72.5% | 0.683 |
| bge (임베딩, 의미) | 97.5% | 100.0% | 0.988 |
| hybrid (RRF 융합) | 85.0% | 95.0% | 0.911 |

## 2. 질의 유형별 recall@1 (하이브리드가 어디서 값을 하나)

| query_type | n | bm25 | bge | hybrid |
|---|---:|---:|---:|---:|
| exact_token | 10 | 100% | 100% | 100% |
| mixed | 10 | 80% | 100% | 80% |
| semantic | 10 | 70% | 100% | 80% |
| typo | 10 | 10% | 90% | 80% |

## 3. recall@3 에서 놓친 질의

### bm25 (lexical, 단어) — 11건
- `쿼리 한 번에 연관된 데이터까지 같이 가져오는 방법` (semantic) → 정답 ['spring-fetch-join'], top3=['spring-n-plus-one']
- `패치조인 어떻게 쓰나요` (typo) → 정답 ['spring-fetch-join'], top3=[]
- `엔플러스원 문제가 뭐임` (typo) → 정답 ['spring-n-plus-one'], top3=['auto-review-hashcode', 'java-equals', 'spring-fetch-join']
- `두 객체가 내용상 같은 값인지 비교하려면` (semantic) → 정답 ['java-equals'], top3=['auto-review-hashcode', 'java-hashcode', 'frontend-aria-label']
- `이퀄즈랑 == 차이가 뭐죠` (typo) → 정답 ['java-equals'], top3=[]
- `해쉬코드 왜 오버라이드 해야됨` (typo) → 정답 ['auto-review-hashcode', 'java-hashcode'], top3=[]
- `어레이리스트 중간삽입 시간복잡도` (typo) → 정답 ['java-arraylist'], top3=[]
- `컨트롤러어드바이스 적용이 안돼요` (typo) → 정답 ['java-backend-controlleradvice'], top3=[]
- `아리아라벨 언제 써야되나` (typo) → 정답 ['frontend-aria-label'], top3=[]
- `리사이클러뷰 어댑터 연결 방법` (typo) → 정답 ['auto-review-recyclerview'], top3=[]
- `텍스트인풋 밸류 온체인지 연결` (typo) → 정답 ['auto-review-textinput'], top3=[]

### bge (임베딩, 의미) — 0건

### hybrid (RRF 융합) — 2건
- `엔플러스원 문제가 뭐임` (typo) → 정답 ['spring-n-plus-one'], top3=['spring-fetch-join', 'auto-review-hashcode', 'java-equals']
- `두 객체가 내용상 같은 값인지 비교하려면` (semantic) → 정답 ['java-equals'], top3=['auto-review-hashcode', 'java-hashcode', 'frontend-aria-label']
