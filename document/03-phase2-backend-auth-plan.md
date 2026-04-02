# Phase 2: 회원 시스템 — Backend 구현 계획서

> 작성일: 2026-04-02 | 프로젝트: DevMatch | 기반: ROADMAP.md Phase 2

---

## 1. 현재 상태 분석

### 완료된 항목 (Phase 1)

| 항목 | 상태 | 비고 |
|------|------|------|
| Spring Boot 3.4.4 프로젝트 | ✅ | Java 17, Gradle |
| build.gradle 의존성 | ✅ | Security, JPA, JWT, Redis, OAuth2, Swagger 모두 포함 |
| application.yml | ✅ | MySQL, Redis, JWT, OAuth2, Toss 설정 완료 |
| docker-compose.yml | ✅ | MySQL 8.0 + Redis 7 |
| 패키지 구조 | ✅ | controller, service, repository, entity, dto, config, exception, common |
| Java 소스 파일 생성 | ✅ | 파일은 존재하나 내용이 비어 있음 |

### 구현 필요 항목 (Phase 2 범위)

모든 Java 소스 파일이 빈 상태이므로 전체 구현이 필요합니다.

---

## 2. 구현 범위 요약

Phase 2에서 구현할 백엔드 기능은 다음과 같습니다:

1. **Entity 계층** — User, MentorProfile 엔티티 + Enum
2. **Repository 계층** — UserRepository, MentorProfileRepository
3. **JWT 인증 모듈** — JwtTokenProvider, JwtAuthFilter
4. **Security 설정** — SecurityFilterChain, CORS, 역할별 접근 제어
5. **DTO 계층** — 요청/응답 DTO (회원가입, 로그인, 프로필, 멘토 신청)
6. **Service 계층** — AuthService, UserService, MentorService
7. **Controller 계층** — AuthController, UserController, MentorController
8. **예외 처리** — GlobalExceptionHandler + 커스텀 예외 클래스
9. **Swagger 설정** — API 문서화

---

## 3. Entity 설계

### 3.1 User Entity

```
파일: entity/User.java
테이블: users
```

| 필드 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | Long | PK, Auto Increment | 기본키 |
| email | String | UNIQUE, NOT NULL, max 100 | 로그인 이메일 |
| password | String | NOT NULL, max 255 | BCrypt 암호화 저장 |
| name | String | NOT NULL, max 50 | 사용자 이름 |
| role | Role (Enum) | NOT NULL, default MENTEE | MENTEE / MENTOR / ADMIN |
| provider | String | nullable, max 20 | 소셜 로그인 제공자 (google 등) |
| providerId | String | nullable, max 100 | 소셜 로그인 고유 ID |
| createdAt | LocalDateTime | NOT NULL | 가입일 (@CreatedDate) |
| updatedAt | LocalDateTime | NOT NULL | 수정일 (@LastModifiedDate) |

**핵심 설계 결정:**

- `@EntityListeners(AuditingEntityListener.class)` 사용 → `@EnableJpaAuditing` 필요
- password는 소셜 로그인 사용자의 경우 null 허용 가능하나, 일반 회원가입은 필수 → 검증을 Service 레벨에서 처리
- provider + providerId 조합으로 소셜 로그인 사용자 식별
- role은 `@Enumerated(EnumType.STRING)`으로 저장 (DB에 문자열로 저장)

### 3.2 Role Enum

```
파일: entity/Role.java (신규 생성)
```

| 값 | 설명 |
|----|------|
| MENTEE | 일반 사용자 (기본값) |
| MENTOR | 승인된 멘토 |
| ADMIN | 관리자 |

### 3.3 MentorProfile Entity

```
파일: entity/MentorProfile.java
테이블: mentor_profiles
```

| 필드 | 타입 | 제약조건 | 설명 |
|------|------|----------|------|
| id | Long | PK, Auto Increment | 기본키 |
| user | User | FK, @OneToOne, UNIQUE | User와 1:1 관계 |
| specialty | List\<String\> | JSON 변환 | 전문 분야 목록 |
| careerYears | Integer | NOT NULL | 경력 연수 |
| company | String | nullable, max 100 | 현재 회사 |
| bio | String | nullable, max 1000 | 자기 소개 |
| status | MentorStatus (Enum) | NOT NULL, default PENDING | 심사 상태 |
| createdAt | LocalDateTime | NOT NULL | 신청일 |
| updatedAt | LocalDateTime | NOT NULL | 수정일 |

