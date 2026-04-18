# Phase B 멘토 리팩터 — JPA 갱신 버그, Test 카테고리 정합성, 이력 무결성

- 발생일: 2026-04-18
- 영역: backend
- 심각도: high (JPA 버그 + 데이터 정합성 모두 운영 영향)

## 증상

### [1] JPA PersistentSet 갱신 누락
`MentorProfile.updateFromRequest()` 에서 코스 목록 업데이트 시 `this.courses = newCourses;` 로 필드를 통째로 교체했을 때, Hibernate가 `mentor_profile_courses` junction 테이블에 DELETE + INSERT를 발행하지 않음. 기존 코스 행이 그대로 남아 이전 코스 선택이 반영되지 않는다.

### [2] Test 카테고리 슬러그 불일치
`MatchingService.recommendMentors` 가 `c.getCourseKey().equals(category)` 로 필터링하는데, `DataInitializer` 가 시드하는 Test 엔티티의 category 값이 `"Java"`, `"Spring"`, `"React"`, `"Python"`, `"Algorithm"` 같은 표시명(display name)이었음. courseKey 슬러그(`"java-backend"`, `"frontend"` 등)와 일치하지 않아 매칭 추천 결과가 항상 빈 목록으로 반환됨.

### [3] 이력에 비검증 courseKeys 저장
`MentorService.apply()` 에서 `historyRepository.save(...)` 시 `request.getCourseKeys()` (사용자 입력 원본)를 그대로 저장. 존재하지 않거나 비활성화된 courseKey가 포함되어도 이력에 그대로 기록되는 silent audit drift 발생. 또한 무효 코스에 대한 명시적 거부 응답이 없었음.

## 원인

### [1] Hibernate PersistentSet 참조 교체
`@ManyToMany` 컬렉션 필드는 Hibernate가 `PersistentSet` 으로 프록시 관리함. `this.courses = newCourses;` 는 Hibernate 추적 대상인 `PersistentSet` 참조를 끊고 일반 `HashSet` 으로 교체하기 때문에, dirty checking 시 변경 감지가 되지 않아 flush 때 junction 테이블 변경 SQL이 생성되지 않는다.

### [2] 시드 데이터 작성 당시 courseKey 체계와 무관하게 표시명 사용
DataInitializer 작성 시점에 Test.category 와 MentoringCourse.courseKey 가 동일 값이어야 한다는 제약이 명시되지 않았음. 추후 MatchingService 가 courseKey 기반 필터로 리팩터링되면서 불일치가 생겼다.

### [3] 서비스 레이어에서 입력 원본과 DB 조회 결과를 구분하지 않음
`courseService.findActiveByKeys` 결과(검증된 엔티티)를 프로필에는 적용하면서, 이력 저장 시에는 원본 요청 값을 그대로 사용. 또한 요청에 포함된 키 수와 DB 결과 수 불일치 시 오류를 발생시키지 않아 무효 입력이 조용히 통과됐다.

## 해결 방법

### [1] PersistentSet in-place 갱신
`backend/src/main/java/com/devmatch/entity/MentorProfile.java:90-91`
```java
// Before
this.courses = newCourses;
// After
this.courses.clear();
this.courses.addAll(newCourses);
```
PersistentSet 참조를 유지하면서 내용만 교체 → Hibernate가 정확히 DELETE + INSERT 발행.

### [2] Test 카테고리를 courseKey 슬러그로 통일
`backend/src/main/java/com/devmatch/config/DataInitializer.java`
- `"Java"` / `"Spring"` → `"java-backend"`
- `"React"` → `"frontend"`
- `"Python"` → `"python-backend"`
- `"Algorithm"` → `"firststep"`

Swagger 설명도 동일하게 갱신:
- `backend/src/main/java/com/devmatch/controller/MatchingController.java:29` — `?category=Java` → `?category=java-backend`
- `backend/src/main/java/com/devmatch/controller/TestController.java:28` — 동일

### [3] 검증된 courseKey만 이력에 저장 + 무효 입력 즉시 거부
`backend/src/main/java/com/devmatch/service/MentorService.java`
- 상태 중복 검사(PENDING/APPROVED) 를 course 조회보다 먼저 수행하도록 순서 변경 (불필요한 DB 조회 방지 + 기존 테스트 동작 유지)
- `findActiveByKeys` 결과 size != 요청 size 이면 `CourseNotFoundException` throw
- 이력 저장 시 `courses.stream().map(MentoringCourse::getCourseKey).toList()` 사용

## 재발 방지 / 메모

- **Hibernate 컬렉션 갱신 원칙**: `@OneToMany` / `@ManyToMany` 필드에 새 컬렉션을 통째로 대입하지 말 것. 반드시 `clear()` + `addAll()` (또는 `removeIf` + `add`).
- **시드/테스트 데이터에서 FK처럼 동작하는 문자열 키** (category, courseKey 등)는 실제 키 값과 동기화 여부를 시드 작성 시점에 확인할 것. 컨벤션 문서나 enum 상수로 공유하면 오류를 줄일 수 있다.
- **서비스 입력 검증**: 외부 입력을 DB에 저장할 때는 항상 검증 통과 후 DB 조회 결과 기준으로 저장. 입력 원본을 그대로 persist 하면 audit 신뢰성이 깨진다.
- `MentorProfile.courses` 필드의 `@Builder.Default` + `= new HashSet<>()` 초기화가 있으므로, `clear()` 호출 전 `null` 체크는 불필요하다.
