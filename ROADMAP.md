# DevMatch — Spring Boot + Next.js 버전

## 개발 로드맵 & 단계별 계획서

> 2026년 3월 | F-Lab 참고 스타일 매칭 플랫폼

---

## 1. Java + Spring Boot를 추천하는 이유

Java + Spring Boot는 한국 IT 시장에서 가장 넓은 수요를 가진 백엔드 기술 스택입니다. F-Lab에서도 Java Backend 멘토링이 가장 인기 있는 코스입니다.

- **취업 시장:** 네이버, 카카오, 삼성 등 대부분 대기업이 Java/Spring 기반
- **학습 효과:** OOP, 디자인 패턴, 레이어드 아키텍처 등 엔지니어링 기초체력 향상
- **기술 깊이:** JVM 이해, 멀티쓰레드, GC 등 깊이 있는 학습 가능
- **생태계:** Spring 문서, 한국어 커뮤니티, 백기선 강의 등 풍부한 자료

---

## 2. 기술 스택 상세

| 영역 | 기술 | 설명 |
|------|------|------|
| Backend | Spring Boot 3.x + Java 17 | 업계 표준 프레임워크, 자동 설정 지원 |
| 인증/인가 | Spring Security + JWT | 토큰 기반 인증, 역할별 접근 제어 |
| ORM | Spring Data JPA + Hibernate | 객체-관계 매핑, DB 추상화 |
| DB | MySQL 8.0 | 한국 시장에서 가장 많이 사용, 무료 |
| Cache | Redis | 세션 관리, 응답 캐싱, 성능 향상 |
| API 문서 | Swagger (SpringDoc) | API 자동 문서화, 프론트엔드 협업 편리 |
| 빌드 | Gradle | 의존성 관리, 빌드 자동화 |
| Frontend | Next.js 14 + TypeScript | React 기반 SSR, 프론트엔드 독립 배포 |
| UI | Tailwind CSS + shadcn/ui | 빠른 UI 개발, 컴포넌트 재사용 |
| 결제 | 토스페이먼츠 | 한국 결제 시장 특화, REST API 제공 |
| 배포 | Docker + AWS EC2 | 컨테이너화로 일관된 환경 구성 |
| CI/CD | GitHub Actions | 자동 테스트 + 배포 파이프라인 |

---

## 3. 단계별 개발 계획

Spring Boot 버전은 백엔드 학습량이 더 많으므로 총 7단계, 약 24주(6개월)로 계획합니다.

| 단계 | 주제 | 핵심 기술 | 기간 |
|------|------|-----------|------|
| Phase 0 | Java 기초 학습 | Java 문법, OOP, 자료구조 | 4주 |
| Phase 1 | 프로젝트 세팅 | Spring Boot, Gradle, MySQL | 2주 |
| Phase 2 | 회원 시스템 | Spring Security, JWT, JPA | 4주 |
| Phase 3 | 테스트 & 매칭 | REST API, QueryDSL | 4주 |
| Phase 4 | Google 연동 | OAuth2 Client, Calendar API | 3주 |
| Phase 5 | 결제 & 커뮤니티 | 토스 API, 페이지네이션 | 4주 |
| Phase 6 | Admin & 배포 | Docker, AWS, GitHub Actions | 3주 |

---

## 4. Phase 0: Java 기초 학습 (4주)

Spring Boot를 시작하기 전에 Java 언어 기초를 탄탄히 다져야 합니다. 이 단계를 건너뛰면 이후 모든 단계에서 어려움을 겪습니다.

### 1~2주차: Java 문법 기초

- Java 설치 (JDK 17) 및 IntelliJ IDEA 설정
- 변수, 타입, 연산자, 조건문, 반복문
- 배열, 문자열, 메서드
- 클래스와 객체: 생성자, 필드, 메서드

### 3~4주차: OOP & 자료구조

- 객체지향 4대 특성: 캡슐화, 상속, 다형성, 추상화
- 인터페이스, 추상 클래스
- Collection Framework: List, Map, Set
- Exception 처리, Generic, Stream API
- Git 기초 (커밋, 푸시, 브랜치)

### 추천 학습 자료

- **책:** 자바의 정석 (3판) — 한국어 Java 학습의 바이블
- **영상:** 백기선의 스프링 입문 — 스프링 기초부터 체계적 학습
- **실습:** Baekjoon Online Judge / Programmers — 매일 1~2문제씩 풀기

---

## 5. Phase 1: 프로젝트 세팅 (2주)

Spring Boot 프로젝트와 Next.js 프로젝트를 각각 생성하고 기본 구조를 잡습니다.

### Backend 세팅

1. Spring Initializr로 프로젝트 생성 (start.spring.io)
2. 의존성: Spring Web, Spring Data JPA, Spring Security, MySQL Driver, Lombok, Validation
3. `application.yml` 설정 (DB 연결, JPA 설정, 서버 포트)
4. MySQL 설치 및 데이터베이스 생성
5. 패키지 구조 설계 (레이어드 아키텍처)