### 3.4 MentorStatus Enum

```
파일: entity/MentorStatus.java (신규 생성)
```

| 값 | 설명 |
|----|------|
| PENDING | 심사 대기 |
| APPROVED | 승인 완료 |
| REJECTED | 거절 |

### 3.5 StringListConverter (기존 파일)

```
파일: entity/StringListConverter.java
```

- `AttributeConverter<List<String>, String>` 구현
- DB에 JSON 문자열로 저장, 엔티티에서는 `List<String>`으로 사용
- Jackson ObjectMapper 사용하여 직렬화/역직렬화

---

## 4. Repository 설계

### 4.1 UserRepository

```
파일: repository/UserRepository.java
```

| 메서드 | 반환타입 | 설명 |
|--------|----------|------|
| findByEmail(String email) | Optional\<User\> | 이메일로 사용자 조회 |
| existsByEmail(String email) | boolean | 이메일 중복 확인 |
| findByProviderAndProviderId(String provider, String providerId) | Optional\<User\> | 소셜 로그인 사용자 조회 |

### 4.2 MentorProfileRepository

```
파일: repository/MentorProfileRepository.java
```

| 메서드 | 반환타입 | 설명 |
|--------|----------|------|
| findByUser(User user) | Optional\<MentorProfile\> | 사용자의 멘토 프로필 조회 |
| findByUserId(Long userId) | Optional\<MentorProfile\> | 사용자 ID로 멘토 프로필 조회 |
| findByStatus(MentorStatus status) | List\<MentorProfile\> | 상태별 멘토 목록 조회 |
| existsByUserId(Long userId) | boolean | 멘토 신청 여부 확인 |

---

## 5. JWT 인증 모듈 설계

### 5.1 JwtTokenProvider

```
파일: security/JwtTokenProvider.java (신규 생성)
패키지: com.devmatch.security
```

**역할:** JWT 토큰 생성, 검증, 파싱

| 메서드 | 설명 |
|--------|------|
| generateAccessToken(Long userId, String email, Role role) | Access Token 생성 (1시간) |
| generateRefreshToken(Long userId) | Refresh Token 생성 (7일) |
| validateToken(String token) | 토큰 유효성 검증 (boolean 반환) |
| getUserIdFromToken(String token) | 토큰에서 userId 추출 |
| getEmailFromToken(String token) | 토큰에서 email 추출 |
| getRoleFromToken(String token) | 토큰에서 role 추출 |

**설정값 (application.yml에서 주입):**

- `jwt.secret` — HMAC-SHA256 서명 키
- `jwt.access-token-expiration` — Access Token 만료 시간 (ms)
- `jwt.refresh-token-expiration` — Refresh Token 만료 시간 (ms)

**기술 결정:**

- jjwt 0.12.6 사용 (build.gradle에 이미 포함)
- Refresh Token은 Redis에 저장 (`refreshToken:{userId}` 키)
- Access Token Claims: `sub`(userId), `email`, `role`, `iat`, `exp`

### 5.2 JwtAuthFilter

```
파일: security/JwtAuthFilter.java (신규 생성)
패키지: com.devmatch.security
```

**역할:** HTTP 요청의 Authorization 헤더에서 JWT를 추출하고 인증 처리

**동작 흐름:**

1. `Authorization: Bearer {token}` 헤더에서 토큰 추출
2. `JwtTokenProvider.validateToken()`으로 유효성 검증
3. 유효하면 토큰에서 사용자 정보 추출
4. `UsernamePasswordAuthenticationToken` 생성 → SecurityContext에 설정
5. 유효하지 않으면 필터 체인 계속 진행 (인증 실패는 Security가 처리)

**extends:** `OncePerRequestFilter`

### 5.3 CustomUserDetails

```
파일: security/CustomUserDetails.java (신규 생성)
패키지: com.devmatch.security
```

- `UserDetails` 구현
- User 엔티티를 감싸서 Spring Security가 인식할 수 있는 형태로 변환
- `getAuthorities()` → Role을 `ROLE_MENTEE`, `ROLE_MENTOR`, `ROLE_ADMIN` 형태로 반환

