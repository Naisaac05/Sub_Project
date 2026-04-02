# Phase 3: 테스트 & 멘토 매칭 — Backend 구현 결과서

> 구현일: 2026-04-02 | 프로젝트: DevMatch | 기반: 04-phase3-test-matching-plan.md

---

## 1. 구현 완료 요약

Phase 3 테스트 & 멘토 매칭 백엔드 전체 구현을 완료했습니다.
`gradle compileJava` 빌드 성공 확인 완료.

| 항목 | 수량 |
|------|------|
| Phase 3 신규 Java 파일 | 30개 |
| Phase 3 수정 Java 파일 | 1개 (GlobalExceptionHandler) |
| 신규 API 엔드포인트 | 9개 |
| 전체 Java 파일 (Phase 2 + 3) | 67개 |
| 전체 API 엔드포인트 (Phase 2 + 3) | 17개 |

---

## 2. 프로젝트 구조 (Phase 3 신규 부분)

```
backend/src/main/java/com/devmatch/
├── entity/
│   ├── Difficulty.java          ← 신규 Enum
│   ├── MatchingStatus.java      ← 신규 Enum
│   ├── Test.java                ← 신규 Entity
│   ├── Question.java            ← 신규 Entity
│   ├── TestResult.java          ← 신규 Entity
│   ├── TestAnswer.java          ← 신규 Entity
│   └── Matching.java            ← 신규 Entity
├── repository/
│   ├── TestRepository.java      ← 신규
│   ├── QuestionRepository.java  ← 신규
│   ├── TestResultRepository.java ← 신규
│   ├── TestAnswerRepository.java ← 신규
│   └── MatchingRepository.java  ← 신규
├── dto/
│   ├── test/
│   │   ├── TestListResponse.java     ← 신규
│   │   ├── TestDetailResponse.java   ← 신규
│   │   ├── QuestionResponse.java     ← 신규
│   │   ├── TestSubmitRequest.java    ← 신규
│   │   ├── AnswerRequest.java        ← 신규
│   │   └── TestResultResponse.java   ← 신규
│   └── matching/
│       ├── MentorRecommendResponse.java  ← 신규
│       ├── MatchingRequest.java          ← 신규
│       ├── MatchingResponse.java         ← 신규
│       └── MatchingAcceptRequest.java    ← 신규
├── service/
│   ├── TestService.java         ← 신규
│   └── MatchingService.java     ← 신규
├── controller/
│   ├── TestController.java      ← 신규
│   └── MatchingController.java  ← 신규
└── exception/
    ├── TestNotFoundException.java         ← 신규
    ├── MatchingNotFoundException.java     ← 신규
    ├── DuplicateMatchingException.java    ← 신규
    ├── UnauthorizedMatchingException.java ← 신규
    └── GlobalExceptionHandler.java        ← 수정 (핸들러 4개 추가)
```

---

## 3. Entity 계층

### 3.1 Enum

| Enum | 값 | 설명 |
|------|----|------|
| `Difficulty` | BEGINNER, INTERMEDIATE, ADVANCED | 테스트 난이도 |
| `MatchingStatus` | PENDING, ACCEPTED, REJECTED, CANCELLED | 매칭 상태 |

### 3.2 Test.java

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long (PK) | 기본키 |
| title | String (max 200) | 테스트 제목 |
| description | String (max 2000) | 테스트 설명 |
| category | String (max 50) | 분야 (Java, Spring, React 등) |
| difficulty | Difficulty (Enum) | 난이도 |
| timeLimit | Integer | 제한 시간 (분) |
| passingScore | Integer | 합격 기준 점수 (100점 만점) |
| questionCount | Integer | 총 문제 수 |
| isActive | Boolean (default true) | 활성화 여부 |
| createdAt / updatedAt | LocalDateTime | JPA Auditing |

### 3.3 Question.java

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long (PK) | 기본키 |
| test | Test (FK, ManyToOne) | 소속 테스트 |
| content | String (max 2000) | 문제 내용 |
| options | List\<String\> (JSON) | 선택지 (4지선다), StringListConverter 사용 |
| correctAnswer | Integer | 정답 인덱스 (0~3) |
| score | Integer | 배점 |
| orderIndex | Integer | 문제 순서 |
| createdAt | LocalDateTime | 생성일 |

