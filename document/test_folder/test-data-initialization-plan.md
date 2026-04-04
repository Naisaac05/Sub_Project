
# 테스트 문제 데이터 초기화 계획서

> 작성일: 2026-04-04 | 프로젝트: DevMatch | 기반: phase3-test-matching-implementation.md
> 참고: [F-Lab](https://f-lab.kr/) 멘토링 서비스 구조

---

## 1. 목적

Phase 3 테스트 시스템의 API를 실제로 동작시키려면 `tests`, `questions` 테이블에 초기 데이터가 필요합니다.
이 문서는 **DataInitializer를 통한 문제 데이터 삽입 계획**을 정의합니다.

---

## 2. 카테고리 & 난이도 설계

### 2.1 카테고리 선정

프론트엔드에서 이미 사용 중인 카테고리를 기준으로 합니다.

| 카테고리 | 사용 위치 | 설명 |
|----------|-----------|------|
| **Java** | 테스트 목록, 멘토 추천 | Java 기초~심화 (F-Lab 핵심 분야) |
| **Spring** | 테스트 목록, 멘토 추천 | Spring Boot/Framework |
| **React** | 테스트 목록, 멘토 추천 | React + 프론트엔드 |
| **Python** | 테스트 목록, 멘토 추천 | Python 기초~심화 |
| **Algorithm** | 테스트 목록 | 자료구조 & 알고리즘 |

> 프론트엔드 참조 파일:
> - `frontend/src/app/tests/page.tsx` → `CATEGORIES = ['전체', 'Java', 'Spring', 'React', 'Python', 'Algorithm']`
> - `frontend/src/app/mentors/page.tsx` → `CATEGORIES = ['전체', 'Java', 'Spring', 'React', 'Python', 'Node.js', 'DevOps']`

### 2.2 난이도별 구성

| 난이도 | Enum 값 | 한글 | 대상 수준 |
|--------|---------|------|-----------|
| 입문 | `BEGINNER` | 입문 | 비전공자/부트캠프 수료 수준 |
| 중급 | `INTERMEDIATE` | 중급 | 실무 1~3년차, 채용 경쟁력 |
| 고급 | `ADVANCED` | 고급 | 실무 3년 이상, 깊은 이론 + 실무 |

### 2.3 테스트 매트릭스

**5개 카테고리 x 3개 난이도 = 총 15개 테스트**

| # | 카테고리 | 난이도 | 테스트 제목 | 문항 수 | 제한시간(분) | 합격점(100점) |
|---|----------|--------|-------------|---------|-------------|--------------|
| 1 | Java | BEGINNER | Java 기초 문법 테스트 | 10 | 15 | 60 |
| 2 | Java | INTERMEDIATE | Java 중급 심화 테스트 | 10 | 20 | 65 |
| 3 | Java | ADVANCED | Java 고급 아키텍처 테스트 | 10 | 25 | 70 |
| 4 | Spring | BEGINNER | Spring 입문 테스트 | 10 | 15 | 60 |
| 5 | Spring | INTERMEDIATE | Spring 중급 실무 테스트 | 10 | 20 | 65 |
| 6 | Spring | ADVANCED | Spring 고급 아키텍처 테스트 | 10 | 25 | 70 |
| 7 | React | BEGINNER | React 기초 테스트 | 10 | 15 | 60 |
| 8 | React | INTERMEDIATE | React 중급 실무 테스트 | 10 | 20 | 65 |
| 9 | React | ADVANCED | React 고급 심화 테스트 | 10 | 25 | 70 |
| 10 | Python | BEGINNER | Python 기초 문법 테스트 | 10 | 15 | 60 |
| 11 | Python | INTERMEDIATE | Python 중급 심화 테스트 | 10 | 20 | 65 |
| 12 | Python | ADVANCED | Python 고급 아키텍처 테스트 | 10 | 25 | 70 |
| 13 | Algorithm | BEGINNER | 알고리즘 기초 테스트 | 10 | 15 | 60 |
| 14 | Algorithm | INTERMEDIATE | 알고리즘 중급 테스트 | 10 | 20 | 65 |
| 15 | Algorithm | ADVANCED | 알고리즘 고급 테스트 | 10 | 25 | 70 |

**총 문항 수: 15개 테스트 x 10문항 = 150문항**

---

## 3. 문제 설계 기준

### 3.1 공통 규칙

- 4지선다 객관식 (options 배열 4개)
- correctAnswer: 0~3 (정답 인덱스)
- score: 각 문항 10점 (10문항 x 10점 = 100점 만점)
- orderIndex: 1~10 (문제 순서)

### 3.2 난이도별 출제 기준

| 난이도 | 출제 방향 | 예시 |
|--------|-----------|------|
| BEGINNER | 기본 문법, 개념 정의, 용어 이해 | "Java에서 변수를 선언하는 올바른 방법은?" |
| INTERMEDIATE | 동작 원리, 실무 패턴, 코드 분석 | "다음 코드의 출력 결과는?", "어떤 디자인 패턴이 적용되었는가?" |
| ADVANCED | 내부 구현, 성능 최적화, 아키텍처 설계 | "JVM GC 알고리즘 비교", "트랜잭션 격리 수준별 차이" |

---

## 4. 카테고리별 문제 범위

### 4.1 Java

| 난이도 | 출제 범위 |
|--------|-----------|
| BEGINNER | 변수/타입, 조건문/반복문, 배열, 문자열, 클래스/객체 기초, 접근 제어자, 상속 기초 |
| INTERMEDIATE | 컬렉션 프레임워크, 제네릭, 예외 처리, 스트림 API, 람다, 멀티스레드 기초, OOP 원칙 (SOLID) |
| ADVANCED | JVM 메모리 구조, GC 알고리즘, 동시성 (synchronized, Lock, Atomic), 리플렉션, 디자인 패턴, 성능 최적화 |

### 4.2 Spring

| 난이도 | 출제 범위 |
|--------|-----------|
| BEGINNER | Spring Boot 기본 구조, IoC/DI 개념, 어노테이션 (@Controller, @Service 등), REST API 기초, application.yml |
| INTERMEDIATE | Spring MVC 동작 흐름, JPA/Hibernate 매핑, 트랜잭션 관리, Spring Security 기초, Bean 스코프, AOP |
| ADVANCED | 트랜잭션 전파/격리 수준, 영속성 컨텍스트 상세, 캐시 전략, 대용량 처리 (Batch), MSA 패턴, 테스트 전략 |

### 4.3 React

| 난이도 | 출제 범위 |
|--------|-----------|
| BEGINNER | JSX 문법, 컴포넌트/Props, useState, 이벤트 핸들링, 조건부 렌더링, 리스트 렌더링 |
| INTERMEDIATE | useEffect, useContext, Custom Hook, 상태 관리 (Context API), React Router, 비동기 처리, 성능 최적화 (memo, useMemo) |
| ADVANCED | 가상 DOM & 재조정(Reconciliation), Fiber 아키텍처, 서버 컴포넌트(RSC), Suspense/Concurrent, 렌더링 전략 (SSR/SSG/ISR), 접근성 |

### 4.4 Python

| 난이도 | 출제 범위 |
|--------|-----------|
| BEGINNER | 변수/타입, 리스트/딕셔너리/튜플, 조건문/반복문, 함수 정의, 문자열 처리, 파일 I/O 기초 |
| INTERMEDIATE | 클래스/OOP, 데코레이터, 제너레이터, 컴프리헨션, 예외 처리, 모듈/패키지, 정규표현식 |
| ADVANCED | GIL과 멀티프로세싱, 메타클래스, 디스크립터, asyncio, 메모리 관리, C 확장, 타입 힌팅 고급 |

### 4.5 Algorithm

| 난이도 | 출제 범위 |
|--------|-----------|
| BEGINNER | 시간/공간 복잡도, 배열/스택/큐, 선형 탐색, 정렬 기초 (버블/선택/삽입), 재귀 기초 |
| INTERMEDIATE | 이진 탐색, 해시 테이블, 트리(BST), 그래프 탐색 (BFS/DFS), DP 기초, 분할 정복 |
| ADVANCED | 고급 DP, 그리디 증명, 최단 경로 (다익스트라/벨만-포드), 세그먼트 트리, 네트워크 플로우, NP-완전 |

---

## 5. 샘플 문제 (카테고리별 각 1문항)

### 5.1 Java BEGINNER 샘플

```
Q: Java에서 문자열을 비교할 때 올바른 방법은?
A) str1 == str2
B) str1.equals(str2)
C) str1.compare(str2)
D) str1.match(str2)
정답: B (인덱스 1)
```

### 5.2 Spring INTERMEDIATE 샘플

```
Q: Spring에서 @Transactional 어노테이션의 기본 전파(Propagation) 속성은?
A) REQUIRES_NEW
B) REQUIRED
C) SUPPORTS
D) MANDATORY
정답: B (인덱스 1)
```

### 5.3 React BEGINNER 샘플

```
Q: React에서 컴포넌트의 상태를 관리하기 위해 사용하는 Hook은?
A) useEffect
B) useContext
C) useState
D) useRef
정답: C (인덱스 2)
```

### 5.4 Python INTERMEDIATE 샘플

```
Q: Python에서 리스트 컴프리헨션의 올바른 문법은?
A) [x for x in range(10) if x > 5]
B) [for x in range(10): x if x > 5]
C) [x if x > 5 for x in range(10)]
D) [x in range(10) for if x > 5]
정답: A (인덱스 0)
```

### 5.5 Algorithm ADVANCED 샘플

```
Q: 다익스트라 알고리즘의 시간 복잡도는? (우선순위 큐 사용 시)
A) O(V^2)
B) O(V + E)
C) O((V + E) log V)
D) O(V * E)
정답: C (인덱스 2)
```

---

## 6. 구현 방식

### 6.1 DataInitializer.java

`CommandLineRunner`를 구현하여 애플리케이션 시작 시 데이터를 삽입합니다.

```
위치: backend/src/main/java/com/devmatch/config/DataInitializer.java
```

**구현 전략:**

```java
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final TestRepository testRepository;
    private final QuestionRepository questionRepository;

    @Override
    @Transactional
    public void run(String... args) {
        // 이미 데이터가 있으면 스킵
        if (testRepository.count() > 0) return;

        // 카테고리별 테스트 + 문제 생성
        initJavaTests();
        initSpringTests();
        initReactTests();
        initPythonTests();
        initAlgorithmTests();
    }

    private void initJavaTests() {
        // BEGINNER
        Test test = createTest("Java 기초 문법 테스트",
            "Java 언어의 기본 문법과 핵심 개념을 평가합니다.",
            "Java", Difficulty.BEGINNER, 15, 60, 10);
        createJavaBeginnerQuestions(test);

        // INTERMEDIATE, ADVANCED ...
    }

    private Test createTest(String title, String desc, String category,
                           Difficulty diff, int timeLimit, int passingScore, int qCount) {
        return testRepository.save(Test.builder()
            .title(title).description(desc).category(category)
            .difficulty(diff).timeLimit(timeLimit)
            .passingScore(passingScore).questionCount(qCount)
            .build());
    }

    private void createQuestion(Test test, int order, String content,
                               List<String> options, int correctAnswer) {
        questionRepository.save(Question.builder()
            .test(test).orderIndex(order).content(content)
            .options(options).correctAnswer(correctAnswer).score(10)
            .build());
    }
}
```

### 6.2 중복 삽입 방지

- `testRepository.count() > 0` 체크로 이미 데이터가 있으면 스킵
- H2 개발 환경에서 매 재시작마다 자동 삽입
- MySQL 운영 환경에서는 한 번만 실행

### 6.3 프로파일 분리 (선택)

개발 환경에서만 실행하려면:

```java
@Profile("dev")  // application.yml에서 spring.profiles.active=dev 일 때만 실행
@Component
public class DataInitializer implements CommandLineRunner { ... }
```

---

## 7. 엔티티-DB 매핑 참조

### 7.1 Test Entity → tests 테이블

| Java 필드 | DB 컬럼 | 타입 | 제약조건 |
|-----------|---------|------|----------|
| id | id | BIGINT PK | AUTO_INCREMENT |
| title | title | VARCHAR(200) | NOT NULL |
| description | description | VARCHAR(2000) | - |
| category | category | VARCHAR(50) | NOT NULL |
| difficulty | difficulty | VARCHAR(20) | NOT NULL (ENUM) |
| timeLimit | time_limit | INT | NOT NULL |
| passingScore | passing_score | INT | NOT NULL |
| questionCount | question_count | INT | NOT NULL |
| isActive | is_active | BOOLEAN | NOT NULL, DEFAULT TRUE |

### 7.2 Question Entity → questions 테이블

| Java 필드 | DB 컬럼 | 타입 | 제약조건 |
|-----------|---------|------|----------|
| id | id | BIGINT PK | AUTO_INCREMENT |
| test | test_id | BIGINT FK | NOT NULL → tests(id) |
| content | content | VARCHAR(2000) | NOT NULL |
| options | options | TEXT (JSON) | StringListConverter |
| correctAnswer | correct_answer | INT | NOT NULL (0~3) |
| score | score | INT | NOT NULL (기본 10) |
| orderIndex | order_index | INT | NOT NULL (1~10) |

---

## 8. 멘토 초기 데이터 (매칭 테스트용)

매칭 추천 API 테스트를 위해 APPROVED 상태의 멘토 프로필도 함께 생성해야 합니다.

| 멘토 이름 | specialty | careerYears | company |
|-----------|-----------|-------------|---------|
| 김자바 | ["Java", "Spring"] | 8 | 네이버 |
| 이스프링 | ["Spring", "DevOps"] | 5 | 카카오 |
| 박리액트 | ["React", "Node.js"] | 6 | 라인 |
| 최파이썬 | ["Python", "Algorithm"] | 7 | 쿠팡 |
| 정풀스택 | ["Java", "React", "Spring"] | 10 | 토스 |

> 멘토 계정 생성 → MentorProfile 생성 (status = APPROVED) → 매칭 추천 API 테스트 가능

---

## 9. 작업 순서

| 단계 | 작업 | 산출물 |
|------|------|--------|
| 1 | DataInitializer 클래스 생성 | `config/DataInitializer.java` |
| 2 | 테스트 데이터 생성 메서드 (5개 카테고리) | `initJava/Spring/React/Python/AlgorithmTests()` |
| 3 | 문제 데이터 작성 (카테고리별 30문항 = 총 150문항) | `create___Questions()` 메서드 15개 |
| 4 | 멘토 초기 데이터 생성 | `initMentors()` |
| 5 | 빌드 및 API 테스트 | `gradle compileJava` + Swagger/Postman 검증 |
| 6 | 프론트엔드 연동 확인 | 테스트 목록/상세/제출 화면 |

---

## 10. 검증 체크리스트

- [ ] `GET /api/tests` → 15개 테스트 목록 반환
- [ ] `GET /api/tests?category=Java` → Java 테스트 3개 반환
- [ ] `GET /api/tests/{id}` → 문제 10개 포함 (정답 미포함)
- [ ] `POST /api/tests/{id}/submit` → 자동 채점 + 결과 반환
- [ ] `GET /api/tests/results` → 내 결과 목록
- [ ] `GET /api/matching/recommend?category=Java` → APPROVED 멘토 추천 목록
- [ ] 프론트엔드 테스트 목록 페이지에서 카테고리 필터 동작
- [ ] 프론트엔드 테스트 상세 페이지에서 문제 표시
- [ ] 프론트엔드 테스트 제출 후 결과 표시