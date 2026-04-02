# Phase 3: 테스트 & 멘토 매칭 — Backend 구현 계획서

> 작성일: 2026-04-02 | 프로젝트: DevMatch | 기반: ROADMAP.md Phase 3

---

## 1. 현재 상태 분석

### 완료된 항목 (Phase 2)

| 항목 | 상태 |
|------|------|
| User Entity + JWT 인증 | ✅ |
| MentorProfile Entity + 멘토 신청 | ✅ |
| Role (MENTEE/MENTOR/ADMIN), MentorStatus (PENDING/APPROVED/REJECTED) | ✅ |
| AuthService (회원가입/로그인/토큰갱신/로그아웃) | ✅ |
| UserService (마이페이지 조회/수정) | ✅ |
| MentorService (멘토 신청/프로필 조회) | ✅ |
| SecurityConfig (JWT Stateless, 경로별 접근 제어) | ✅ |
| GlobalExceptionHandler + 커스텀 예외 5종 | ✅ |
| Swagger (JWT Bearer 인증 스키마) | ✅ |

### Phase 3에서 활용할 기존 코드

| 기존 코드 | 활용 방식 |
|-----------|-----------|
| `User` Entity | 테스트 응시자, 매칭 신청자/수신자 FK |
| `MentorProfile` Entity | 멘토 추천 시 specialty/careerYears 기반 필터링 |
| `MentorStatus.APPROVED` | 승인된 멘토만 매칭 대상으로 노출 |
| `Role` Enum | MENTOR 역할만 매칭 수락/거절 가능 |
| `CustomUserDetails` | Controller에서 현재 사용자 정보 접근 |
| `ApiResponse<T>` | 모든 API 응답 래퍼 |
| `GlobalExceptionHandler` | 신규 예외 추가 등록 |

---

## 2. 구현 범위 요약

Phase 3에서 구현할 기능은 크게 두 가지 도메인으로 나뉩니다.

### A. 테스트 시스템

멘티의 실력 수준을 파악하기 위한 분야별 테스트를 제공하고, 자동 채점합니다.

- 테스트 목록 조회 (분야별 필터)
- 테스트 상세 + 문제 목록 조회
- 답안 제출 + 자동 채점
- 내 테스트 결과 목록 조회

### B. 멘토 매칭 시스템

테스트 결과를 기반으로 적합한 멘토를 추천하고, 매칭을 신청/수락/거절합니다.

- 테스트 결과 기반 멘토 추천
- 멘토에게 매칭 신청
- 멘토의 매칭 수락/거절
- 내 매칭 내역 조회

---

## 3. Entity 설계

### 3.1 Test Entity (테스트)

```
파일: entity/Test.java (신규)
테이블: tests
```

| 필드 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | Long | PK, Auto Increment | 기본키 |
| title | String | NOT NULL, max 200 | 테스트 제목 (예: "Java 기초 레벨 테스트") |
| description | String | nullable, max 2000 | 테스트 설명 |
| category | String | NOT NULL, max 50 | 분야 (예: "Java", "Spring", "React") |
| difficulty | Difficulty (Enum) | NOT NULL | BEGINNER / INTERMEDIATE / ADVANCED |
| timeLimit | Integer | NOT NULL | 제한 시간 (분) |
| passingScore | Integer | NOT NULL | 합격 기준 점수 (100점 만점) |
| questionCount | Integer | NOT NULL | 총 문제 수 |
| isActive | Boolean | NOT NULL, default true | 활성화 여부 |
| createdAt | LocalDateTime | NOT NULL | 생성일 |
| updatedAt | LocalDateTime | NOT NULL | 수정일 |

**설계 포인트:**
- category는 MentorProfile.specialty와 매칭에 사용됨
- questionCount는 문제 추가/삭제 시 동기화 필요
- isActive로 비활성 테스트 숨김 처리

### 3.2 Question Entity (문제)

```
파일: entity/Question.java (신규)
테이블: questions
```

