# EXAONE Live E2E Quality Evaluation

Semantic Judge is disabled for this live evaluation. Human review is required for factual quality.

## Summary

| mode | total | errors | live EXAONE | retrieval | keyword pass | forbidden absent | fallback | p50 ms | p95 ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| rag | 12 | 0 | 8 | 66.7% | 25.0% | 100.0% | 58.3% | 82798 | 94192 |
| no_rag_forced | 12 | 0 | 11 | 0.0% | 58.3% | 100.0% | 33.3% | 22706 | 31795 |

## Human Review

### rest-api-definition / rag

- Question: REST API가 무엇이고 HTTP 메서드와 자원은 어떤 관계야?
- Model: `exaone3.5:2.4b`
- Route: `fallback_template`
- Intent: `concept_definition/definition`
- RAG policy: `latest_question_only`
- Retrieved: `java-backend-controlleradvice`
- Latency: `79992 ms`
- Fallback: `True`
- Quality flags: `missing_required_keywords`
- Required keywords passed: `False`
- Forbidden claims absent: `True`
- Error: `none`

REST API에 대한 승인된 지식 카드가 아직 부족해서 현재 문제 맥락 기준으로 답할게요. 정의만 외우기보다 이 개념이 어떤 조건에서 정답이 되는지, 그리고 헷갈리는 보기와 어떤 책임이나 역할이 다른지를 나눠서 보면 됩니다.

### rest-api-definition / no_rag_forced

- Question: REST API가 무엇이고 HTTP 메서드와 자원은 어떤 관계야?
- Model: `exaone3.5:2.4b`
- Route: `generation`
- Intent: `concept_definition/definition`
- RAG policy: `latest_question_only`
- Retrieved: `none`
- Latency: `24631 ms`
- Fallback: `False`
- Quality flags: `evidence_missing, hallucination_suspected`
- Required keywords passed: `True`
- Forbidden claims absent: `True`
- Error: `none`

REST API(리소스 기반 애플리케이션 프로그래밍 인터페이스)란 웹 서비스에서 데이터 교환을 위한 규칙 체계입니다. HTTP 메서드 중 GET은 특정 자원에 대한 정보 요청 시 사용되고, POST는 새로운 리소스 생성이나 업데이트 작업에 활용됩니다. 예를 들어, **GET /users** 호출로 모든 사용자 목록을 가져오거나, **POST /posts**로 새 게시물 작성과 같습니다.

### equals-hashcode-comparison / rag

- Question: Java equals와 hashCode의 역할 차이와 함께 재정의해야 하는 이유를 설명해줘.
- Model: `exaone3.5:2.4b`
- Route: `fallback_template`
- Intent: `concept_definition/comparison`
- RAG policy: `latest_question_only`
- Retrieved: `java-equals, java-hashcode, auto-review-hashcode`
- Latency: `78475 ms`
- Fallback: `True`
- Quality flags: `stale_original_context, missing_topic`
- Required keywords passed: `False`
- Forbidden claims absent: `True`
- Error: `none`

Java equals에 대한 승인된 지식 카드가 아직 부족해서 현재 문제 맥락 기준으로 답할게요. 정의만 외우기보다 이 개념이 어떤 조건에서 정답이 되는지, 그리고 헷갈리는 보기와 어떤 책임이나 역할이 다른지를 나눠서 보면 됩니다.

### equals-hashcode-comparison / no_rag_forced

- Question: Java equals와 hashCode의 역할 차이와 함께 재정의해야 하는 이유를 설명해줘.
- Model: `exaone3.5:2.4b`
- Route: `fallback_template`
- Intent: `concept_definition/comparison`
- RAG policy: `latest_question_only`
- Retrieved: `none`
- Latency: `22594 ms`
- Fallback: `True`
- Quality flags: `stale_original_context, missing_topic`
- Required keywords passed: `False`
- Forbidden claims absent: `True`
- Error: `none`

