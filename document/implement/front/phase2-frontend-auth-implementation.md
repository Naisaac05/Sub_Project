# Phase 2: 회원 시스템 — Frontend 구현 결과서

> 구현일: 2026-04-02 | 프로젝트: DevMatch | 기반: phase2-backend-auth-implementation.md

---

## 1. 구현 완료 요약

Phase 2 회원 시스템 프론트엔드 전체 구현을 완료했습니다.
백엔드 API(8개 엔드포인트)와 연결하여 회원가입, 로그인, 토큰 관리, 프로필 관리를 구현합니다.

| 항목 | 수량 |
|------|------|
| 신규 파일 | 5개 |
| 수정 파일 | 4개 |
| 연결된 API | 6개 (signup, login, refresh, logout, getMe, updateMe) |

---

## 2. 신규/수정 파일 목록

### 신규 파일
| 파일 | 설명 |
|------|------|
| `src/lib/types.ts` | 백엔드 DTO 대응 TypeScript 인터페이스 |
| `src/lib/auth.ts` | 인증 API 호출 함수 모음 |
| `src/contexts/AuthContext.tsx` | 전역 인증 상태 관리 (React Context) |
| `src/app/Providers.tsx` | 전역 Provider wrapper |
| `src/app/mypage/page.tsx` | 마이페이지 (프로필 조회/수정) |

### 수정 파일
| 파일 | 변경 사항 |
|------|-----------|
| `src/lib/api.ts` | 401 시 token refresh 자동 시도 + 재요청 |
| `src/app/auth/login/page.tsx` | AuthContext 연결, API 호출, 에러/로딩 처리 |
| `src/app/auth/signup/page.tsx` | AuthContext 연결, API 호출, 유효성 검증 |
| `src/components/layout/Header.tsx` | 로그인 상태별 UI 분기 (드롭다운 메뉴) |
| `src/app/layout.tsx` | AuthProvider 적용 |

---

## 3. 상세 구현 내용

### 3.1 TypeScript Types (`src/lib/types.ts`)

```typescript
// 백엔드 API 공통 응답
interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

// 인증 DTO
interface SignupRequest { email, password, name }
interface LoginRequest { email, password }
interface TokenResponse { accessToken, refreshToken, tokenType }
interface TokenRefreshRequest { refreshToken }

// 사용자 DTO
interface UserResponse { id, email, name, role, createdAt }
interface UserUpdateRequest { name?, password? }
```

### 3.2 Auth Service (`src/lib/auth.ts`)

| 함수 | API | 동작 |
|------|-----|------|
| `signup(data)` | POST /api/auth/signup | 회원가입 요청 |
| `login(data)` | POST /api/auth/login | 로그인 + 토큰 저장 |
| `refresh()` | POST /api/auth/refresh | 토큰 갱신 |
| `logout()` | POST /api/auth/logout | 로그아웃 + 토큰 삭제 |
| `getMyProfile()` | GET /api/users/me | 내 정보 조회 |
| `updateMyProfile(data)` | PUT /api/users/me | 프로필 수정 |

### 3.3 AuthContext (`src/contexts/AuthContext.tsx`)

- **상태**: `user`, `isLoading`, `isLoggedIn`
- **메서드**: `login()`, `signup()`, `logout()`, `refreshUser()`
- **초기화**: 앱 로드 시 localStorage에 accessToken이 있으면 사용자 정보 자동 복원

### 3.4 API Client 개선 (`src/lib/api.ts`)

- 401 에러 시 refresh token으로 자동 갱신 시도
- refresh 성공 → 원래 요청 자동 재시도
- refresh 실패 → localStorage 정리 + 로그인 페이지 리다이렉트
- refresh 요청 자체는 무한루프 방지

### 3.5 Login 페이지 연결

- `useAuth()` 훅으로 `login()` 호출
- 로딩 spinner, 에러 메시지(빨간색 알림) 표시
- 성공 시 `/` 리다이렉트
- 이미 로그인 상태면 자동 리다이렉트

### 3.6 Signup 페이지 연결

- 비밀번호 확인 일치 검증
- 비밀번호 길이 검증 (8~20자)
- 이름 길이 검증 (2~50자)
- 성공 시 로그인 페이지로 이동 + 성공 메시지
- 에러(이메일 중복 등) 처리

### 3.7 Header 동적 인증 상태

- **비로그인**: "로그인" + "시작하기" 버튼
- **로그인**: 사용자 아바타(이니셜) + 드롭다운 메뉴
  - 드롭다운: 사용자 이름/이메일 표시, 마이페이지 링크, 로그아웃 버튼

### 3.8 마이페이지 (`/mypage`)

- 로그인 필수 (미로그인 시 `/auth/login` 리다이렉트)
- 사용자 정보 표시: 이름, 이메일, 역할, 가입일
- 이름/비밀번호 수정 기능

---

## 4. API 연결 흐름도

### 회원가입 → 로그인

```
[Signup 페이지]
  → form 유효성 검증 (클라이언트)
  → POST /api/auth/signup { email, password, name }
  → 성공 → 로그인 페이지로 이동

[Login 페이지]
  → POST /api/auth/login { email, password }
  → 성공 → localStorage에 accessToken/refreshToken 저장
  → GET /api/users/me → AuthContext에 user 설정
  → 홈 페이지로 이동
```

### 인증된 요청

```
[API 요청]
  → axios 인터셉터: Authorization: Bearer {accessToken} 자동 첨부
  → 401 응답 시:
    → POST /api/auth/refresh { refreshToken }
    → 성공 → 새 토큰 저장 → 원래 요청 재시도
    → 실패 → 로그아웃 처리 → /auth/login 리다이렉트
```

---

## 5. 테스트 순서

1. **회원가입**: `/auth/signup` → 이름/이메일/비밀번호 입력 → 가입하기
2. **로그인**: `/auth/login` → 이메일/비밀번호 입력 → 로그인
3. **Header 확인**: 로그인 후 Header에 사용자 아이콘 표시 확인
4. **마이페이지**: `/mypage` → 내 정보 확인 → 이름 변경
5. **로그아웃**: Header 드롭다운 → 로그아웃 → Header 복원 확인