| 필드 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | Long | PK, Auto Increment | 기본키 |
| test | Test | FK, @ManyToOne | 소속 테스트 |
| content | String | NOT NULL, max 2000 | 문제 내용 |
| options | List\<String\> | JSON 변환 | 선택지 목록 (4지선다) |
| correctAnswer | Integer | NOT NULL | 정답 인덱스 (0~3) |
| score | Integer | NOT NULL | 배점 |
| orderIndex | Integer | NOT NULL | 문제 순서 |
| createdAt | LocalDateTime | NOT NULL | 생성일 |

**설계 포인트:**
- options는 기존 `StringListConverter`를 재활용하여 JSON 변환
- correctAnswer는 options 리스트의 인덱스 (0부터 시작)
- orderIndex로 문제 출제 순서 관리
- score 합계 = 100점이 되도록 관리자가 설정

### 3.3 TestResult Entity (테스트 결과)

```
파일: entity/TestResult.java (신규)
테이블: test_results
```

| 필드 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | Long | PK, Auto Increment | 기본키 |
| user | User | FK, @ManyToOne | 응시자 |
| test | Test | FK, @ManyToOne | 응시한 테스트 |
| totalScore | Integer | NOT NULL | 획득 점수 (100점 만점) |
| correctCount | Integer | NOT NULL | 정답 수 |
| passed | Boolean | NOT NULL | 합격 여부 |
| submittedAt | LocalDateTime | NOT NULL | 제출 시각 |

**설계 포인트:**
- user + test 조합으로 동일 테스트 재응시 가능 (히스토리 누적)
- passed = (totalScore >= test.passingScore)
- 매칭 추천 시 가장 최근 TestResult의 category + totalScore 활용

### 3.4 TestAnswer Entity (제출 답안)

```
파일: entity/TestAnswer.java (신규)
테이블: test_answers
```

| 필드 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | Long | PK, Auto Increment | 기본키 |
| testResult | TestResult | FK, @ManyToOne | 소속 테스트 결과 |
| question | Question | FK, @ManyToOne | 해당 문제 |
| selectedAnswer | Integer | NOT NULL | 사용자가 선택한 답 (0~3) |
| isCorrect | Boolean | NOT NULL | 정답 여부 |

**설계 포인트:**
- 채점 후 문제별 정오답을 기록하여 오답 확인 가능
- testResult를 통해 전체 결과 조회 시 답안 상세도 함께 로드 가능

### 3.5 Matching Entity (매칭)

```
파일: entity/Matching.java (신규)
테이블: matchings
```

| 필드 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | Long | PK, Auto Increment | 기본키 |
| mentee | User | FK, @ManyToOne | 멘티 (신청자) |
| mentor | User | FK, @ManyToOne | 멘토 (수신자) |
| testResult | TestResult | FK, @ManyToOne, nullable | 매칭 근거 테스트 결과 |
| category | String | NOT NULL, max 50 | 매칭 분야 |
| message | String | nullable, max 500 | 멘티의 신청 메시지 |
| status | MatchingStatus (Enum) | NOT NULL, default PENDING | 매칭 상태 |
| rejectedReason | String | nullable, max 500 | 거절 사유 |
| createdAt | LocalDateTime | NOT NULL | 신청일 |
| updatedAt | LocalDateTime | NOT NULL | 수정일 |

**설계 포인트:**
- mentee와 mentor 모두 User FK → 동일 테이블 참조 (역할로 구분)
- testResult는 nullable → 테스트 없이도 직접 매칭 신청 가능
- category는 매칭 분야 (테스트 category와 동일 체계)
- 동일 멘토에게 PENDING 상태 중복 신청 방지 로직 필요

### 3.6 신규 Enum

```
파일: entity/Difficulty.java (신규)
```

| 값 | 설명 |
|----|------|
| BEGINNER | 입문 |
| INTERMEDIATE | 중급 |
| ADVANCED | 고급 |

```
파일: entity/MatchingStatus.java (신규)
```

| 값 | 설명 |
|----|------|
| PENDING | 대기 중 |
| ACCEPTED | 수락 |
| REJECTED | 거절 |
| CANCELLED | 취소 (멘티가 취소) |