---

## 6. Security 설정

### 6.1 SecurityConfig

```
파일: config/SecurityConfig.java
```

**SecurityFilterChain 설정:**

```
접근 허용 (permitAll):
  - POST /api/auth/signup
  - POST /api/auth/login
  - POST /api/auth/refresh
  - GET  /api/auth/oauth2/**
  - GET  /swagger-ui/**, /v3/api-docs/**

인증 필요 (authenticated):
  - GET/PUT /api/users/me
  - POST    /api/mentor/apply
  - 그 외 /api/** 경로

관리자 전용 (ROLE_ADMIN):
  - /api/admin/**
```

**핵심 설정:**

- CSRF 비활성화 (JWT 기반 Stateless이므로)
- 세션 정책: `STATELESS`
- `JwtAuthFilter`를 `UsernamePasswordAuthenticationFilter` 앞에 등록
- `BCryptPasswordEncoder` Bean 등록
- OAuth2 로그인 성공/실패 핸들러 설정

### 6.2 CorsConfig

```
파일: config/CorsConfig.java
```

| 항목 | 값 |
|------|-----|
| allowedOrigins | http://localhost:3000 (개발), 프로덕션 도메인 |
| allowedMethods | GET, POST, PUT, DELETE, OPTIONS |
| allowedHeaders | Authorization, Content-Type |
| allowCredentials | true |
| maxAge | 3600 |

---

## 7. DTO 설계

### 7.1 인증 관련 DTO

```
파일: dto/auth/SignupRequest.java (신규)
```

| 필드 | 타입 | 검증 | 설명 |
|------|------|------|------|
| email | String | @Email, @NotBlank | 이메일 |
| password | String | @NotBlank, @Size(min=8, max=20) | 비밀번호 |
| name | String | @NotBlank, @Size(min=2, max=50) | 이름 |

```
파일: dto/auth/LoginRequest.java (신규)
```

| 필드 | 타입 | 검증 | 설명 |
|------|------|------|------|
| email | String | @Email, @NotBlank | 이메일 |
| password | String | @NotBlank | 비밀번호 |

```
파일: dto/auth/TokenResponse.java (신규)
```

| 필드 | 타입 | 설명 |
|------|------|------|
| accessToken | String | JWT Access Token |
| refreshToken | String | JWT Refresh Token |
| tokenType | String | "Bearer" (고정) |

```
파일: dto/auth/TokenRefreshRequest.java (신규)
```

| 필드 | 타입 | 검증 | 설명 |
|------|------|------|------|
| refreshToken | String | @NotBlank | 갱신할 Refresh Token |

### 7.2 사용자 관련 DTO

```
파일: dto/user/UserResponse.java (기존 파일 활용)
```

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 사용자 ID |
| email | String | 이메일 |
| name | String | 이름 |
| role | String | 역할 |
| createdAt | LocalDateTime | 가입일 |

```
파일: dto/user/UserUpdateRequest.java (신규)
```

| 필드 | 타입 | 검증 | 설명 |
|------|------|------|------|
| name | String | @Size(min=2, max=50) | 이름 변경 (nullable) |
| password | String | @Size(min=8, max=20) | 비밀번호 변경 (nullable) |

### 7.3 멘토 관련 DTO

```
파일: dto/mentor/MentorApplyRequest.java (신규)
```

| 필드 | 타입 | 검증 | 설명 |
|------|------|------|------|
| specialty | List\<String\> | @NotEmpty | 전문 분야 |
| careerYears | Integer | @NotNull, @Min(1) | 경력 연수 |
| company | String | nullable | 현재 회사 |
| bio | String | @Size(max=1000) | 자기 소개 |

```
파일: dto/mentor/MentorProfileResponse.java (신규)
```

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 멘토 프로필 ID |
| userId | Long | 사용자 ID |
| name | String | 멘토 이름 |
| email | String | 이메일 |
| specialty | List\<String\> | 전문 분야 |
| careerYears | Integer | 경력 연수 |
| company | String | 회사 |
| bio | String | 자기 소개 |
| status | String | 심사 상태 |

### 7.4 공통 응답 DTO

```
파일: dto/common/ApiResponse.java (신규)
```

