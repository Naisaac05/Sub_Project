# 테스트 문제 데이터 초기화 — 실행 결과서

> 실행일: 2026-04-04 | 프로젝트: DevMatch | 기반: test-data-initialization-plan.md

---

## 1. 실행 환경

| 항목 | 값 |
|------|-----|
| Spring Boot | 3.4.4 |
| Java | 17.0.15 |
| MySQL | 8.0.45 (Docker, port 3307) |
| Redis | 7-alpine (Docker, port 6379) |
| Hibernate | 6.6.11.Final |

---

## 2. DataInitializer 실행 로그

```
2026-04-04T11:19:05 INFO  DataInitializer : ===== 테스트 초기 데이터 삽입 시작 =====
2026-04-04T11:19:07 INFO  DataInitializer : 멘토 초기 데이터 5명 삽입 완료
2026-04-04T11:19:07 INFO  DataInitializer : ===== 초기 데이터 삽입 완료: 테스트 15개, 문제 150개 =====
```

- 삽입 소요 시간: 약 2초
- 에러/경고: 없음

---

## 3. 삽입 데이터 확인

### 3.1 테스트 (15개)

| ID | 카테고리 | 난이도 | 제목 | 문항 | 제한시간 | 합격점 |
|----|----------|--------|------|------|----------|--------|
| 1 | Java | BEGINNER | Java 기초 문법 테스트 | 10 | 15분 | 60 |
| 2 | Java | INTERMEDIATE | Java 중급 심화 테스트 | 10 | 20분 | 65 |
| 3 | Java | ADVANCED | Java 고급 아키텍처 테스트 | 10 | 25분 | 70 |
| 4 | Spring | BEGINNER | Spring 입문 테스트 | 10 | 15분 | 60 |
| 5 | Spring | INTERMEDIATE | Spring 중급 실무 테스트 | 10 | 20분 | 65 |
| 6 | Spring | ADVANCED | Spring 고급 아키텍처 테스트 | 10 | 25분 | 70 |
| 7 | React | BEGINNER | React 기초 테스트 | 10 | 15분 | 60 |
| 8 | React | INTERMEDIATE | React 중급 실무 테스트 | 10 | 20분 | 65 |
| 9 | React | ADVANCED | React 고급 심화 테스트 | 10 | 25분 | 70 |
| 10 | Python | BEGINNER | Python 기초 문법 테스트 | 10 | 15분 | 60 |
| 11 | Python | INTERMEDIATE | Python 중급 심화 테스트 | 10 | 20분 | 65 |
| 12 | Python | ADVANCED | Python 고급 아키텍처 테스트 | 10 | 25분 | 70 |
| 13 | Algorithm | BEGINNER | 알고리즘 기초 테스트 | 10 | 15분 | 60 |
| 14 | Algorithm | INTERMEDIATE | 알고리즘 중급 테스트 | 10 | 20분 | 65 |
| 15 | Algorithm | ADVANCED | 알고리즘 고급 테스트 | 10 | 25분 | 70 |

### 3.2 문제 (150문항)

- 테스트당 10문항, 4지선다, 각 10점 (100점 만점)
- correctAnswer 인덱스 0~3, orderIndex 1~10

### 3.3 멘토 계정 (5명, APPROVED)

| ID | 이름 | 이메일 | 전문분야 | 경력 | 소속 |
|----|------|--------|----------|------|------|
| 1 | 김자바 | java.mentor@devmatch.com | Java, Spring | 8년 | 네이버 |
| 2 | 이스프링 | spring.mentor@devmatch.com | Spring, DevOps | 5년 | 카카오 |
| 3 | 박리액트 | react.mentor@devmatch.com | React, Node.js | 6년 | 라인 |
| 4 | 최파이썬 | python.mentor@devmatch.com | Python, Algorithm | 7년 | 쿠팡 |
| 5 | 정풀스택 | fullstack.mentor@devmatch.com | Java, React, Spring | 10년 | 토스 |

- 공통 비밀번호: `mentor1234!`
- Role: `MENTOR`, MentorStatus: `APPROVED`

---

## 4. API 검증 결과

### 4.1 테스트 목록 조회

| API | 요청 | 응답 | 결과 |
|-----|------|------|------|
| `GET /api/tests` | 전체 | 15개 테스트 반환 | PASS |
| `GET /api/tests?category=Java` | Java 필터 | 3개 반환 (id: 1, 2, 3) | PASS |
| `GET /api/tests?category=Spring` | Spring 필터 | 3개 반환 (id: 4, 5, 6) | PASS |
| `GET /api/tests?category=React` | React 필터 | 3개 반환 (id: 7, 8, 9) | PASS |
| `GET /api/tests?category=Python` | Python 필터 | 3개 반환 (id: 10, 11, 12) | PASS |
| `GET /api/tests?category=Algorithm` | Algorithm 필터 | 3개 반환 (id: 13, 14, 15) | PASS |