---

## 4. Repository 설계

### 4.1 TestRepository

```
파일: repository/TestRepository.java (신규)
```

| 메서드 | 반환타입 | 설명 |
|--------|----------|------|
| findByCategoryAndIsActiveTrue(String category) | List\<Test\> | 분야별 활성 테스트 목록 |
| findByIsActiveTrue() | List\<Test\> | 전체 활성 테스트 목록 |

### 4.2 QuestionRepository

```
파일: repository/QuestionRepository.java (신규)
```

| 메서드 | 반환타입 | 설명 |
|--------|----------|------|
| findByTestIdOrderByOrderIndexAsc(Long testId) | List\<Question\> | 테스트의 문제 목록 (순서대로) |

### 4.3 TestResultRepository

```
파일: repository/TestResultRepository.java (신규)
```

| 메서드 | 반환타입 | 설명 |
|--------|----------|------|
| findByUserIdOrderBySubmittedAtDesc(Long userId) | List\<TestResult\> | 내 테스트 결과 목록 (최신순) |
| findTopByUserIdAndTest_CategoryOrderBySubmittedAtDesc(Long userId, String category) | Optional\<TestResult\> | 특정 분야 최근 결과 |

### 4.4 TestAnswerRepository

```
파일: repository/TestAnswerRepository.java (신규)
```

| 메서드 | 반환타입 | 설명 |
|--------|----------|------|
| findByTestResultId(Long testResultId) | List\<TestAnswer\> | 특정 결과의 답안 목록 |

### 4.5 MatchingRepository

```
파일: repository/MatchingRepository.java (신규)
```

| 메서드 | 반환타입 | 설명 |
|--------|----------|------|
| findByMenteeIdOrderByCreatedAtDesc(Long menteeId) | List\<Matching\> | 멘티의 매칭 신청 내역 |
| findByMentorIdOrderByCreatedAtDesc(Long mentorId) | List\<Matching\> | 멘토가 받은 매칭 요청 |
| findByMentorIdAndStatus(Long mentorId, MatchingStatus status) | List\<Matching\> | 멘토의 상태별 매칭 목록 |
| existsByMenteeIdAndMentorIdAndStatus(Long menteeId, Long mentorId, MatchingStatus status) | boolean | 중복 신청 확인 |

---

## 5. DTO 설계

### 5.1 테스트 DTO (dto/test/)

```
파일: dto/test/TestListResponse.java (신규)
```

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 테스트 ID |
| title | String | 제목 |
| category | String | 분야 |
| difficulty | String | 난이도 |
| timeLimit | Integer | 제한 시간 (분) |
| questionCount | Integer | 문제 수 |
| passingScore | Integer | 합격 기준 점수 |

```
파일: dto/test/TestDetailResponse.java (신규)
```

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 테스트 ID |
| title | String | 제목 |
| description | String | 설명 |
| category | String | 분야 |
| difficulty | String | 난이도 |
| timeLimit | Integer | 제한 시간 (분) |
| passingScore | Integer | 합격 기준 점수 |
| questions | List\<QuestionResponse\> | 문제 목록 |

```
파일: dto/test/QuestionResponse.java (신규)
```

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 문제 ID |
| content | String | 문제 내용 |
| options | List\<String\> | 선택지 |
| score | Integer | 배점 |
| orderIndex | Integer | 문제 순서 |

> **주의:** 응시 화면에서는 correctAnswer를 포함하지 않습니다.

```
파일: dto/test/TestSubmitRequest.java (신규)
```

| 필드 | 타입 | 검증 | 설명 |
|------|------|------|------|
| answers | List\<AnswerRequest\> | @NotEmpty | 답안 목록 |

```
파일: dto/test/AnswerRequest.java (신규)
```

| 필드 | 타입 | 검증 | 설명 |
|------|------|------|------|
| questionId | Long | @NotNull | 문제 ID |
| selectedAnswer | Integer | @NotNull, @Min(0), @Max(3) | 선택한 답 |