Java equals에 대한 승인된 지식 카드가 아직 부족해서 현재 문제 맥락 기준으로 답할게요. 정의만 외우기보다 이 개념이 어떤 조건에서 정답이 되는지, 그리고 헷갈리는 보기와 어떤 책임이나 역할이 다른지를 나눠서 보면 됩니다.

### spring-n-plus-one-debug / rag

- Question: Spring JPA에서 목록 조회 시 쿼리가 예상보다 많이 실행되는 N+1 문제의 원인과 해결 방법은?
- Model: `exaone3.5:2.4b`
- Route: `rag_generation`
- Intent: `concept_definition/related`
- RAG policy: `latest_question_only`
- Retrieved: `spring-n-plus-one, spring-fetch-join, java-arraylist`
- Latency: `68740 ms`
- Fallback: `False`
- Quality flags: `none`
- Required keywords passed: `False`
- Forbidden claims absent: `True`
- Error: `none`

Spring JPA에서 목록 조회 시 쿼리 실행 횟수가 예상보다 많아지는 N+1 문제의 주요 원인은 지연 로딩 메커니즘 때문입니다. 각 엔티티 객체 생성 과정에서 연관 데이터들이 별도의 쿼리에 의해 순차적으로 로드되기 때문에 발생합니다. 해결 방법으로는:

### spring-n-plus-one-debug / no_rag_forced

- Question: Spring JPA에서 목록 조회 시 쿼리가 예상보다 많이 실행되는 N+1 문제의 원인과 해결 방법은?
- Model: `exaone3.5:2.4b`
- Route: `generation`
- Intent: `concept_definition/related`
- RAG policy: `latest_question_only`
- Retrieved: `none`
- Latency: `22237 ms`
- Fallback: `False`
- Quality flags: `evidence_missing, hallucination_suspected, contradiction_suspected`
- Required keywords passed: `True`
- Forbidden claims absent: `True`
- Error: `none`

Spring JPA에서 목록 조회 시 쿼리 실행 횟수가 예상보다 많아지는 N+1 문제는 주로 연관된 서브쿼리 또는 조인 연산으로 인해 발생합니다. 예를 들어, `User` 엔티티와 그들의 여러 `Order` 객체를 동시에 가져오려고 할 때, 첫 번째로 모든 `User`를 가져온 후 각각의 사용자별 주문을 별도로 가져와 총 2번 이상의 데이터베이스 접근이 이루어질 수 있습니다. 이를 해결하기 위해 **Batch Loading** 기법이나 `@FetchGroup` 어노테이션을 사용하여 관련 데이터를 한 번에 로드하도록 설정할 수 있

### controller-advice-practical / rag

- Question: Spring에서 ControllerAdvice를 언제 사용하고 예외 응답을 어떻게 구성하는 게 좋아?
- Model: `exaone3.5:2.4b`
- Route: `rag_generation`
- Intent: `concept_definition/related`
- RAG policy: `latest_question_only`
- Retrieved: `java-backend-controlleradvice`
- Latency: `69440 ms`
- Fallback: `False`
- Quality flags: `none`
- Required keywords passed: `True`
- Forbidden claims absent: `True`
- Error: `none`

Spring MVC에서 `@ControllerAdvice`는 여러 컨트롤러가 공통적으로 처리해야 하는 예외 상황을 한 곳에 모아 관리할 때 유용합니다. 예를 들어, 사용자 인증 실패 시 모든 요청 경로에서 일관된 오류 메시지와 상태 코드(예: 401 Unauthorized)로 응답하려면 다음과 같이 구성할 수 있습니다:

### controller-advice-practical / no_rag_forced

- Question: Spring에서 ControllerAdvice를 언제 사용하고 예외 응답을 어떻게 구성하는 게 좋아?
- Model: `exaone3.5:2.4b`
- Route: `generation`
- Intent: `concept_definition/related`
- RAG policy: `latest_question_only`
- Retrieved: `none`
- Latency: `20733 ms`
- Fallback: `False`
- Quality flags: `evidence_missing, hallucination_suspected`
- Required keywords passed: `True`
- Forbidden claims absent: `True`
- Error: `none`