### Frontend 세팅

1. Next.js + TypeScript 프로젝트 생성
2. Tailwind CSS + shadcn/ui 설정
3. Axios 설치 및 API 클라이언트 세팅
4. 기본 레이아웃 (GNB, Footer) 및 페이지 라우팅

### 프로젝트 패키지 구조 (Backend)

| 패키지 / 파일 | 역할 |
|---------------|------|
| `controller/` | REST API 엔드포인트 (요청 수신) |
| `service/` | 비즈니스 로직 (핵심 기능) |
| `repository/` | DB 접근 (JPA Repository) |
| `entity/` | DB 테이블 매핑 클래스 |
| `dto/` | 요청/응답 데이터 객체 |
| `config/` | Security, CORS, Swagger 설정 |
| `exception/` | 글로벌 예외 처리 |
| `common/` | 공통 유틸, 상수, 열거형 |

---

## 6. Phase 2: 회원 시스템 (4주)

인증/인가 시스템을 구현합니다. Spring Security + JWT를 사용하여 토큰 기반 인증을 구현합니다.

### Entity 설계

| Entity | 필드 | 타입 | 설명 |
|--------|------|------|------|
| User | id | Long (PK) | 기본키 (Auto Increment) |
| | email | String (unique) | 로그인 이메일 |
| | password | String | BCrypt 암호화 저장 |
| | name | String | 사용자 이름 |
| | role | Enum | MENTEE / MENTOR / ADMIN |
| | createdAt | LocalDateTime | 가입일 (@CreatedDate) |
| MentorProfile | id | Long (PK) | 멘토 프로필 기본키 |
| | user | User (FK) | @OneToOne 관계 |
| | specialty | List\<String\> | 전문 분야 (JSON 변환) |
| | careerYears | Integer | 경력 연수 |
| | company | String | 현재 회사 |
| | status | Enum | PENDING / APPROVED / REJECTED |

### 개발 목록

1. User Entity + UserRepository 생성
2. 회원가입 API: `POST /api/auth/signup`
3. 로그인 API: `POST /api/auth/login` (이메일+비밀번호 → JWT 발급)
4. JWT 필터 구현 (JwtTokenProvider, JwtAuthFilter)
5. Spring Security 설정 (SecurityFilterChain)
6. Google OAuth2 소셜 로그인 연동
7. 역할별 접근 제어 (`@PreAuthorize`)
8. 멘토 신청 폼 API: `POST /api/mentor/apply`
9. 마이페이지 API: `GET/PUT /api/users/me`

### 핵심 학습 포인트

- **Spring Security:** 필터 체인 구조 이해 — 요청이 어떻게 필터를 통과하는지
- **JWT:** Access Token + Refresh Token 전략 이해
- **JPA:** Entity 설계, 연관관계 매핑, 영속성 컨텍스트 이해

---

## 7. Phase 3: 테스트 & 멘토 매칭 (4주)

플랫폼의 핵심 기능입니다. 실력 테스트로 멘티의 수준을 분석하고, 적합한 멘토와 매칭합니다.

### 테스트 시스템 API

| API | 메서드 | 설명 |
|-----|--------|------|
| `/api/tests` | GET | 테스트 목록 조회 (분야별) |
| `/api/tests/{id}` | GET | 테스트 상세 + 문제 목록 |
| `/api/tests/{id}/submit` | POST | 답안 제출 + 자동 채점 |
| `/api/tests/results` | GET | 내 테스트 결과 목록 |
| `/api/matching/recommend` | GET | 테스트 결과 기반 멘토 추천 |
| `/api/matching/request` | POST | 멘토에게 매칭 신청 |
| `/api/matching/accept` | PUT | 멘토가 매칭 수락/거절 |

### 멘토 심사 흐름

1. 멘토 신청 (status: PENDING)
2. 관리자가 경력/전문성 확인 후 승인 또는 거절
3. 승인 시 멘토 목록에 공개 (status: APPROVED)
4. 거절 시 사유와 함께 이메일 발송

---

## 8. Phase 4: Google Calendar & Meet 연동 (3주)

매칭이 완료되면 Google Calendar에 멘토링 일정을 자동으로 등록하고 Google Meet 링크를 생성합니다.

### Spring Boot에서 Google API 연동

1. Google Cloud Console에서 OAuth 2.0 클라이언트 ID 생성
2. `google-api-java-client` 라이브러리 추가 (build.gradle)
3. GoogleCalendarService 클래스 구현
4. Calendar Event 생성 시 `conferenceData`로 Meet 링크 자동 생성
5. 멘토/멘티 양쪽 캘린더에 자동 초대 (attendees 추가)
6. 멘토링 일정 관리 UI 구현 (요일/시간 선택)

### 주요 코드 흐름

