# Phase 2: 회원 시스템 — Backend 구현 결과서

> 구현일: 2026-04-02 | 프로젝트: DevMatch | 기반: 03-phase2-backend-auth-plan.md

---

## 1. 구현 완료 요약

Phase 2 회원 시스템 백엔드 전체 구현을 완료했습니다.
`gradle compileJava` 빌드 성공 확인 완료.

| 항목 | 수량 |
|------|------|
| 신규/수정 Java 파일 | 35개 |
| API 엔드포인트 | 8개 |
| 패키지 | 8개 (entity, config, security, dto, exception, repository, service, controller) |

---

## 2. 프로젝트 구조

```
backend/src/main/java/com/devmatch/
├── DevMatchApplication.java
├── common/
│   └── Constants.java
├── config/
│   ├── CorsConfig.java
│   ├── JpaAuditingConfig.java
│   ├── RedisConfig.java
│   ├── SecurityConfig.java
│   └── SwaggerConfig.java
├── controller/
│   ├── AuthController.java
│   ├── MentorController.java
│   └── UserController.java
├── dto/
│   ├── auth/
│   │   ├── LoginRequest.java
│   │   ├── SignupRequest.java
│   │   ├── TokenRefreshRequest.java
│   │   └── TokenResponse.java
│   ├── common/
│   │   └── ApiResponse.java
│   ├── mentor/
│   │   ├── MentorApplyRequest.java
│   │   └── MentorProfileResponse.java
│   └── user/
│       ├── UserResponse.java
│       └── UserUpdateRequest.java
├── entity/
│   ├── MentorProfile.java
│   ├── MentorStatus.java
│   ├── Role.java
│   ├── StringListConverter.java
│   └── User.java
├── exception/
│   ├── AlreadyAppliedException.java
│   ├── DuplicateEmailException.java
│   ├── GlobalExceptionHandler.java
│   ├── InvalidCredentialsException.java
│   ├── InvalidTokenException.java
│   └── UserNotFoundException.java
├── repository/
│   ├── MentorProfileRepository.java
│   └── UserRepository.java
├── security/
│   ├── CustomUserDetails.java
│   ├── JwtAuthFilter.java
│   └── JwtTokenProvider.java
└── service/
    ├── AuthService.java
    ├── MentorService.java
    └── UserService.java
```

---

## 3. Entity 계층

### 3.1 Role.java

```java
package com.devmatch.entity;

public enum Role {
    MENTEE,   // 일반 사용자 (기본값)
    MENTOR,   // 승인된 멘토
    ADMIN     // 관리자
}
```

### 3.2 MentorStatus.java

```java
package com.devmatch.entity;

public enum MentorStatus {
    PENDING,   // 심사 대기
    APPROVED,  // 승인 완료
    REJECTED   // 거절
}
```

### 3.3 User.java

```java
package com.devmatch.entity;

@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor @Builder
public class User {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;                    // BCrypt 암호화 저장

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.MENTEE;

    @Column(length = 20)
    private String provider;                    // 소셜 로그인 제공자

    @Column(length = 100)
    private String providerId;                  // 소셜 로그인 고유 ID

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 변경 메서드
    public void updateName(String name) { this.name = name; }
    public void updatePassword(String encodedPassword) { this.password = encodedPassword; }
    public void updateRole(Role role) { this.role = role; }
}
```

**설계 포인트:**
- `@EntityListeners(AuditingEntityListener.class)` → createdAt/updatedAt 자동 관리
- `@Builder.Default` → role 기본값 MENTEE
- password 변경은 `updatePassword(encodedPassword)` — 이미 인코딩된 값만 받음
- provider/providerId는 nullable → Phase 4 Google OAuth2 연동 시 활용

### 3.4 MentorProfile.java

```java
package com.devmatch.entity;

@Entity
@Table(name = "mentor_profiles")
@EntityListeners(AuditingEntityListener.class)
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor @Builder
public class MentorProfile {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;                          // User와 1:1 관계

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> specialty;             // JSON으로 DB 저장

    @Column(nullable = false)
    private Integer careerYears;

    @Column(length = 100)
    private String company;

    @Column(length = 1000)
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MentorStatus status = MentorStatus.PENDING;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
```

### 3.5 StringListConverter.java