### 4.2 테스트 상세 조회

| API | 검증 항목 | 결과 |
|-----|-----------|------|
| `GET /api/tests/1` | 문제 10개 포함 | PASS |
| | 각 문제에 options 배열 4개 | PASS |
| | correctAnswer 미포함 (보안) | PASS |
| | orderIndex 1~10 순서 정렬 | PASS |

**응답 샘플 (문제 1):**
```json
{
  "id": 1,
  "content": "Java에서 정수형 변수를 선언하는 올바른 방법은?",
  "options": [
    "int number = 10;",
    "number int = 10;",
    "integer number = 10;",
    "var: int number = 10;"
  ],
  "score": 10,
  "orderIndex": 1
}
```

### 4.3 답안 제출 + 자동 채점

**테스트 케이스: Java 기초 테스트 (id=1) — 전문항 정답 제출**

요청:
```json
{
  "answers": [
    {"questionId": 1, "selectedAnswer": 0},
    {"questionId": 2, "selectedAnswer": 1},
    {"questionId": 3, "selectedAnswer": 2},
    {"questionId": 4, "selectedAnswer": 1},
    {"questionId": 5, "selectedAnswer": 1},
    {"questionId": 6, "selectedAnswer": 1},
    {"questionId": 7, "selectedAnswer": 3},
    {"questionId": 8, "selectedAnswer": 2},
    {"questionId": 9, "selectedAnswer": 1},
    {"questionId": 10, "selectedAnswer": 1}
  ]
}
```

응답:
```json
{
  "success": true,
  "message": "채점이 완료되었습니다",
  "data": {
    "id": 1,
    "testId": 1,
    "testTitle": "Java 기초 문법 테스트",
    "category": "Java",
    "totalScore": 100,
    "correctCount": 10,
    "questionCount": 10,
    "passed": true,
    "submittedAt": "2026-04-04T11:43:13.8729459"
  }
}
```

| 검증 항목 | 기대값 | 실제값 | 결과 |
|-----------|--------|--------|------|
| totalScore | 100 | 100 | PASS |
| correctCount | 10 | 10 | PASS |
| questionCount | 10 | 10 | PASS |
| passed | true (100 >= 60) | true | PASS |

### 4.4 테스트 결과 목록 조회

| API | 검증 항목 | 결과 |
|-----|-----------|------|
| `GET /api/tests/results` | 제출 결과 1건 반환 | PASS |
| | testTitle, category, totalScore 포함 | PASS |

### 4.5 멘토 추천

**Java 카테고리 추천:**

| 순위 | 멘토 | matchScore | specialty |
|------|------|------------|----------|
| 1 | 정풀스택 (토스) | 100 | Java, React, Spring |
| 2 | 김자바 (네이버) | 94 | Java, Spring |

**React 카테고리 추천:**

| 순위 | 멘토 | matchScore | specialty |
|------|------|------------|----------|
| 1 | 정풀스택 (토스) | 70 | Java, React, Spring |
| 2 | 박리액트 (라인) | 58 | React, Node.js |

| 검증 항목 | 결과 |
|-----------|------|
| APPROVED 멘토만 추천 | PASS |
| specialty에 해당 category 포함 멘토 필터링 | PASS |
| matchScore 내림차순 정렬 | PASS |
| 경력/테스트 점수 기반 점수 계산 | PASS |

### 4.6 인증 검증

| API | 인증 없이 호출 | 결과 |
|-----|----------------|------|
| `GET /api/tests` | HTTP 403 Forbidden | PASS (인증 필수 확인) |
| 멘토 계정 로그인 | accessToken + refreshToken 발급 | PASS |

---

## 5. 종합 결과

| 항목 | 상태 |
|------|------|
| gradle compileJava 빌드 | PASS |
| Spring Boot 기동 | PASS |
| DataInitializer 실행 (테스트 15개, 문제 150개) | PASS |
| 멘토 초기 데이터 (5명) | PASS |
| 테스트 목록 조회 (전체 / 카테고리 필터) | PASS |
| 테스트 상세 조회 (문제 포함, 정답 미포함) | PASS |
| 답안 제출 + 자동 채점 | PASS |
| 테스트 결과 목록 조회 | PASS |
| 멘토 추천 (matchScore 기반) | PASS |
| 중복 삽입 방지 (재시작 시 스킵) | PASS |

**전체 10개 검증 항목 — 모두 PASS**

---

## 6. 구현 파일

| 파일 | 역할 |
|------|------|
| `backend/src/main/java/com/devmatch/config/DataInitializer.java` | 초기 데이터 삽입 (CommandLineRunner) |

---

## 7. 다음 단계

- 프론트엔드 연동 확인 (테스트 목록 → 상세 → 제출 → 결과 화면)
- 일부 오답 제출 테스트 (부분 점수, passed=false 케이스)
- 멘토 매칭 신청 → 수락/거절 흐름 테스트
- 앱 재시작 후 중복 삽입 방지 확인