멘티가 멘토를 선택하고 결제가 완료되면 `CalendarService.createMentoringEvent()`가 호출됩니다. 이 메서드는 Google Calendar API를 통해 이벤트를 생성하고, `conferenceData` 옵션으로 Google Meet 링크를 자동 포함시킵니다. 생성된 일정은 멘토와 멘티 양쪽의 캘린더에 자동으로 등록됩니다.

---

## 9. Phase 5: 결제 & 커뮤니티 (4주)

### 결제 시스템 (토스페이먼츠)

- **결제 흐름:** 멘토 선택 → 결제 페이지 → 토스 SDK 호출 → 웹훅 확인 → 매칭 확정
- **Backend API:** `POST /api/payments/confirm` — 토스 결제 승인 및 DB 저장
- **보안:** 결제 금액 검증 (프론트엔드 금액과 서버 금액 비교)
- **환불:** `POST /api/payments/{id}/cancel` — 토스 환불 API 호출

### 커뮤니티 기능

| API | 메서드 | 설명 |
|-----|--------|------|
| `/api/posts` | GET | 게시글 목록 (페이지네이션) |
| `/api/posts` | POST | 게시글 작성 |
| `/api/posts/{id}/comments` | POST | 댓글 작성 |
| `/api/posts/{id}/like` | POST | 좋아요 토글 |

---

## 10. Phase 6: Admin 페이지 & 배포 (3주)

### Admin API

| API | 기능 |
|-----|------|
| `GET /api/admin/users` | 회원 목록 조회, 검색, 필터링 |
| `PUT /api/admin/users/{id}/role` | 역할 변경 (MENTEE/MENTOR/ADMIN) |
| `GET /api/admin/mentors/pending` | 멘토 승인 대기 목록 |
| `PUT /api/admin/mentors/{id}/approve` | 멘토 승인/거절 처리 |
| `GET /api/admin/payments` | 결제 내역 조회 + 통계 |
| `GET /api/admin/dashboard` | 통계 요약 (회원수, 매칭수, 매출) |
| `CRUD /api/admin/faqs` | FAQ 관리 |

### 배포 전략

- **Frontend:** Vercel에 Next.js 배포 (Git push 시 자동 배포)
- **Backend:** Docker 컨테이너 빌드 → AWS EC2 또는 Railway에 배포
- **DB:** AWS RDS (MySQL) 또는 Railway MySQL
- **CI/CD:** GitHub Actions로 자동 테스트 + 배포
- **HTTPS:** Let's Encrypt 인증서 또는 AWS ALB

### 배포 체크리스트

1. Dockerfile 작성 (Multi-stage build)
2. `docker-compose.yml`로 로컬 개발 환경 구성
3. GitHub Actions 워크플로우 작성 (test → build → deploy)
4. AWS EC2 인스턴스 세팅 및 Docker 설치
5. 도메인 연결 및 SSL 설정
6. 환경변수 관리 (`application-prod.yml`)

---

## 11. 추천 학습 리소스

| 주제 | 리소스 | 비고 |
|------|--------|------|
| Java 기초 | 자바의 정석 (3판, 남궁성) | 한국어 Java 바이블 |
| Spring Boot | 백기선 스프링 입문 강의 | 무료 강의, 기초부터 체계적 |
| Spring Boot 심화 | 백기선 스프링 완전정복 로드맵 | MVC, JPA, Security 심화 |
| JPA | 자바 ORM 표준 JPA (김영한) | JPA 바이블, 필독 권장 |
| React/Next.js | React 공식 문서 + Next.js Docs | App Router 기준 학습 |
| TypeScript | TypeScript Handbook | 공식 핸드북으로 충분 |
| Docker | Docker 공식 튜토리얼 | 컨테이너 기초 학습 |
| SQL | MySQL 공식 문서 / 프로그래머스 SQL 고득점 Kit | 기본 SQL부터 학습 |
| 결제 | 토스페이먼츠 개발자 문서 | Java SDK 가이드 참고 |

---

## 12. 입문자를 위한 팁

1. **Java 기초에 시간을 투자하세요:** Phase 0을 건너뛰면 Spring Boot에서 고생합니다. OOP가 탄탄해야 스프링이 편해집니다.
2. **백기선 강의를 따라가세요:** 한국어 Spring Boot 강의 중 가장 체계적입니다. 무료 입문부터 시작하세요.
3. **API 먼저, UI 나중에:** 백엔드 API를 먼저 완성하고 Swagger로 테스트한 후 프론트엔드를 연결하세요.
4. **테스트 코드를 작성하세요:** JUnit5로 단위 테스트를 작성하면 코드 품질과 자신감이 동시에 올라갑니다.
5. **Claude를 활용하세요:** 각 단계에서 막히는 부분이 있으면 구체적으로 질문해 주세요. Entity 설계, API 구현, 디버깅 모두 도와드릴 수 있습니다.