```
파일: dto/test/TestResultResponse.java (신규)
```

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 결과 ID |
| testId | Long | 테스트 ID |
| testTitle | String | 테스트 제목 |
| category | String | 분야 |
| totalScore | Integer | 획득 점수 |
| correctCount | Integer | 정답 수 |
| questionCount | Integer | 총 문제 수 |
| passed | Boolean | 합격 여부 |
| submittedAt | LocalDateTime | 제출 시각 |

### 5.2 매칭 DTO (dto/matching/)

```
파일: dto/matching/MentorRecommendResponse.java (신규)
```

| 필드 | 타입 | 설명 |
|------|------|------|
| mentorId | Long | 멘토의 User ID |
| name | String | 멘토 이름 |
| specialty | List\<String\> | 전문 분야 |
| careerYears | Integer | 경력 연수 |
| company | String | 회사 |
| bio | String | 자기 소개 |
| matchScore | Integer | 매칭 적합도 점수 (0~100) |

```
파일: dto/matching/MatchingRequest.java (신규)
```

| 필드 | 타입 | 검증 | 설명 |
|------|------|------|------|
| mentorId | Long | @NotNull | 멘토 User ID |
| category | String | @NotBlank | 매칭 분야 |
| testResultId | Long | nullable | 테스트 결과 ID (선택) |
| message | String | @Size(max=500) | 신청 메시지 |

```
파일: dto/matching/MatchingResponse.java (신규)
```

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 매칭 ID |
| menteeId | Long | 멘티 ID |
| menteeName | String | 멘티 이름 |
| mentorId | Long | 멘토 ID |
| mentorName | String | 멘토 이름 |
| category | String | 매칭 분야 |
| message | String | 신청 메시지 |
| status | String | 매칭 상태 |
| rejectedReason | String | 거절 사유 |
| testScore | Integer | 테스트 점수 (있는 경우) |
| createdAt | LocalDateTime | 신청일 |

```
파일: dto/matching/MatchingAcceptRequest.java (신규)
```

| 필드 | 타입 | 검증 | 설명 |
|------|------|------|------|
| accepted | Boolean | @NotNull | 수락(true) / 거절(false) |
| rejectedReason | String | @Size(max=500) | 거절 사유 (거절 시 필수) |

---

## 6. Service 설계

### 6.1 TestService

```
파일: service/TestService.java (신규)
```

| 메서드 | 반환타입 | 설명 |
|--------|----------|------|
| getTests(String category) | List\<TestListResponse\> | 테스트 목록 조회 (category null이면 전체) |
| getTestDetail(Long testId) | TestDetailResponse | 테스트 상세 + 문제 목록 (정답 제외) |
| submitTest(Long userId, Long testId, TestSubmitRequest) | TestResultResponse | 답안 채점 + 결과 저장 |
| getMyResults(Long userId) | List\<TestResultResponse\> | 내 테스트 결과 목록 |

**submitTest 채점 로직:**
1. testId로 Test + Questions 조회
2. 각 답안의 questionId로 Question 찾기
3. selectedAnswer와 correctAnswer 비교 → 정답이면 score 합산
4. TestResult 생성 (totalScore, correctCount, passed 계산)
5. TestAnswer 목록 일괄 저장
6. TestResultResponse 반환

### 6.2 MatchingService

```
파일: service/MatchingService.java (신규)
```

| 메서드 | 반환타입 | 설명 |
|--------|----------|------|
| recommendMentors(Long userId, String category) | List\<MentorRecommendResponse\> | 테스트 결과 기반 멘토 추천 |
| requestMatching(Long menteeId, MatchingRequest) | MatchingResponse | 멘토에게 매칭 신청 |
| acceptMatching(Long mentorId, Long matchingId, MatchingAcceptRequest) | MatchingResponse | 매칭 수락/거절 |
| getMyMatchingsAsMentee(Long userId) | List\<MatchingResponse\> | 멘티 입장 매칭 내역 |
| getMyMatchingsAsMentor(Long userId) | List\<MatchingResponse\> | 멘토 입장 매칭 요청 목록 |

