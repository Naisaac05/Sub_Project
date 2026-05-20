# AI Review Candidate Approval Guide

이 문서는 `ai/app/knowledge/candidates/course_concepts.jsonl`에 쌓인 concept 후보를 사람이 검토하고, 승인된 후보만 RAG concept card로 승격하는 방법을 정리한다.

## 목적

course skill test의 10문제들에는 학습자가 자주 헷갈리는 핵심 단어가 많이 들어 있다. 하지만 문제에서 단어가 추출됐다는 사실만으로는 그 단어가 정확한 학습 지식이라는 뜻이 아니다.

따라서 Phase 4.8의 candidate approval pipeline은 아래 원칙을 따른다.

1. 문제에서 단어 후보를 자동 추출한다.
2. 자동 후보는 기본적으로 `approved: false` 상태로 둔다.
3. 사람이 정의와 맥락을 검토한 후보만 `approved: true`로 바꾼다.
4. 승인된 후보만 concept card로 승격한다.
5. concept card lint를 통과한 카드만 RAG 지식으로 사용한다.

이 흐름은 잘못된 자동 정의가 답변 품질을 오염시키는 것을 막기 위한 안전장치다.

## 관련 파일

- 후보 파일: `ai/app/knowledge/candidates/course_concepts.jsonl`
- 후보 추출 스크립트: `ai/scripts/extract_course_concepts.py`
- AI 초안/critic 메타데이터 생성 스크립트: `ai/scripts/draft_candidate_reviews.py`
- 중복 후보 검사 스크립트: `ai/scripts/check_candidate_duplicates.py`
- CLI 검토 스크립트: `ai/scripts/review_concept_candidate.py`
- 승인 승격 스크립트: `ai/scripts/promote_concept_candidates.py`
- concept card 출력 위치: `ai/app/knowledge/concepts/generated/`
- concept card lint: `ai/scripts/lint_knowledge_cards.py`
- 원본 문제 시드: `backend/src/main/java/com/devmatch/config/CourseSkillTestInitializer.java`
- 관리자 UI: `/admin/ai-review-candidates`
- 관리자 API: `/api/admin/ai-review/candidates`

## 전체 작업 순서

### 1. 후보를 다시 추출한다

문제가 추가되거나 수정됐다면 먼저 후보 파일을 갱신한다.

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python scripts\extract_course_concepts.py
```

정상 출력 예시는 다음과 같다.

```json
{
  "source": "backend\\src\\main\\java\\com\\devmatch\\config\\CourseSkillTestInitializer.java",
  "output": "app\\knowledge\\candidates\\course_concepts.jsonl",
  "questions": 140,
  "candidates": 127
}
```

`questions`는 파싱된 문제 수이고, `candidates`는 추출된 단어 후보 수다.

### 2. 후보 JSONL을 연다

파일 위치:

```text
C:\Users\User\Desktop\Sub_Project\ai\app\knowledge\candidates\course_concepts.jsonl
```

JSONL은 한 줄이 하나의 후보다. 예시는 다음과 같다.

```json
{"aliases":["fastapi"],"approved":false,"category":"python-backend","definition":"","definition_status":"pending_human_review","source":"CourseSkillTestInitializer","source_path":"backend\\src\\main\\java\\com\\devmatch\\config\\CourseSkillTestInitializer.java","source_question_ids":["python-backend:2"],"term":"FastAPI"}
```

검토자는 한 줄씩 보면서 `definition`, `definition_status`, `approved`를 수정한다.

### 2-1. AI 초안과 critic 메타데이터를 만든다

후보를 사람이 빈칸에서 직접 정의하지 않도록 AI draft/critic 메타데이터를 먼저 채운다.

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python scripts\draft_candidate_reviews.py --limit 10
```

현재 기본 provider는 노트북 친화적인 `template-candidate-review`다. 이 스크립트는 승인하지 않는다. 아래 필드만 추가한다.

로컬 Ollama로 실제 draft/critic을 생성하려면 다음처럼 실행한다.

```powershell
python scripts\draft_candidate_reviews.py --provider ollama --model qwen3:4b-q4_K_M --limit 3
```

Ollama 모드는 더 느릴 수 있으므로 처음에는 `--limit 3`처럼 작게 돌리는 것을 권장한다.

- `definition_draft`
- `draft_model`
- `draft_version`
- `drafted_at`
- `critic_feedback`
- `critic_risk_level`
- `critic_recommendation`
- `critic_model`
- `critic_version`
- `criticized_at`
- `sources`
- `rejected_reason`

중요한 원칙:

- `definition_draft`는 초안일 뿐이다.
- 초안은 RAG에 바로 들어가지 않는다.
- 사람이 승인해서 `definition`으로 확정한 후보만 승격된다.

### 2-2. 기존 concept card와 중복되는지 확인한다

승격 전에 기존 concept card와 의미가 겹칠 수 있는 후보를 표시한다.

```powershell
python scripts\check_candidate_duplicates.py
```

중복 의심 후보에는 아래 필드가 붙는다.

- `duplicate_status`
- `duplicate_concept_ids`
- `duplicate_reason`

