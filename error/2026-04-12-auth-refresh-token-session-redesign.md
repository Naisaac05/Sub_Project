# Refresh Token 세션 재설계 (Redis 세션 + HttpOnly 쿠키 + Rotation/Reuse Detection)

- 발생일: 2026-04-12
- 영역: backend + frontend
- 심각도: high (보안/세션 관리 근간)

## 증상

기존 구조의 문제가 한꺼번에 드러남:

1. 백엔드를 잠깐 내렸다 올리고 나면 로그인이 풀림. 사용자 입장에서는 "데이터를 불러오는데 에러가 있습니다" + 강제 로그아웃.
2. Refresh token을 localStorage에 그대로 저장 → XSS 한 번이면 장기 세션 탈취 가능.
3. Redis에 `refreshToken:{userId}` 단일 키로 저장 → 한 사용자당 1개 기기만 로그인 유지 가능, 새 기기에서 로그인하면 기존 기기가 조용히 로그아웃됨.
4. 재사용 탐지 없음: 털린 refresh token이 여러 번 갱신에 쓰여도 감지 불가.
5. Redis 컨테이너가 AOF 없이 동작 → 컨테이너 재시작 시 모든 세션 증발.

## 원인

- `AuthService`가 flat key(`refreshToken:{userId}`)와 평문 저장으로 설계되어 있어 다중 기기/회전/재사용 탐지 어느 것도 지원하지 못함.
- JWT refresh token이 `sid`/`gen` 같은 세션 식별 claim을 갖지 않아 "어떤 세션의 몇 세대 토큰인가"를 판별할 수 없었음.
- Redis docker 설정이 기본값이라 persistence(AOF/RDB) 미활성화. 컨테이너 재생성 시 세션 전멸.
- Refresh token을 프런트 localStorage로 관리해 XSS 노출면이 컸고, CORS/쿠키 흐름도 없었음.

## 해결 방법

### 백엔드 — 세션 모델 Redis 재설계

- `backend/src/main/java/com/devmatch/service/RefreshSessionService.java` (신규)
  - Key layout:
    - `session:{sessionId}` HASH: `{ userId, tokenHash, deviceInfo, ip, issuedAt, lastUsedAt, generation }`
    - `user_sessions:{userId}` SET: 해당 유저의 모든 sessionId
    - `revoked_session:{sessionId}` STRING (TTL = `app.auth.reuse-window-seconds`): 재사용 탐지용 블랙리스트
  - `createSession(userId, deviceInfo, ip)` — UUID sessionId, generation=1 로 JWT 발급 + persist
  - `rotate(presentedToken)` — 회전 + 재사용 탐지:
    1. JWT 서명 검증
    2. `revoked_session:{sid}` 존재 시 → **사용자의 모든 세션 revoke** 후 401 (유출 신호)
    3. session hash 로드, 없으면 401
    4. userId/generation/tokenHash(SHA-256) 모두 일치해야 통과. 하나라도 어긋나면 해당 세션 revoke 후 401
    5. 통과 시 generation+1, 새 JWT 발급, tokenHash 갱신
  - Token은 항상 SHA-256 해시로만 저장(원문 저장 X)
  - `revokeByToken` / `revokeAllForUser` 제공

- `backend/src/main/java/com/devmatch/security/JwtTokenProvider.java`
  - `generateRefreshToken(userId, sessionId, generation)` — `sid`, `gen` claim 추가
  - `getSessionIdFromToken`, `getGenerationFromToken` 추가

- `backend/src/main/java/com/devmatch/service/AuthService.java`
  - flat Redis 키 + 평문 저장 로직 제거
  - `login` → `RefreshSessionService.createSession` 사용, `AuthTokens(accessToken, refreshToken)` 반환
  - `refresh` → `RefreshSessionService.rotate` 사용
  - `logout` → `RefreshSessionService.revokeByToken` 사용

### 백엔드 — HttpOnly 쿠키 전환

- `backend/src/main/java/com/devmatch/config/AuthProperties.java` (신규)
  - `@ConfigurationProperties("app.auth")` 로 쿠키 이름/경로/secure/sameSite + reuseWindowSeconds 바인딩