Spring에서 `ControllerAdvice`는 주로 예외 처리와 공통적인 응답 로직을 관리할 때 사용됩니다. 예를 들어, `@ExceptionHandler` 어노테이션을 통해 특정 exceptions (예: `NotFoundException`)에 대한 맞춤형 응답 코드(HTTP 상태 코드 404)와 메시지를 구성합니다. 구체적으로, 다음과 같이 설정할 수 있습니다:

### aria-label-practical / rag

- Question: 아이콘 버튼에 aria-label이 필요한 이유와 작성할 때 주의점을 알려줘.
- Model: `exaone3.5:2.4b`
- Route: `rag_generation`
- Intent: `concept_definition/related`
- RAG policy: `latest_question_only`
- Retrieved: `frontend-aria-label`
- Latency: `66257 ms`
- Fallback: `False`
- Quality flags: `none`
- Required keywords passed: `False`
- Forbidden claims absent: `True`
- Error: `none`

아이콘 버튼에 `aria-label`이 필요하다면 주로 시각적 표현으로 명확한 기능이나 의미를 전달하기 어려운 경우입니다. 예를 들어, 단순히 아이콘만 있으면 사용자가 그 아이콘의 실제 역할(예: "로그인", "공유")을 알기 어렵습니다. 이때 `aria-label="로그인"`처럼 구체적으로 설명해주면 스크린 리더 사용자도 해당 요소의 목적을 이해할 수 있게 됩니다.

### aria-label-practical / no_rag_forced

- Question: 아이콘 버튼에 aria-label이 필요한 이유와 작성할 때 주의점을 알려줘.
- Model: `exaone3.5:2.4b`
- Route: `generation`
- Intent: `concept_definition/related`
- RAG policy: `latest_question_only`
- Retrieved: `none`
- Latency: `19828 ms`
- Fallback: `False`
- Quality flags: `none`
- Required keywords passed: `False`
- Forbidden claims absent: `True`
- Error: `none`

아이콘 버튼에 `aria-label`을 사용하는 이유는 시각 장애인이나 키보드만으로 웹사이트를 이용해야 하는 사용자들이 아이콘의 기능이나 의미를 이해할 수 있도록 돕기 위함입니다. 예를 들어, 장바구니 아이콘 옆에 `"장바구니 추가"`라는 텍스트를 제공하면 화면 리더기에서 해당 정보를 명확하게 전달합니다. 작성 시에 주의점은 간결하고 직관적인 언어로 라벨링하여 혼란 없이 정보가 전달되도록 해야 합니다 (예: 너무 긴 문장 대신 핵심 단어 위주).

### transaction-propagation / rag

- Question: Spring 트랜잭션 전파 옵션 REQUIRED와 REQUIRES_NEW는 어떤 상황에서 다르게 동작해?
- Model: `exaone3.5:2.4b`
- Route: `generation`
- Intent: `concept_definition/related`
- RAG policy: `latest_question_only`
- Retrieved: `none`
- Latency: `75356 ms`
- Fallback: `False`
- Quality flags: `evidence_missing, hallucination_suspected`
- Required keywords passed: `True`
- Forbidden claims absent: `True`
- Error: `none`

Spring 트랜잭션에서 `REQUIRED`와 `REQUIRES_NEW` 옵션은 다음과 같이 다르게 동작합니다:  
- **REQUIRED**는 부모 트랜잭션 내 모든 자식 트랜잭션을 완료해야만 커밋되거나 롤백되도록 강제합니다. 예를 들어, 은행 계좌 이체 시 출금 트랜잭션(자식)도 반드시 성공적이어야 전체 거래가 처리됩니다.

### transaction-propagation / no_rag_forced