`duplicate_status`가 `duplicate_suspected`이면 바로 승인하지 말고 기존 concept card를 확장할지, 새 concept card가 필요한지 먼저 판단한다.

### 3. 원본 문제 맥락을 확인한다

`source_question_ids`를 보고 어떤 문제에서 나온 단어인지 확인한다.

예를 들어:

```json
"source_question_ids": ["python-backend:2"]
```

이 값은 `python-backend` 코스의 2번 문제에서 나온 후보라는 뜻이다. 원본은 `CourseSkillTestInitializer.java`에서 해당 코스와 문제 번호를 찾아 확인한다.

확인해야 할 것은 세 가지다.

1. 이 단어가 실제로 학습자가 질문할 만한 개념인가?
2. 이 코스와 문제 맥락에서 설명할 가치가 있는가?
3. 정의가 너무 넓거나, 반대로 문제 하나에만 묶여 너무 좁지는 않은가?

## 승인 기준

### 승인해도 좋은 후보

아래 조건을 모두 만족하면 승인할 수 있다.

- 기술 개념으로 독립적인 설명 가치가 있다.
- 학습자가 "이게 뭐야?", "왜 쓰는 거야?", "어디서 헷갈리는 거야?"라고 물을 가능성이 높다.
- 정의를 1~3문장으로 명확하게 설명할 수 있다.
- 문제 풀이 또는 면접 답변에 직접 도움이 된다.
- 기존 concept card와 의미가 완전히 중복되지 않는다.

예시:

- `FastAPI`
- `Pydantic`
- `JPA N+1`
- `fetch join`
- `pagination`
- `aria-label`
- `Kafka partition`
- `Redis SET NX PX`

### 보류해야 하는 후보

아래에 해당하면 `approved: false`로 둔다.

- 너무 일반적인 단어다. 예: `key`, `state`, `error`
- 코스 맥락 없이는 의미가 모호하다.
- 이미 기존 concept card나 fast path에서 충분히 다루고 있다.
- 정의를 정확히 쓰기 어렵다.
- 문제 선택지에 우연히 등장했지만 핵심 개념은 아니다.
- 한국어/영어 alias가 깨져 있거나 의미를 확신할 수 없다.

보류 후보는 삭제하지 않아도 된다. 나중에 다시 검토할 수 있게 `approved: false`로 남겨두는 것이 안전하다.

## definition 작성 방법

`definition`은 답변 생성에 직접 쓰일 수 있으므로 짧고 정확해야 한다.

좋은 정의의 조건:

- 첫 문장은 "무엇인가"를 말한다.
- 두 번째 문장은 "왜 쓰는가" 또는 "어디서 문제가 되는가"를 말한다.
- 가능하면 문제 풀이 관점의 힌트를 포함한다.
- 과장하거나 절대화하지 않는다.
- "항상", "무조건", "절대" 같은 표현은 근거가 확실할 때만 쓴다.

좋은 예시:

```json
"definition": "FastAPI는 Python으로 API 서버를 만들 때 사용하는 웹 프레임워크입니다. 타입 힌트와 Pydantic 기반 검증을 활용해 요청/응답 스키마를 비교적 명확하게 관리할 수 있습니다."
```

좋지 않은 예시:

```json
"definition": "FastAPI는 제일 빠르고 좋은 백엔드 프레임워크입니다."
```

좋지 않은 이유:

- "제일 빠르고 좋은"은 과장이다.
- 어떤 상황에서 쓰는지 설명하지 않는다.
- 학습자가 문제를 이해하는 데 필요한 힌트가 부족하다.

## 필드별 수정 방법

### approved

승인 여부다.

승인 전:

```json
"approved": false
```

승인 후:

```json
"approved": true
```

`approved: true`라도 `definition`이 비어 있으면 승격되지 않는다.

### definition

사람이 검토해서 작성한 개념 정의다.

승인 전:

```json
"definition": ""
```

승인 후:

```json
"definition": "Pydantic은 Python 타입 힌트를 기반으로 데이터 검증과 직렬화를 도와주는 라이브러리입니다. FastAPI에서는 요청 바디나 응답 스키마를 명확하게 다루는 데 자주 사용됩니다."
```

### definition_status

정의의 검토 상태다.

대기 상태:

```json
"definition_status": "pending_human_review"
```

사람이 검토한 뒤:

```json
"definition_status": "human_reviewed"
```

현재 승격 스크립트는 `approved: true`와 비어 있지 않은 `definition`을 기준으로 승격한다. 그래도 운영상으로는 승인할 때 `definition_status`도 `human_reviewed`로 바꾸는 것을 권장한다.

### aliases

같은 개념을 부르는 다른 이름이다. 검색 품질에 영향을 준다.

예시:

```json
"aliases": ["fastapi"]
```

alias 추가가 필요한 경우:

- 사용자가 영어로 물을 수도 있고 한국어로 물을 수도 있다.
- 약어와 풀네임이 같이 쓰인다.
- 하이픈 유무가 달라질 수 있다.

예시:

```json
"aliases": ["n+1", "n plus one", "N+1 문제"]
```