**recommendMentors 추천 로직:**
1. 해당 category의 APPROVED 멘토 목록 조회 (specialty에 category 포함)
2. 사용자의 해당 category 최근 TestResult 조회
3. matchScore 계산:
   - 멘토 specialty에 해당 분야 포함 → +40점
   - 멘토 경력 연수 기반 → 경력 많을수록 가산 (최대 +30점)
   - 테스트 난이도와 멘토 수준 적합도 → 최대 +30점
4. matchScore 내림차순 정렬 후 반환

---

## 7. Controller (API 엔드포인트) 설계

### 7.1 TestController

```
파일: controller/TestController.java (신규)
경로: /api/tests
```

| HTTP | 경로 | 메서드 | 인증 | 설명 |
|------|------|--------|------|------|
| GET | / | getTests() | 필요 | 테스트 목록 (쿼리: ?category=Java) |
| GET | /{id} | getTestDetail() | 필요 | 테스트 상세 + 문제 목록 |
| POST | /{id}/submit | submitTest() | 필요 | 답안 제출 + 자동 채점 |
| GET | /results | getMyResults() | 필요 | 내 테스트 결과 목록 |

### 7.2 MatchingController

```
파일: controller/MatchingController.java (신규)
경로: /api/matching
```

| HTTP | 경로 | 메서드 | 인증 | 설명 |
|------|------|--------|------|------|
| GET | /recommend | recommendMentors() | 필요 | 멘토 추천 (쿼리: ?category=Java) |
| POST | /request | requestMatching() | 필요 | 매칭 신청 |
| PUT | /{id}/accept | acceptMatching() | 필요 (MENTOR) | 매칭 수락/거절 |
| GET | /mentee | getMyMatchingsAsMentee() | 필요 | 멘티 입장 매칭 내역 |
| GET | /mentor | getMyMatchingsAsMentor() | 필요 (MENTOR) | 멘토 입장 매칭 요청 |

---

## 8. 예외 처리 설계

### 기존 GlobalExceptionHandler에 추가할 커스텀 예외

| 예외 클래스 | HTTP 상태 | 사용 상황 |
|-------------|-----------|-----------|
| TestNotFoundException | 404 Not Found | 존재하지 않는 테스트 조회 시 |
| MatchingNotFoundException | 404 Not Found | 존재하지 않는 매칭 조회 시 |
| DuplicateMatchingException | 409 Conflict | 동일 멘토에게 PENDING 상태 중복 신청 시 |
| UnauthorizedMatchingException | 403 Forbidden | 매칭 수락 권한이 없는 경우 (본인 매칭이 아님) |

---

## 9. 기존 코드 수정 사항

### 9.1 SecurityConfig.java 수정

테스트 목록 조회는 인증 필요 (기존 `anyRequest().authenticated()` 규칙으로 자동 적용).
추가 수정 불필요 — 모든 `/api/**` 경로는 이미 인증 필수.

### 9.2 MentorProfileRepository.java 수정

멘토 추천을 위해 쿼리 메서드 추가 필요:

| 추가 메서드 | 반환타입 | 설명 |
|-------------|----------|------|
| findByStatusAndSpecialtyContaining(MentorStatus status, String category) | List\<MentorProfile\> | 승인된 멘토 중 해당 분야 전문가 조회 |

> **참고:** specialty가 JSON으로 저장되므로 `LIKE '%category%'` 방식 대신 전체 APPROVED 멘토 조회 후 Java에서 필터링하는 것이 안정적.

실제 구현:
```
findByStatus(MentorStatus.APPROVED)  →  Java stream에서 specialty.contains(category) 필터링
```

---

## 10. 신규 생성 파일 목록