```java
package com.devmatch.entity;

@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        // List<String> → JSON 문자열 (DB 저장)
        if (attribute == null || attribute.isEmpty()) return "[]";
        return objectMapper.writeValueAsString(attribute);
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        // JSON 문자열 → List<String> (엔티티 매핑)
        if (dbData == null || dbData.isBlank()) return Collections.emptyList();
        return objectMapper.readValue(dbData, new TypeReference<>() {});
    }
}
```

---

## 4. Repository 계층

### 4.1 UserRepository.java

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByProviderAndProviderId(String provider, String providerId);
}
```

### 4.2 MentorProfileRepository.java

```java
public interface MentorProfileRepository extends JpaRepository<MentorProfile, Long> {
    Optional<MentorProfile> findByUser(User user);
    Optional<MentorProfile> findByUserId(Long userId);
    List<MentorProfile> findByStatus(MentorStatus status);
    boolean existsByUserId(Long userId);
}
```

---

## 5. Security 계층

### 5.1 JwtTokenProvider.java

JWT 토큰 생성, 검증, 파싱을 담당합니다.

```java
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;   // 1시간 (3600000ms)
    private final long refreshTokenExpiration;  // 7일 (604800000ms)

    // application.yml에서 jwt.secret, jwt.access-token-expiration, jwt.refresh-token-expiration 주입
}
```

| 메서드 | 설명 |
|--------|------|
| `generateAccessToken(userId, email, role)` | Access Token 생성 — Claims: sub(userId), email, role |
| `generateRefreshToken(userId)` | Refresh Token 생성 — Claims: sub(userId)만 포함 |
| `validateToken(token)` | 토큰 유효성 검증 (만료, 서명 불일치 등 → false) |
| `getUserIdFromToken(token)` | 토큰에서 userId(subject) 추출 |
| `getEmailFromToken(token)` | 토큰에서 email claim 추출 |
| `getRoleFromToken(token)` | 토큰에서 role claim 추출 → Role enum 반환 |
| `getRefreshTokenExpiration()` | Redis TTL 설정에 사용 |

**사용 라이브러리:** jjwt 0.12.6 (`Jwts.builder()`, `Jwts.parser().verifyWith()`)

### 5.2 JwtAuthFilter.java

`OncePerRequestFilter`를 상속하여 모든 HTTP 요청에서 JWT를 검사합니다.

```
요청 → Authorization 헤더 확인
  → "Bearer {token}" 형식이면 토큰 추출
    → validateToken() 성공 시 → SecurityContext에 인증 정보 설정
    → 실패 시 → 필터 통과 (Security가 401 처리)
```

### 5.3 CustomUserDetails.java

Spring Security의 `UserDetails` 인터페이스 구현체입니다.

| 필드 | 설명 |
|------|------|
| userId (Long) | DB의 User.id |
| email (String) | 로그인 이메일 |
| role (Role) | 사용자 역할 |

- `getAuthorities()` → `ROLE_MENTEE`, `ROLE_MENTOR`, `ROLE_ADMIN` 형태로 반환
- Controller에서 `@AuthenticationPrincipal CustomUserDetails`로 현재 사용자 정보 접근

---

## 6. Config 계층

### 6.1 SecurityConfig.java

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    // SecurityFilterChain 설정
    // - CSRF 비활성화 (JWT Stateless)
    // - 세션 정책: STATELESS
    // - JwtAuthFilter를 UsernamePasswordAuthenticationFilter 앞에 등록
    // - BCryptPasswordEncoder Bean 등록
}
```

**경로별 접근 제어:**

| 경로 | 접근 권한 |
|------|-----------|
| `POST /api/auth/signup, /login, /refresh` | permitAll |
| `/swagger-ui/**, /v3/api-docs/**` | permitAll |
| `/api/admin/**` | ROLE_ADMIN |
| 나머지 `/api/**` | authenticated |

### 6.2 CorsConfig.java

| 항목 | 값 |
|------|-----|
| allowedOrigins | `http://localhost:3000` |
| allowedMethods | GET, POST, PUT, DELETE, OPTIONS |
| allowedHeaders | Authorization, Content-Type |
| allowCredentials | true |
| maxAge | 3600초 |

### 6.3 SwaggerConfig.java

- OpenAPI 3.0 기반 API 문서화
- JWT Bearer Authentication 스키마 설정
- Swagger UI에서 `Authorize` 버튼으로 토큰 입력 후 보호된 API 호출 가능

### 6.4 JpaAuditingConfig.java

- `@EnableJpaAuditing` — Entity의 `@CreatedDate`, `@LastModifiedDate` 자동 관리