## 승인 전 체크리스트

후보 하나를 승인하기 전에 아래를 확인한다.

- `term`이 실제 개념명인가?
- `category`가 맞는가?
- `source_question_ids`의 원본 문제와 연결되는가?
- `definition`이 1~3문장으로 명확한가?
- 정의에 틀린 기술 설명이 없는가?
- 기존 concept card와 중복되지 않는가?
- `approved`를 `true`로 바꿨는가?
- `definition_status`를 `human_reviewed`로 바꿨는가?

## 승격 실행

검토가 끝나면 승인 후보를 concept card로 승격한다.

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python scripts\promote_concept_candidates.py
```

## CLI로 검토하기

관리자 UI를 쓰지 않고 CLI로도 후보를 검토할 수 있다.

승인:

```powershell
python scripts\review_concept_candidate.py --term FastAPI --category python-backend --action approve --definition "FastAPI는 Python으로 API 서버를 만들 때 사용하는 웹 프레임워크입니다."
```

보류:

```powershell
python scripts\review_concept_candidate.py --term API --category distributed-lock --action hold --rejected-reason "너무 넓은 개념이라 REST API와 분리 검토 필요"
```

반려:

```powershell
python scripts\review_concept_candidate.py --term API --category distributed-lock --action reject --rejected-reason "독립 concept card로는 너무 일반적임"
```

## 관리자 UI로 검토하기

Spring Boot와 Next.js가 실행 중이면 아래 화면에서 후보를 검토할 수 있다.

```text
/admin/ai-review-candidates
```

UI에서 할 수 있는 일:

- 후보 목록 확인
- risk level 필터링
- AI definition draft 확인
- Critic feedback 확인
- duplicate warning 확인
- definition 수정
- 승인 / 보류 / 반려 처리

UI는 Spring Boot의 아래 API를 호출한다.

```text
GET  /api/admin/ai-review/candidates
POST /api/admin/ai-review/candidates/review
```

승인된 후보가 없다면 다음처럼 나온다.

```json
{
  "input": "app\\knowledge\\candidates\\course_concepts.jsonl",
  "output": "app\\knowledge\\concepts\\generated",
  "candidates": 127,
  "approved_candidates": 0,
  "written": []
}
```

승인된 후보가 있다면 `written`에 생성된 concept card 경로가 표시된다.

## 승격 후 검증

승격 후에는 반드시 lint와 테스트를 실행한다.

```powershell
python scripts\lint_knowledge_cards.py
python -m unittest discover -s tests -v
python scripts\evaluate_lightweight_rag.py
```

확인해야 할 결과:

- `Knowledge card lint passed`
- unittest가 실패 없이 종료
- evaluator의 핵심 지표가 급격히 떨어지지 않음

concept card가 생성된 뒤에는 필요하면 아래 명령으로 Python 문법 컴파일도 확인한다.

```powershell
python -m compileall app scripts tests
```

## 자주 하는 실수

### 자동 후보를 바로 승인한다

자동 추출은 "이 단어가 문제에 등장했다"는 의미일 뿐이다. "정확한 학습 지식이다"라는 의미가 아니다. 반드시 원본 문제 맥락과 정의를 확인한다.

### definition을 너무 길게 쓴다

RAG 답변은 짧고 빠르게 나와야 한다. concept card의 핵심 정의는 길게 쓰기보다 정확하게 써야 한다.

권장 길이:

- 짧은 개념: 1문장
- 헷갈리기 쉬운 개념: 2~3문장
- 비교가 필요한 개념: 별도 concept card 또는 향후 approved QA로 분리

### 문제 정답을 definition에 넣는다

definition은 특정 문제의 정답 해설이 아니라 개념 설명이다.

나쁜 방향:

```text
이 문제에서는 1번이 정답입니다.
```

좋은 방향:

```text
페이지네이션은 전체 데이터를 한 번에 내려주지 않고 page, size 같은 기준으로 나누어 제공하는 방식입니다. 데이터가 많을 때 응답 크기와 화면 렌더링 부담을 줄이는 데 사용됩니다.
```

### 너무 넓은 개념을 하나로 합친다

예를 들어 `API`, `REST API`, `HTTP status code`는 서로 연결되어 있지만 같은 개념은 아니다. 학습자가 따로 물어볼 가능성이 높으면 별도 후보로 유지한다.

## 운영 원칙

- 후보 추출은 자주 해도 된다.
- 승인은 천천히 해도 된다.
- 모르는 후보는 삭제하지 말고 보류한다.
- 승인된 후보만 RAG 지식으로 들어가야 한다.
- lint를 통과하지 못한 concept card는 사용하지 않는다.
- 답변 품질이 이상해지면 최근 승인한 concept card를 먼저 의심한다.

## 다음 확장 방향

Phase 4.8 이후에는 아래 기능을 붙일 수 있다.

- 관리자 화면에서 후보 승인/반려 처리
- 후보별 원본 문제 미리보기
- 중복 concept 자동 감지
- 승인 이력 저장
- 승인자와 승인 시각 기록
- 생성된 concept card를 UI에서 검색하고 수정하는 기능