| 필드 | 타입 | 설명 |
|------|------|------|
| success | boolean | 성공 여부 |
| message | String | 응답 메시지 |
| data | T (Generic) | 응답 데이터 |

---

## 8. Service 설계

### 8.1 AuthService

```
파일: service/AuthService.java
```

| 메서드 | 반환타입 | 설명 |
|--------|----------|------|
| signup(SignupRequest) | UserResponse | 회원가입 (이메일 중복 확인 → BCrypt 암호화 → DB 저장) |
| login(LoginRequest) | TokenResponse | 로그인 (이메일/비밀번호 검증 → JWT 발급 → Refresh Token Redis 저장) |
| refresh(TokenRefreshRequest) | TokenResponse | 토큰 갱신 (Refresh Token 검증 → 새 Access Token 발급) |
| logout(Long userId) | void | 로그아웃 (Redis에서 Refresh Token 삭제) |

### 8.2 UserService

```
파일: service/UserService.java (신규 생성)
```

| 메서드 | 반환타입 | 설명 |
|--------|----------|------|
| getMyProfile(Long userId) | UserResponse | 내 정보 조회 |
| updateMyProfile(Long userId, UserUpdateRequest) | UserResponse | 내 정보 수정 |

### 8.3 MentorService

```
파일: service/MentorService.java (신규 생성)
```

| 메서드 | 반환타입 | 설명 |
|--------|----------|------|
| apply(Long userId, MentorApplyRequest) | MentorProfileResponse | 멘토 신청 (중복 신청 방지, MentorProfile 생성) |
| getMyMentorProfile(Long userId) | MentorProfileResponse | 내 멘토 프로필 조회 |

---

## 9. Controller (API 엔드포인트) 설계

### 9.1 AuthController

```
파일: controller/AuthController.java
경로: /api/auth
```

| HTTP | 경로 | 메서드 | 인증 | 설명 |
|------|------|--------|------|------|
| POST | /signup | signup() | 불필요 | 회원가입 |
| POST | /login | login() | 불필요 | 로그인 → JWT 발급 |
| POST | /refresh | refresh() | 불필요 | Access Token 갱신 |
| POST | /logout | logout() | 필요 | 로그아웃 (Refresh Token 삭제) |

### 9.2 UserController

```
파일: controller/UserController.java (신규 생성)
경로: /api/users
```

| HTTP | 경로 | 메서드 | 인증 | 설명 |
|------|------|--------|------|------|
| GET | /me | getMyProfile() | 필요 | 내 정보 조회 |
| PUT | /me | updateMyProfile() | 필요 | 내 정보 수정 |

### 9.3 MentorController

```
파일: controller/MentorController.java (신규 생성)
경로: /api/mentor
```

| HTTP | 경로 | 메서드 | 인증 | 설명 |
|------|------|--------|------|------|
| POST | /apply | apply() | 필요 | 멘토 신청 |
| GET | /me | getMyMentorProfile() | 필요 | 내 멘토 프로필 조회 |

---

## 10. 예외 처리 설계

### 10.1 커스텀 예외 클래스

```
패키지: com.devmatch.exception
```

| 예외 클래스 | HTTP 상태 | 사용 상황 |
|-------------|-----------|-----------|
| DuplicateEmailException | 409 Conflict | 이메일 중복 회원가입 시 |
| InvalidCredentialsException | 401 Unauthorized | 로그인 실패 (이메일/비밀번호 불일치) |
| UserNotFoundException | 404 Not Found | 존재하지 않는 사용자 조회 시 |
| InvalidTokenException | 401 Unauthorized | JWT 토큰 유효하지 않을 때 |
| AlreadyAppliedException | 409 Conflict | 멘토 중복 신청 시 |
| AccessDeniedException | 403 Forbidden | 권한 없는 리소스 접근 시 |

### 10.2 GlobalExceptionHandler

```
파일: exception/GlobalExceptionHandler.java
```

- `@RestControllerAdvice`로 전역 예외 처리
- 각 커스텀 예외 → 적절한 HTTP 상태 코드 + `ApiResponse` 형태로 반환
- `MethodArgumentNotValidException` → 400 Bad Request (Validation 실패)
- `Exception` → 500 Internal Server Error (예상치 못한 오류)

---