- Question: Spring 트랜잭션 전파 옵션 REQUIRED와 REQUIRES_NEW는 어떤 상황에서 다르게 동작해?
- Model: `exaone3.5:2.4b`
- Route: `generation`
- Intent: `concept_definition/related`
- RAG policy: `latest_question_only`
- Retrieved: `none`
- Latency: `22706 ms`
- Fallback: `False`
- Quality flags: `evidence_missing, hallucination_suspected`
- Required keywords passed: `True`
- Forbidden claims absent: `True`
- Error: `none`

Spring 트랜잭션에서 `REQUIRED`와 `REQUIRES_NEW` 옵션은 다음과 같이 다르게 동작합니다:  
- **REQUIRED**는 부모 트랜잭션 내 모든 하위 작업을 반드시 완료해야만 커밋되며, 롤백 시에도 일관성 유지에 중점입니다 (예: 은행 이체 시스템).

### database-index-tradeoff / rag

- Question: 데이터베이스 인덱스가 조회를 빠르게 하지만 쓰기 성능과 저장 공간에 영향을 주는 이유는?
- Model: `template`
- Route: `fallback_template`
- Intent: `concept_definition/related`
- RAG policy: `latest_question_only`
- Retrieved: `java-arraylist, spring-n-plus-one`
- Latency: `84526 ms`
- Fallback: `True`
- Quality flags: `missing_required_keywords, missing_topic`
- Required keywords passed: `False`
- Forbidden claims absent: `True`
- Error: `none`

데이터베이스에 대한 승인된 지식 카드가 아직 부족해서 현재 문제 맥락 기준으로 답할게요. 정의만 외우기보다 이 개념이 어떤 조건에서 정답이 되는지, 그리고 헷갈리는 보기와 어떤 책임이나 역할이 다른지를 나눠서 보면 됩니다.

### database-index-tradeoff / no_rag_forced

- Question: 데이터베이스 인덱스가 조회를 빠르게 하지만 쓰기 성능과 저장 공간에 영향을 주는 이유는?
- Model: `exaone3.5:2.4b`
- Route: `generation`
- Intent: `concept_definition/related`
- RAG policy: `latest_question_only`
- Retrieved: `none`
- Latency: `21782 ms`
- Fallback: `False`
- Quality flags: `none`
- Required keywords passed: `True`
- Forbidden claims absent: `True`
- Error: `none`

데이터베이스 인덱스는 데이터 검색 속도를 크게 향상시킵니다. 하지만 이 과정에서 두 가지 주요 제약 사항이 발생합니다: 첫째, 추가적인 메타 정보 저장 공간이 필요해져 전체 데이터베이스 크기 증가와 관련된 저장 공간 소모가 생깁니다. 둘째, 새로운 레코드 삽입이나 업데이트 시 해당 인덱스도 갱신해야 하므로 쓰기 작업 시간과 리소스 사용량이 늘어납니다.

### http-401-403 / rag

- Question: HTTP 401과 403의 차이를 인증과 권한 관점에서 비교해줘.
- Model: `exaone3.5:2.4b`
- Route: `fallback_template`
- Intent: `concept_definition/comparison`
- RAG policy: `latest_question_only`
- Retrieved: `none`
- Latency: `82798 ms`
- Fallback: `True`
- Quality flags: `stale_original_context, missing_topic`
- Required keywords passed: `False`
- Forbidden claims absent: `True`
- Error: `none`

HTTP 401에 대한 승인된 지식 카드가 아직 부족해서 현재 문제 맥락 기준으로 답할게요. 정의만 외우기보다 이 개념이 어떤 조건에서 정답이 되는지, 그리고 헷갈리는 보기와 어떤 책임이나 역할이 다른지를 나눠서 보면 됩니다.

### http-401-403 / no_rag_forced

- Question: HTTP 401과 403의 차이를 인증과 권한 관점에서 비교해줘.
- Model: `exaone3.5:2.4b`
- Route: `fallback_template`
- Intent: `concept_definition/comparison`
- RAG policy: `latest_question_only`
- Retrieved: `none`
- Latency: `27473 ms`
- Fallback: `True`
- Quality flags: `stale_original_context, missing_topic`
- Required keywords passed: `False`
- Forbidden claims absent: `True`
- Error: `none`