- `backend/src/main/resources/application.yml`
  - `app.auth.refresh-cookie.*` (Path=`/api/auth`, SameSite=Strict, Secure는 env)
  - `app.auth.reuse-window-seconds: 300`
- `backend/src/main/java/com/devmatch/controller/AuthController.java`
  - `login`/`refresh`/`logout` 모두 `ResponseCookie`로 쿠키 set/clear
  - `refresh`는 바디가 아니라 쿠키에서 토큰을 읽음
  - 응답 바디에는 accessToken만 (refreshToken 필드 제거)
  - User-Agent/X-Forwarded-For/remoteAddr 로 deviceInfo/ip 기록
- `backend/src/main/java/com/devmatch/dto/auth/TokenResponse.java`
  - `refreshToken` 필드 제거
- `backend/src/main/java/com/devmatch/dto/auth/TokenRefreshRequest.java` — 삭제 (더 이상 사용 안 함)
- `backend/src/main/java/com/devmatch/config/SecurityConfig.java`
  - `/api/auth/logout` permitAll — 쿠키 기반이라 access token이 만료돼도 logout은 가능해야 함
- `backend/src/main/java/com/devmatch/exception/GlobalExceptionHandler.java`
  - `RefreshSessionService.InvalidRefreshSessionException` → 401 매핑

### 인프라 — Redis persistence

- `docker-compose.yml` Redis 서비스에 AOF + 보조 RDB 스냅샷 활성화:
  ```yaml
  command: >
    redis-server
    --appendonly yes
    --appendfsync everysec
    --save 900 1 --save 300 10 --save 60 10000
  ```
  `redis_data` 볼륨은 이미 존재. 컨테이너 재시작 후에도 세션이 살아남음.

### 프런트 — access token만 직접 관리

- `frontend/src/lib/token.ts` — refresh token 관련 함수 제거, `getAccessToken/setAccessToken/clearAccessToken`만 남김
- `frontend/src/lib/types.ts` — `TokenResponse`에서 `refreshToken` 제거, `TokenRefreshRequest` 삭제
- `frontend/src/lib/api.ts`
  - `withCredentials: true` 로 axios 인스턴스 생성 → 쿠키 자동 송수신
  - 401 인터셉터의 refresh 호출은 바디 없이 `/auth/refresh` 호출 (쿠키가 자동 첨부)
- `frontend/src/lib/auth.ts`
  - `login`은 응답에서 accessToken만 저장
  - `refresh`는 바디 없이 호출
  - `logout`은 엔드포인트 호출 후 access token만 삭제 (서버가 쿠키 만료)
  - `clearTokens` 별칭 유지 → `AuthContext`가 기존 이름 그대로 호출해도 호환

## 재발 방지 / 메모

- 앞으로 refresh token은 **절대** JS에서 접근 가능한 스토리지(localStorage/sessionStorage)에 두지 않는다. HttpOnly + SameSite=Strict + Path=`/api/auth` 유지.
- 운영 배포 시 `REFRESH_COOKIE_SECURE=true` 필수 (HTTPS).
- **적용 절차 (한 번 필요)**:
  1. `docker compose up -d redis` (AOF 설정이 반영되도록 재생성 필요하면 `docker compose rm -sf redis` 후 `up -d redis`)
  2. 백엔드 재시작 (JVM을 내렸다 올려야 새 클래스 반영 — 2026-04-12 커리큘럼 주차 사건과 동일)
  3. 모든 기존 세션은 1회 재로그인 필요 (Redis 키 스키마 변경)
- 재사용 탐지 윈도우는 `app.auth.reuse-window-seconds` (기본 300초). 이 시간 안에 revoke된 토큰이 다시 쓰이면 "유출됐다"고 보고 해당 유저 전체 세션을 revoke함.
- 멀티 디바이스 로그인은 이제 자연스럽게 지원됨 (`user_sessions:{userId}` SET).
- 2026-04-12 "데이터 불러오기 에러 + 강제 로그아웃" 증상은 이 재설계 이후 재현되지 않아야 함. 만약 재현된다면:
  1. 백엔드 로그에 `InvalidRefreshSessionException` 메시지 확인
  2. Redis `KEYS session:*` / `KEYS user_sessions:*` 확인
  3. 프런트 브라우저 DevTools → Application → Cookies → `refresh_token` 존재 확인
