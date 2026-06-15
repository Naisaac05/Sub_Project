# 성공 카드 20개 승인 검토

## 결과

- 검토 대상: 20개
- 기존 approved 유지: 9개
- draft 승격 권고: 2개
- draft 보류: 9개
- 자동 품질 게이트 실패: 0개
- 검색 회귀: 0개
- 실제 승인 상태 변경: 없음

## Draft 승격 권고

| 카드 | 근거 |
|---|---|
| java-completablefuture | `thenApply`의 값 변환과 `thenCompose`의 비동기 단계 연결을 실제 결과로 검증한다. |
| spring-responseentity | HTTP 상태 코드와 헤더를 생성하고 실제 반환값으로 검증한다. |

## Draft 보류

| 카드 | 보류 이유 | 수정 방향 |
|---|---|---|
| java-delegation | Bootstrap/Application 로더 존재만 확인하고 부모 위임 동작은 검증하지 않는다. | 부모 로더가 먼저 클래스를 찾는 흐름을 확인하는 예시로 교체 |
| java-g1-gc | 문자열 목록 변경은 G1 Region의 동적 역할을 검증하지 않는다. | JVM GC 로그 또는 G1 설정 확인 예시로 교체 |
| java-checked | Java 최상위 문맥에서 실행할 수 없는 코드 조각이다. | 클래스와 `main`을 포함한 최소 컴파일 예시로 교체 |
| spring-circuit | 임계값 조건문을 직접 작성했을 뿐 Circuit Breaker 동작을 사용하지 않는다. | Resilience4j CircuitBreaker 상태 전이 예시로 교체 |
| spring-valid | 검증 그룹 인터페이스 여부만 확인하고 Bean Validation 실행을 검증하지 않는다. | Validator로 그룹별 제약 위반 결과를 확인 |
| spring-profile | 활성 프로필만 확인하고 프로필별 Bean 선택을 검증하지 않는다. | ApplicationContext에서 dev Bean 선택을 확인 |
| frontend-usecallback | 함수 실행 결과만 비교해 함수 참조 메모이제이션을 검증하지 않는다. | 재렌더 전후 callback 참조 동일성을 확인 |
| frontend-react-server-components | 함수 타입만 확인해 서버 전용 실행 또는 Hook 제한을 검증하지 않는다. | 서버 데이터 조회 렌더 또는 Client Component와의 차이를 확인 |
| python-multiprocessing | 실행기 worker 수만 확인해 CPU/I/O 작업 선택 차이를 검증하지 않는다. | 작은 CPU 작업과 I/O 대기 작업의 실행 방식을 각각 확인 |

## 기존 Approved 유지

`frontend-functional-component`, `frontend-conditional-rendering`, `python-multiline-string`,
`python-dictionary`, `python-fstring`, `algorithm-5`, `algorithm-linked`, `algorithm-4`,
`algorithm-7`

일부 예시는 더 정교하게 개선할 수 있지만 `invalid_json`, `retrieval_break`,
`critical_fact_error`에 해당하지 않아 기존 승인 상태를 유지한다.

## 검증 근거

- 20개 모두 payload 자동 품질 게이트 통과
- `answer_overlap` 최대 30% 이하
- `same_reason_ratio` 모두 0
- `fake_example_score` 자동 검사상 모두 0
- Production Hit/LOO/Exact 변화 없음
- Content Hit 변화 없음
- 관련 검증 테스트 17개 통과