HTTP 401에 대한 승인된 지식 카드가 아직 부족해서 현재 문제 맥락 기준으로 답할게요. 정의만 외우기보다 이 개념이 어떤 조건에서 정답이 되는지, 그리고 헷갈리는 보기와 어떤 책임이나 역할이 다른지를 나눠서 보면 됩니다.

### react-key-debug / rag

- Question: React 목록 렌더링에서 key로 배열 index를 쓰면 어떤 버그가 생길 수 있어?
- Model: `template`
- Route: `fallback_template`
- Intent: `concept_definition/related`
- RAG policy: `latest_question_only`
- Retrieved: `java-arraylist, auto-review-recyclerview, frontend-aria-label`
- Latency: `89573 ms`
- Fallback: `True`
- Quality flags: `missing_required_keywords, missing_topic`
- Required keywords passed: `False`
- Forbidden claims absent: `True`
- Error: `none`

React에 대한 승인된 지식 카드가 아직 부족해서 현재 문제 맥락 기준으로 답할게요. 정의만 외우기보다 이 개념이 어떤 조건에서 정답이 되는지, 그리고 헷갈리는 보기와 어떤 책임이나 역할이 다른지를 나눠서 보면 됩니다.

### react-key-debug / no_rag_forced

- Question: React 목록 렌더링에서 key로 배열 index를 쓰면 어떤 버그가 생길 수 있어?
- Model: `template`
- Route: `fallback_template`
- Intent: `concept_definition/related`
- RAG policy: `latest_question_only`
- Retrieved: `none`
- Latency: `36061 ms`
- Fallback: `True`
- Quality flags: `missing_topic`
- Required keywords passed: `False`
- Forbidden claims absent: `True`
- Error: `none`

React에 대한 승인된 지식 카드가 아직 부족해서 현재 문제 맥락 기준으로 답할게요. 정의만 외우기보다 이 개념이 어떤 조건에서 정답이 되는지, 그리고 헷갈리는 보기와 어떤 책임이나 역할이 다른지를 나눠서 보면 됩니다.

### docker-image-container / rag

- Question: Docker 이미지와 컨테이너의 차이를 실행 관점에서 설명해줘.
- Model: `template`
- Route: `fallback_template`
- Intent: `concept_definition/comparison`
- RAG policy: `latest_question_only`
- Retrieved: `none`
- Latency: `94192 ms`
- Fallback: `True`
- Quality flags: `missing_topic`
- Required keywords passed: `False`
- Forbidden claims absent: `True`
- Error: `none`

Docker 이미지에 대한 승인된 지식 카드가 아직 부족해서 현재 문제 맥락 기준으로 답할게요. 정의만 외우기보다 이 개념이 어떤 조건에서 정답이 되는지, 그리고 헷갈리는 보기와 어떤 책임이나 역할이 다른지를 나눠서 보면 됩니다.

### docker-image-container / no_rag_forced

- Question: Docker 이미지와 컨테이너의 차이를 실행 관점에서 설명해줘.
- Model: `exaone3.5:2.4b`
- Route: `generation`
- Intent: `concept_definition/comparison`
- RAG policy: `latest_question_only`
- Retrieved: `none`
- Latency: `31795 ms`
- Fallback: `False`
- Quality flags: `evidence_missing, hallucination_suspected`
- Required keywords passed: `True`
- Forbidden claims absent: `True`
- Error: `none`

Docker 이미지는 애플리케이션과 그 의존성을 포함한 정적 패키지입니다. 예를 들어, Python 웹 앱과 필요한 모든 라이브러리들이 하나의 컨테이너화된 환경으로 담겨 있습니다. 반면에 컨테이너는 실제 실행 중인 프로세스 단위로, 동일한 이미지 위에서 여러 개의 동적으로 생성되고 관리될 수 있습니다 (예: `docker run` 명령어로).

### sql-injection-prevention / rag