| 파일 경로 | 설명 |
|-----------|------|
| `entity/Difficulty.java` | 테스트 난이도 Enum |
| `entity/MatchingStatus.java` | 매칭 상태 Enum |
| `entity/Test.java` | 테스트 엔티티 |
| `entity/Question.java` | 문제 엔티티 |
| `entity/TestResult.java` | 테스트 결과 엔티티 |
| `entity/TestAnswer.java` | 제출 답안 엔티티 |
| `entity/Matching.java` | 매칭 엔티티 |
| `repository/TestRepository.java` | 테스트 Repository |
| `repository/QuestionRepository.java` | 문제 Repository |
| `repository/TestResultRepository.java` | 테스트 결과 Repository |
| `repository/TestAnswerRepository.java` | 답안 Repository |
| `repository/MatchingRepository.java` | 매칭 Repository |
| `dto/test/TestListResponse.java` | 테스트 목록 응답 |
| `dto/test/TestDetailResponse.java` | 테스트 상세 응답 |
| `dto/test/QuestionResponse.java` | 문제 응답 (정답 미포함) |
| `dto/test/TestSubmitRequest.java` | 답안 제출 요청 |
| `dto/test/AnswerRequest.java` | 개별 답안 요청 |
| `dto/test/TestResultResponse.java` | 테스트 결과 응답 |
| `dto/matching/MentorRecommendResponse.java` | 멘토 추천 응답 |
| `dto/matching/MatchingRequest.java` | 매칭 신청 요청 |
| `dto/matching/MatchingResponse.java` | 매칭 응답 |
| `dto/matching/MatchingAcceptRequest.java` | 매칭 수락/거절 요청 |
| `service/TestService.java` | 테스트 서비스 |
| `service/MatchingService.java` | 매칭 서비스 |
| `controller/TestController.java` | 테스트 컨트롤러 |
| `controller/MatchingController.java` | 매칭 컨트롤러 |
| `exception/TestNotFoundException.java` | 테스트 미존재 예외 |
| `exception/MatchingNotFoundException.java` | 매칭 미존재 예외 |
| `exception/DuplicateMatchingException.java` | 중복 매칭 예외 |
| `exception/UnauthorizedMatchingException.java` | 매칭 권한 예외 |

총 30개 파일 신규 생성

---

## 11. 구현 순서 (권장)

### Step 1: Enum + Entity (기반 계층)

1. `Difficulty.java`, `MatchingStatus.java` — Enum 정의
2. `Test.java` — 테스트 엔티티
3. `Question.java` — 문제 엔티티
4. `TestResult.java` — 테스트 결과 엔티티
5. `TestAnswer.java` — 답안 엔티티
6. `Matching.java` — 매칭 엔티티

**검증:** 애플리케이션 시작 → DDL 자동 생성 확인

### Step 2: Repository

1. `TestRepository.java`
2. `QuestionRepository.java`
3. `TestResultRepository.java`
4. `TestAnswerRepository.java`
5. `MatchingRepository.java`

**검증:** 컴파일 확인

### Step 3: DTO + 예외

1. 테스트 DTO 6개
2. 매칭 DTO 4개
3. 커스텀 예외 4개
4. `GlobalExceptionHandler.java`에 신규 예외 핸들러 추가

**검증:** 컴파일 확인

### Step 4: 테스트 시스템 (Service + Controller)

1. `TestService.java` — getTests, getTestDetail, submitTest, getMyResults
2. `TestController.java` — 4개 API

**검증:** Swagger에서 테스트 목록 조회 → 상세 조회 → 답안 제출 → 결과 확인

### Step 5: 매칭 시스템 (Service + Controller)

1. `MatchingService.java` — recommendMentors, requestMatching, acceptMatching, getMyMatchings
2. `MatchingController.java` — 5개 API

**검증:** 멘토 추천 → 매칭 신청 → 수락/거절 → 내역 확인

### Step 6: 테스트 데이터 (선택)

초기 테스트 데이터를 위한 `data.sql` 또는 `DataInitializer` 작성 (개발 편의).

---

## 12. API 흐름도

### 테스트 응시 흐름

