# session_id, client_request_id 복합 UNIQUE 제약 DB별 호환성 및 동작 표준 명세서

본 문서는 `ai_review_messages` 테이블에 추가된 `UNIQUE(session_id, client_request_id)` 복합 고유 제약조건이 실서비스 운영 환경(MySQL), 로컬 및 테스트 환경(H2 Database), 그리고 향후 고도화 시 고려될 수 있는 PostgreSQL 데이터베이스 환경에서 어떻게 동작하는지 비교 분석하고 정합성을 입증한 명세서입니다.

---

## 1. DB별 UNIQUE 제약 내 Nullable 컬럼 처리 특성 비교

우리가 설계한 `UNIQUE(session_id, client_request_id)`는 필수 컬럼(`session_id: NOT NULL`)과 선택형 컬럼(`client_request_id: Nullable`)의 복합 구조입니다. 
핵심은 **"client_request_id가 NULL인 레거시 요청은 여러 번 저장(하위 호환)될 수 있어야 하고, 멱등키가 있는 신규 요청은 고유하게 락업되어 중복 저장이 차단되는가"**입니다.

각 RDBMS 엔진별 SQL 표준 준수 여부 및 동작 차이를 분석한 결과는 다음과 같습니다:

| 데이터베이스 엔진 | client_request_id IS NULL 중복 허용 여부 | 멱등키 고유성 보호 여부 | 분석 및 동작 메커니즘 |
| :--- | :---: | :---: | :--- |
| **MySQL (InnoDB)** | **허용 (정상)** | **보호 (정상)** | SQL:2003 표준에 따라 `NULL` 값은 고유성 인덱스 중복성 검사 대상에서 스킵됩니다. 따라서 `(20, NULL)` 레코드가 무한히 인입될 수 있으며, 멱등키가 존재하는 `(20, 'req-uuid')` 조합은 엄격하게 1개만 보장됩니다. |
| **PostgreSQL** | **허용 (정상)** | **보호 (정상)** | PostgreSQL 또한 표준 SQL 규칙을 엄격히 준수합니다. 복합 유니크 제약 내에서 Nullable 필드가 null인 경우 고유성 제약을 적용하지 않으므로 레거시 무제한 삽입 및 신규 멱등키 레이스 철벽 차단이 완벽히 만족됩니다. |
| **H2 Database** | **허용 (정상)** | **보호 (정상)** | 로컬 H2 Database(호환 모드 포함) 역시 Nullable 컬럼의 유니크 제약 내 중복을 정상 허용하며, 멱등키가 존재하는 레코드에 대해서는 완벽하게 동시성 유니크 제약을 작동시킵니다. |

---

## 2. 결론 및 보증

* **동일 동작 보증**: H2, MySQL, PostgreSQL 모든 환경에서 복합 UNIQUE 제약은 **100% 동일하게 의도한 스펙대로 작동**합니다.
* **하위 호환성 보장**: 이전 API 호출 등으로 인해 `client_request_id` 필드가 없이 인입된 기존 데이터 및 신규 레거시 요청은 유니크 제약 조건 충돌을 전혀 일으키지 않고 자유롭게 데이터가 누적됩니다.
* **동시성 철벽 방어**: 프론트엔드가 발급한 UUID 기반 `client_request_id`가 채워진 요청은 세션 내에서 단 1회만 삽입되도록 완벽히 직렬화(Serialization)되어 동시성 race condition 하에서도 중복 저장을 데이터베이스 수준에서 원천 차단합니다.