- Question: SQL Injection이 발생하는 원인과 prepared statement가 이를 막는 원리를 설명해줘.
- Model: `exaone3.5:2.4b`
- Route: `generation`
- Intent: `concept_definition/definition`
- RAG policy: `latest_question_only`
- Retrieved: `none`
- Latency: `109174 ms`
- Fallback: `False`
- Quality flags: `evidence_missing, hallucination_suspected`
- Required keywords passed: `True`
- Forbidden claims absent: `True`
- Error: `none`

SQL Injection은 악의적인 코드 삽입으로 데이터베이스 쿼리에 취약점을 악용하여 데이터 조작이나 접근 권한 상승 등 부정 행위를 일으키는 공격입니다. 이를 방지하기 위해 Prepared Statements 사용이 효과적인데, 이 방법에서 쿼리 문자열과 실제 값 분리 처리함으로써 입력값에 대한 직접 해석을 차단합니다. 예를 들어, 원래 SQL: `SELECT * FROM users WHERE id = '$username';` 에서 Prepared Statement로 작성하면: `PREPARE stmt FROM 'SELECT * FROM users WHERE id = ?

### sql-injection-prevention / no_rag_forced

- Question: SQL Injection이 발생하는 원인과 prepared statement가 이를 막는 원리를 설명해줘.
- Model: `exaone3.5:2.4b`
- Route: `generation`
- Intent: `concept_definition/definition`
- RAG policy: `latest_question_only`
- Retrieved: `none`
- Latency: `22106 ms`
- Fallback: `False`
- Quality flags: `evidence_missing, hallucination_suspected`
- Required keywords passed: `True`
- Forbidden claims absent: `True`
- Error: `none`

SQL Injection은 악의적인 코드 삽입으로 데이터베이스 쿼리에 취약점을 악용해 데이터 조작이나 접근 권한 상승을 일으키는 공격입니다. 예를 들어, `SELECT * FROM users WHERE id = 'user';` 쿼리에서 `' OR '1'='1` 같은 입력값이 주입되면 실제 조건 없이 모든 레코드에 접근 가능합니다. 반면 Prepared Statement는 SQL 문과 사용자 입력 사이에 분리된 구조로 작동하여, 사용자 입력이 항상 리터럴 값으로 해석되도록 합니다.

### async-await-error / rag

- Question: JavaScript async/await 코드에서 비동기 오류를 안전하게 처리하는 방법과 흔한 실수를 알려줘.
- Model: `template`
- Route: `fallback_template`
- Intent: `concept_definition/related`
- RAG policy: `latest_question_only`
- Retrieved: `spring-n-plus-one, java-backend-controlleradvice, cs-blocking-io`
- Latency: `88821 ms`
- Fallback: `True`
- Quality flags: `missing_required_keywords, missing_topic`
- Required keywords passed: `False`
- Forbidden claims absent: `True`
- Error: `none`

JavaScript에 대한 승인된 지식 카드가 아직 부족해서 현재 문제 맥락 기준으로 답할게요. 정의만 외우기보다 이 개념이 어떤 조건에서 정답이 되는지, 그리고 헷갈리는 보기와 어떤 책임이나 역할이 다른지를 나눠서 보면 됩니다.

### async-await-error / no_rag_forced

- Question: JavaScript async/await 코드에서 비동기 오류를 안전하게 처리하는 방법과 흔한 실수를 알려줘.
- Model: `exaone3.5:2.4b`
- Route: `fallback_template`
- Intent: `concept_definition/related`
- RAG policy: `latest_question_only`
- Retrieved: `none`
- Latency: `24193 ms`
- Fallback: `True`
- Quality flags: `missing_topic`
- Required keywords passed: `False`
- Forbidden claims absent: `True`
- Error: `none`

JavaScript에 대한 승인된 지식 카드가 아직 부족해서 현재 문제 맥락 기준으로 답할게요. 정의만 외우기보다 이 개념이 어떤 조건에서 정답이 되는지, 그리고 헷갈리는 보기와 어떤 책임이나 역할이 다른지를 나눠서 보면 됩니다.