```
1. 멘티 → GET /api/tests?category=Java
   ← 테스트 목록 (Java 기초, Java 중급 ...)

2. 멘티 → GET /api/tests/1
   ← 테스트 상세 + 문제 목록 (정답 미포함)

3. 멘티 → POST /api/tests/1/submit { answers: [{questionId: 1, selectedAnswer: 2}, ...] }
   → TestService.submitTest()
     → 문제별 정답 비교 → 점수 합산
     → TestResult 저장 + TestAnswer 일괄 저장
   ← { totalScore: 75, correctCount: 8, passed: true }

4. 멘티 → GET /api/tests/results
   ← 내 테스트 결과 목록 (최신순)
```

### 매칭 흐름

```
1. 멘티 → GET /api/matching/recommend?category=Java
   → MatchingService.recommendMentors()
     → APPROVED 멘토 중 Java 전문가 필터링
     → 멘티의 Java 테스트 결과 조회
     → matchScore 계산 및 정렬
   ← 멘토 추천 목록 [{name, specialty, careerYears, matchScore}, ...]

2. 멘티 → POST /api/matching/request { mentorId: 5, category: "Java", message: "..." }
   → MatchingService.requestMatching()
     → 중복 PENDING 신청 확인
     → Matching 생성 (status: PENDING)
   ← { id, mentorName, status: "PENDING" }

3. 멘토 → GET /api/matching/mentor
   ← 받은 매칭 요청 목록

4. 멘토 → PUT /api/matching/3/accept { accepted: true }
   → MatchingService.acceptMatching()
     → 본인 매칭인지 확인
     → status → ACCEPTED
   ← { status: "ACCEPTED" }
```

---

## 13. DB 스키마 (예상)

```sql
CREATE TABLE tests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    category VARCHAR(50) NOT NULL,
    difficulty VARCHAR(20) NOT NULL,
    time_limit INT NOT NULL,
    passing_score INT NOT NULL,
    question_count INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);

CREATE TABLE questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    test_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    options TEXT,
    correct_answer INT NOT NULL,
    score INT NOT NULL,
    order_index INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    FOREIGN KEY (test_id) REFERENCES tests(id)
);

CREATE TABLE test_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    test_id BIGINT NOT NULL,
    total_score INT NOT NULL,
    correct_count INT NOT NULL,
    passed BOOLEAN NOT NULL,
    submitted_at DATETIME(6) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (test_id) REFERENCES tests(id)
);

CREATE TABLE test_answers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    test_result_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    selected_answer INT NOT NULL,
    is_correct BOOLEAN NOT NULL,
    FOREIGN KEY (test_result_id) REFERENCES test_results(id),
    FOREIGN KEY (question_id) REFERENCES questions(id)
);

CREATE TABLE matchings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mentee_id BIGINT NOT NULL,
    mentor_id BIGINT NOT NULL,
    test_result_id BIGINT,
    category VARCHAR(50) NOT NULL,
    message VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rejected_reason VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    FOREIGN KEY (mentee_id) REFERENCES users(id),
    FOREIGN KEY (mentor_id) REFERENCES users(id),
    FOREIGN KEY (test_result_id) REFERENCES test_results(id)
);
```

---

## 14. 참고 사항

### 테스트 데이터 필요

테스트 시스템은 문제 데이터가 없으면 동작하지 않습니다. 개발 단계에서는 다음 방법 중 하나를 선택합니다:

- **방법 A:** `src/main/resources/data.sql`에 INSERT 문 작성
- **방법 B:** `@Component` DataInitializer 클래스에서 `@PostConstruct`로 초기 데이터 삽입
- **방법 C:** Admin API (Phase 6)로 테스트/문제 등록 — 현 단계에서는 미구현

**권장:** 방법 B — 코드로 관리하여 재현성 확보

### 매칭 추천 알고리즘 확장 가능성

현재 단계에서는 단순 점수 기반 추천이지만, 추후 확장 가능:
- 멘토 리뷰 점수 반영
- 멘토링 이력 기반 추천
- 시간대/선호 언어 매칭

### Phase 4와의 연결

매칭이 ACCEPTED되면 Phase 4에서 Google Calendar에 멘토링 일정을 자동 등록합니다.
현 단계에서는 상태 변경(ACCEPTED)까지만 처리합니다.