## 11. 신규 생성 파일 목록

기존 빈 파일 외에 새로 추가해야 할 파일 목록입니다.

| 파일 경로 | 설명 |
|-----------|------|
| `entity/Role.java` | 사용자 역할 Enum |
| `entity/MentorStatus.java` | 멘토 심사 상태 Enum |
| `security/JwtTokenProvider.java` | JWT 토큰 생성/검증 |
| `security/JwtAuthFilter.java` | JWT 인증 필터 |
| `security/CustomUserDetails.java` | Spring Security UserDetails 구현 |
| `dto/auth/SignupRequest.java` | 회원가입 요청 DTO |
| `dto/auth/LoginRequest.java` | 로그인 요청 DTO |
| `dto/auth/TokenResponse.java` | 토큰 응답 DTO |
| `dto/auth/TokenRefreshRequest.java` | 토큰 갱신 요청 DTO |
| `dto/user/UserUpdateRequest.java` | 프로필 수정 요청 DTO |
| `dto/mentor/MentorApplyRequest.java` | 멘토 신청 요청 DTO |
| `dto/mentor/MentorProfileResponse.java` | 멘토 프로필 응답 DTO |
| `dto/common/ApiResponse.java` | 공통 API 응답 래퍼 |
| `service/UserService.java` | 사용자 서비스 |
| `service/MentorService.java` | 멘토 서비스 |
| `controller/UserController.java` | 사용자 컨트롤러 |
| `controller/MentorController.java` | 멘토 컨트롤러 |
| `exception/DuplicateEmailException.java` | 이메일 중복 예외 |
| `exception/InvalidCredentialsException.java` | 인증 실패 예외 |
| `exception/UserNotFoundException.java` | 사용자 미존재 예외 |
| `exception/InvalidTokenException.java` | 토큰 무효 예외 |
| `exception/AlreadyAppliedException.java` | 중복 신청 예외 |
| `config/JpaAuditingConfig.java` | JPA Auditing 활성화 설정 |
| `config/RedisConfig.java` | Redis 설정 (StringRedisTemplate 등) |

---

## 12. 구현 순서 (권장)

의존성 순서를 고려한 단계별 구현 순서입니다. 각 단계가 완료되면 빌드 및 테스트를 수행합니다.

### Step 1: 기반 계층 (Entity + Config)

1. `Role.java`, `MentorStatus.java` — Enum 정의
2. `StringListConverter.java` — JSON 변환기
3. `User.java` — 사용자 엔티티
4. `MentorProfile.java` — 멘토 프로필 엔티티
5. `JpaAuditingConfig.java` — `@EnableJpaAuditing`
6. `RedisConfig.java` — Redis 설정

**검증:** `docker-compose up` → 애플리케이션 시작 → DDL 자동 생성 확인

### Step 2: 보안 계층 (JWT + Security)

1. `JwtTokenProvider.java` — 토큰 생성/검증
2. `CustomUserDetails.java` — UserDetails 구현
3. `JwtAuthFilter.java` — JWT 인증 필터
4. `SecurityConfig.java` — Security 설정
5. `CorsConfig.java` — CORS 설정

**검증:** 허용된 경로(Swagger 등) 접근 가능, 보호된 경로는 401 반환 확인

### Step 3: DTO + 예외

1. `ApiResponse.java` — 공통 응답 래퍼
2. 인증 DTO — SignupRequest, LoginRequest, TokenResponse, TokenRefreshRequest
3. 사용자 DTO — UserResponse, UserUpdateRequest
4. 멘토 DTO — MentorApplyRequest, MentorProfileResponse
5. 커스텀 예외 클래스 5개
6. `GlobalExceptionHandler.java`

**검증:** 컴파일 확인

### Step 4: 회원가입 + 로그인

1. `UserRepository.java`
2. `AuthService.java` — signup(), login()
3. `AuthController.java` — POST /signup, POST /login

**검증:** Swagger에서 회원가입 → 로그인 → JWT 발급 확인

### Step 5: 토큰 갱신 + 로그아웃

1. `AuthService.java` — refresh(), logout() 추가
2. `AuthController.java` — POST /refresh, POST /logout 추가

**검증:** Access Token 만료 후 Refresh Token으로 갱신 확인

### Step 6: 마이페이지