### 6.5 RedisConfig.java

- `StringRedisTemplate` Bean 등록 — Refresh Token 저장/조회/삭제에 사용

---

## 7. DTO 계층

### 7.1 인증 DTO (dto/auth/)

| 클래스 | 용도 | 필드 |
|--------|------|------|
| `SignupRequest` | 회원가입 요청 | email(@Email), password(@Size 8~20), name(@Size 2~50) |
| `LoginRequest` | 로그인 요청 | email(@Email), password(@NotBlank) |
| `TokenResponse` | 토큰 응답 | accessToken, refreshToken, tokenType("Bearer") |
| `TokenRefreshRequest` | 토큰 갱신 요청 | refreshToken(@NotBlank) |

### 7.2 사용자 DTO (dto/user/)

| 클래스 | 용도 | 필드 |
|--------|------|------|
| `UserResponse` | 사용자 정보 응답 | id, email, name, role, createdAt |
| `UserUpdateRequest` | 프로필 수정 요청 | name(@Size 2~50, nullable), password(@Size 8~20, nullable) |

- `UserResponse.from(User)` — Entity → DTO 변환 정적 메서드

### 7.3 멘토 DTO (dto/mentor/)

| 클래스 | 용도 | 필드 |
|--------|------|------|
| `MentorApplyRequest` | 멘토 신청 요청 | specialty(@NotEmpty), careerYears(@Min 1), company, bio(@Size max 1000) |
| `MentorProfileResponse` | 멘토 프로필 응답 | id, userId, name, email, specialty, careerYears, company, bio, status |

- `MentorProfileResponse.from(MentorProfile)` — Entity → DTO 변환 정적 메서드

### 7.4 공통 DTO (dto/common/)

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;

    // 팩토리 메서드
    static <T> ApiResponse<T> success(T data);
    static <T> ApiResponse<T> success(String message, T data);
    static <T> ApiResponse<T> error(String message);
}
```

---

## 8. Service 계층

### 8.1 AuthService.java

| 메서드 | 동작 |
|--------|------|
| `signup(SignupRequest)` | 이메일 중복 확인 → BCrypt 암호화 → DB 저장 → UserResponse 반환 |
| `login(LoginRequest)` | 이메일로 조회 → 비밀번호 검증 → Access/Refresh Token 생성 → Redis에 Refresh Token 저장 (TTL 7일) |
| `refresh(TokenRefreshRequest)` | Refresh Token 유효성 검증 → Redis 저장 값과 비교 → 새 토큰 쌍 발급 → Redis 갱신 |
| `logout(userId)` | Redis에서 Refresh Token 삭제 |

**Redis 키 패턴:** `refreshToken:{userId}`

### 8.2 UserService.java

| 메서드 | 동작 |
|--------|------|
| `getMyProfile(userId)` | userId로 User 조회 → UserResponse 반환 |
| `updateMyProfile(userId, request)` | name/password 변경 (null이 아닌 필드만) → UserResponse 반환 |

### 8.3 MentorService.java

| 메서드 | 동작 |
|--------|------|
| `apply(userId, request)` | 중복 신청 확인 → MentorProfile 생성 (status: PENDING) → MentorProfileResponse 반환 |
| `getMyMentorProfile(userId)` | userId로 MentorProfile 조회 → MentorProfileResponse 반환 |

---

## 9. Controller 계층 (API 엔드포인트)

### 9.1 AuthController — `/api/auth`

| HTTP | 경로 | 인증 | 요청 DTO | 응답 DTO | 상태 코드 |
|------|------|------|----------|----------|-----------|
| POST | /signup | X | SignupRequest | ApiResponse\<UserResponse\> | 201 Created |
| POST | /login | X | LoginRequest | ApiResponse\<TokenResponse\> | 200 OK |
| POST | /refresh | X | TokenRefreshRequest | ApiResponse\<TokenResponse\> | 200 OK |
| POST | /logout | O | — | ApiResponse\<Void\> | 200 OK |

### 9.2 UserController — `/api/users`

| HTTP | 경로 | 인증 | 요청 DTO | 응답 DTO | 상태 코드 |
|------|------|------|----------|----------|-----------|
| GET | /me | O | — | ApiResponse\<UserResponse\> | 200 OK |
| PUT | /me | O | UserUpdateRequest | ApiResponse\<UserResponse\> | 200 OK |

### 9.3 MentorController — `/api/mentor`

| HTTP | 경로 | 인증 | 요청 DTO | 응답 DTO | 상태 코드 |
|------|------|------|----------|----------|-----------|
| POST | /apply | O | MentorApplyRequest | ApiResponse\<MentorProfileResponse\> | 201 Created |
| GET | /me | O | — | ApiResponse\<MentorProfileResponse\> | 200 OK |

---

## 10. 예외 처리

### 10.1 커스텀 예외 클래스

| 예외 | HTTP 상태 | 발생 상황 |
|------|-----------|-----------|
| `DuplicateEmailException` | 409 Conflict | 회원가입 시 이메일 중복 |
| `InvalidCredentialsException` | 401 Unauthorized | 로그인 실패 (이메일/비밀번호 불일치) |
| `UserNotFoundException` | 404 Not Found | 존재하지 않는 사용자 조회 |
| `InvalidTokenException` | 401 Unauthorized | JWT 토큰 유효하지 않음 |
| `AlreadyAppliedException` | 409 Conflict | 멘토 중복 신청 |

### 10.2 GlobalExceptionHandler

`@RestControllerAdvice`로 전역 예외를 잡아 `ApiResponse` 형태로 반환합니다.

| 처리 대상 | HTTP 상태 | 응답 |
|-----------|-----------|------|
| 커스텀 예외 5종 | 각각 다름 | `ApiResponse.error(message)` |
| `MethodArgumentNotValidException` | 400 Bad Request | 필드별 검증 오류 메시지 |
| `Exception` (기타) | 500 Internal Server Error | "서버 내부 오류가 발생했습니다" |

---

## 11. API 흐름도

### 회원가입

```
POST /api/auth/signup { email, password, name }
  → AuthService.signup()
    → existsByEmail() 중복 확인
    → BCrypt 암호화
    → UserRepository.save()
  ← 201 { success: true, message: "회원가입이 완료되었습니다", data: { id, email, name, role, createdAt } }