### 3.4 TestResult.java

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long (PK) | 기본키 |
| user | User (FK, ManyToOne) | 응시자 |
| test | Test (FK, ManyToOne) | 응시한 테스트 |
| totalScore | Integer | 획득 점수 |
| correctCount | Integer | 정답 수 |
| passed | Boolean | 합격 여부 |
| submittedAt | LocalDateTime | 제출 시각 |

### 3.5 TestAnswer.java

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long (PK) | 기본키 |
| testResult | TestResult (FK, ManyToOne) | 소속 결과 |
| question | Question (FK, ManyToOne) | 해당 문제 |
| selectedAnswer | Integer | 선택한 답 (0~3) |
| isCorrect | Boolean | 정답 여부 |

### 3.6 Matching.java

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long (PK) | 기본키 |
| mentee | User (FK, ManyToOne) | 멘티 (신청자) |
| mentor | User (FK, ManyToOne) | 멘토 (수신자) |
| testResult | TestResult (FK, nullable) | 매칭 근거 테스트 결과 |
| category | String (max 50) | 매칭 분야 |
| message | String (max 500) | 신청 메시지 |
| status | MatchingStatus (Enum, default PENDING) | 매칭 상태 |
| rejectedReason | String (max 500) | 거절 사유 |
| createdAt / updatedAt | LocalDateTime | JPA Auditing |

**변경 메서드:**
- `accept()` → status를 ACCEPTED로 변경
- `reject(reason)` → status를 REJECTED로 변경 + 거절 사유 저장

---

## 4. API 엔드포인트 (Phase 3 신규 9개)

### 4.1 TestController — `/api/tests`

| HTTP | 경로 | 인증 | 요청 | 응답 | 설명 |
|------|------|------|------|------|------|
| GET | / | O | ?category (선택) | ApiResponse\<List\<TestListResponse\>\> | 테스트 목록 |
| GET | /{id} | O | — | ApiResponse\<TestDetailResponse\> | 테스트 상세 + 문제 (정답 미포함) |
| POST | /{id}/submit | O | TestSubmitRequest | ApiResponse\<TestResultResponse\> | 답안 제출 + 자동 채점 |
| GET | /results | O | — | ApiResponse\<List\<TestResultResponse\>\> | 내 결과 목록 |

### 4.2 MatchingController — `/api/matching`

| HTTP | 경로 | 인증 | 요청 | 응답 | 설명 |
|------|------|------|------|------|------|
| GET | /recommend | O | ?category (필수) | ApiResponse\<List\<MentorRecommendResponse\>\> | 멘토 추천 |
| POST | /request | O | MatchingRequest | ApiResponse\<MatchingResponse\> | 매칭 신청 |
| PUT | /{id}/accept | O | MatchingAcceptRequest | ApiResponse\<MatchingResponse\> | 수락/거절 |
| GET | /mentee | O | — | ApiResponse\<List\<MatchingResponse\>\> | 멘티 매칭 내역 |
| GET | /mentor | O | — | ApiResponse\<List\<MatchingResponse\>\> | 멘토 매칭 요청 |

---

## 5. 핵심 비즈니스 로직

### 5.1 자동 채점 (TestService.submitTest)

```
1. testId로 Test + Question 목록 조회
2. Question을 Map<questionId, Question>으로 변환
3. 각 답안에 대해:
   - questionMap에서 문제 조회
   - selectedAnswer == correctAnswer → 정답 (score 합산)
   - GradedAnswer 중간 결과 저장
4. TestResult 생성 (totalScore, correctCount, passed 계산)
5. TestAnswer 일괄 저장
6. TestResultResponse 반환
```

### 5.2 멘토 추천 알고리즘 (MatchingService.recommendMentors)