1. `UserService.java`
2. `UserController.java` — GET /me, PUT /me

**검증:** JWT 포함 요청으로 내 정보 조회/수정 확인

### Step 7: 멘토 신청

1. `MentorProfileRepository.java`
2. `MentorService.java`
3. `MentorController.java` — POST /apply, GET /me

**검증:** 멘토 신청 → DB 저장 → 프로필 조회 확인

### Step 8: Swagger 설정

1. `SwaggerConfig.java` — JWT Bearer 인증 스키마 설정

**검증:** Swagger UI에서 Authorize 버튼으로 JWT 인증 후 보호된 API 호출 확인

---

## 13. API 흐름도

### 회원가입 흐름

```
Client → POST /api/auth/signup (email, password, name)
  → AuthController.signup()
    → AuthService.signup()
      → UserRepository.existsByEmail() → 중복이면 DuplicateEmailException
      → BCryptPasswordEncoder.encode(password)
      → UserRepository.save(user)
      → UserResponse 반환
  ← 201 Created + ApiResponse<UserResponse>
```

### 로그인 흐름

```
Client → POST /api/auth/login (email, password)
  → AuthController.login()
    → AuthService.login()
      → UserRepository.findByEmail() → 없으면 InvalidCredentialsException
      → BCryptPasswordEncoder.matches() → 불일치하면 InvalidCredentialsException
      → JwtTokenProvider.generateAccessToken()
      → JwtTokenProvider.generateRefreshToken()
      → Redis에 Refresh Token 저장 (key: refreshToken:{userId}, TTL: 7일)
      → TokenResponse 반환
  ← 200 OK + ApiResponse<TokenResponse>
```

### JWT 인증 흐름 (모든 보호된 요청)

```
Client → GET /api/users/me (Authorization: Bearer {accessToken})
  → JwtAuthFilter.doFilterInternal()
    → Authorization 헤더에서 토큰 추출
    → JwtTokenProvider.validateToken() → 실패하면 필터 통과 (인증 없이)
    → JwtTokenProvider.getUserIdFromToken(), getRoleFromToken()
    → UsernamePasswordAuthenticationToken 생성
    → SecurityContextHolder에 설정
  → SecurityFilterChain 검사 → 인증 성공
  → UserController.getMyProfile()
```

### 토큰 갱신 흐름

```
Client → POST /api/auth/refresh (refreshToken)
  → AuthController.refresh()
    → AuthService.refresh()
      → JwtTokenProvider.validateToken(refreshToken) → 실패하면 InvalidTokenException
      → Redis에서 저장된 Refresh Token과 비교 → 불일치하면 InvalidTokenException
      → 새 Access Token + Refresh Token 발급
      → Redis에 새 Refresh Token 저장 (기존 것 덮어쓰기)
      → TokenResponse 반환
  ← 200 OK + ApiResponse<TokenResponse>
```

---

## 14. DB 스키마 (예상)

JPA ddl-auto: update 설정에 의해 자동 생성됩니다.

```sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(50) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'MENTEE',
    provider VARCHAR(20),
    provider_id VARCHAR(100),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);

CREATE TABLE mentor_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    specialty TEXT,
    career_years INT NOT NULL,
    company VARCHAR(100),
    bio VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

---

## 15. 참고 사항

### 보안 고려사항

- 비밀번호는 반드시 BCrypt로 암호화 저장 (평문 저장 절대 금지)
- JWT Secret Key는 환경변수로 관리 (`${JWT_SECRET}`)
- Refresh Token은 Redis에 TTL과 함께 저장 (서버 재시작 시에도 유지)
- API 응답에 password 필드 노출 금지 (DTO 분리)

### 테스트 전략

- Service 단위 테스트: JUnit5 + Mockito (Repository Mock)
- Controller 통합 테스트: @WebMvcTest + MockMvc
- 전체 통합 테스트: @SpringBootTest (Docker MySQL 연동)

### Google OAuth2 소셜 로그인

- Phase 2에서는 기본 구조만 준비 (provider, providerId 필드)
- 실제 OAuth2 핸들러 구현은 Google API 연동(Phase 4)과 함께 진행
- SecurityConfig에 OAuth2 Login 경로는 미리 설정해 두되 핸들러는 추후 구현