```

### 로그인

```
POST /api/auth/login { email, password }
  → AuthService.login()
    → findByEmail() → 없으면 401
    → passwordEncoder.matches() → 불일치 401
    → generateAccessToken() + generateRefreshToken()
    → Redis SET refreshToken:{userId} (TTL 7일)
  ← 200 { success: true, data: { accessToken, refreshToken, tokenType: "Bearer" } }
```

### 인증된 요청

```
GET /api/users/me
  Header: Authorization: Bearer {accessToken}
  → JwtAuthFilter
    → 토큰 추출 → validateToken()
    → SecurityContext에 CustomUserDetails 설정
  → UserController.getMyProfile()
    → UserService.getMyProfile(userId)
  ← 200 { success: true, data: { id, email, name, role, createdAt } }
```

### 토큰 갱신

```
POST /api/auth/refresh { refreshToken }
  → AuthService.refresh()
    → validateToken() → 실패 시 401
    → Redis에 저장된 토큰과 비교 → 불일치 시 401
    → 새 Access + Refresh Token 발급
    → Redis 갱신
  ← 200 { success: true, data: { accessToken, refreshToken, tokenType: "Bearer" } }
```

---

## 12. DB 스키마 (JPA 자동 생성)

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

## 13. 실행 방법

### 1) Docker로 MySQL + Redis 실행

```bash
docker-compose up -d mysql redis
```

### 2) 애플리케이션 실행

```bash
cd backend
bash gradlew bootRun
```

### 3) Swagger UI 접속

```
http://localhost:8080/swagger-ui.html
```

### 4) 테스트 순서

1. **회원가입:** POST `/api/auth/signup` — email, password, name 입력
2. **로그인:** POST `/api/auth/login` — email, password 입력 → accessToken 획득
3. **Swagger Authorize:** 상단 Authorize 버튼 → `Bearer {accessToken}` 입력
4. **내 정보 조회:** GET `/api/users/me`
5. **멘토 신청:** POST `/api/mentor/apply` — specialty, careerYears 등 입력
6. **토큰 갱신:** POST `/api/auth/refresh` — refreshToken 입력
7. **로그아웃:** POST `/api/auth/logout`

---

## 14. 향후 작업 (Phase 3 이후)

- Phase 3: 테스트 & 멘토 매칭 시스템
- Phase 4: Google OAuth2 소셜 로그인 핸들러 구현 (provider/providerId 필드 활용)
- Admin API: 멘토 승인/거절 처리 (`/api/admin/mentors/{id}/approve`)
- 단위 테스트 및 통합 테스트 작성