```
1. APPROVED 멘토 중 specialty에 해당 category 포함된 멘토 필터링
2. 사용자의 해당 category 최근 TestResult 조회
3. matchScore 계산 (0~100):
   - 전문 분야 일치: +40점
   - 경력 기반 가산: 1년당 3점 (최대 +30점)
   - 테스트 점수 기반 적합도: totalScore * 30 / 100 (최대 +30점)
4. matchScore 내림차순 정렬 후 반환
```

### 5.3 매칭 수락/거절 (MatchingService.acceptMatching)

```
1. matchingId로 Matching 조회
2. 본인(mentorId)에게 온 매칭인지 확인 → 아니면 403
3. PENDING 상태인지 확인 → 아니면 403
4. accepted == true → matching.accept()
   accepted == false → matching.reject(reason)
5. MatchingResponse 반환
```

---

## 6. 예외 처리 (Phase 3 추가)

| 예외 | HTTP | 발생 상황 |
|------|------|-----------|
| `TestNotFoundException` | 404 | 존재하지 않는 테스트 조회/제출 |
| `MatchingNotFoundException` | 404 | 존재하지 않는 매칭 조회 |
| `DuplicateMatchingException` | 409 | 동일 멘토에게 PENDING 중복 신청 |
| `UnauthorizedMatchingException` | 403 | 매칭 수락 권한 없음 / 미승인 멘토에게 신청 |

---

## 7. DB 스키마 (Phase 3 신규 5개 테이블)

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

## 8. API 흐름도

### 테스트 응시 흐름

```
GET /api/tests?category=Java
  ← [{id:1, title:"Java 기초", difficulty:"BEGINNER", questionCount:10, ...}]

GET /api/tests/1
  ← {title:"Java 기초", questions:[{id:1, content:"...", options:["A","B","C","D"], score:10}, ...]}

POST /api/tests/1/submit
  → {answers: [{questionId:1, selectedAnswer:2}, {questionId:2, selectedAnswer:0}, ...]}
  ← {totalScore:75, correctCount:8, questionCount:10, passed:true}

GET /api/tests/results
  ← [{testTitle:"Java 기초", totalScore:75, passed:true, submittedAt:"..."}, ...]
```

### 매칭 흐름

```
GET /api/matching/recommend?category=Java
  ← [{mentorId:5, name:"김멘토", specialty:["Java","Spring"], matchScore:85}, ...]

POST /api/matching/request
  → {mentorId:5, category:"Java", testResultId:1, message:"Java 학습 도움 부탁드립니다"}
  ← {id:1, mentorName:"김멘토", status:"PENDING"}

GET /api/matching/mentor   (멘토 입장)
  ← [{id:1, menteeName:"이멘티", category:"Java", testScore:75, status:"PENDING"}, ...]

PUT /api/matching/1/accept  (멘토가 수락)
  → {accepted: true}
  ← {status:"ACCEPTED"}
```

---

## 9. 테스트 시 참고사항

테스트 시스템은 문제 데이터가 필요합니다. `data.sql` 또는 `DataInitializer`로 초기 데이터를 삽입해야 API 테스트가 가능합니다.

### 테스트 순서

1. 회원가입 + 로그인 (Phase 2 API)
2. GET `/api/tests` → 테스트 목록 확인 (데이터 필요)
3. GET `/api/tests/{id}` → 문제 확인
4. POST `/api/tests/{id}/submit` → 답안 제출 + 채점
5. GET `/api/tests/results` → 결과 확인
6. GET `/api/matching/recommend?category=Java` → 멘토 추천 (APPROVED 멘토 필요)
7. POST `/api/matching/request` → 매칭 신청
8. PUT `/api/matching/{id}/accept` → 멘토 계정으로 수락/거절

---

## 10. 향후 작업

- Phase 4: Google Calendar + Meet 연동 (매칭 ACCEPTED 시 자동 일정 등록)
- Phase 6: Admin API에서 테스트/문제 CRUD, 멘토 승인/거절 관리
- 테스트 데이터 초기화 (`DataInitializer` 또는 Admin API)
- 매칭 추천 알고리즘 고도화 (리뷰 점수, 시간대 등)
