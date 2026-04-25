# Admin 레이아웃 가드가 SUPER_ADMIN 을 거부 — 403 페이지로 차단

- 발생 일시: 2026-04-25
- 영역: frontend
- 심각도: medium

## 증상

DB role 이 `SUPER_ADMIN` 인 계정으로 로그인 후 `/admin/dashboard` 접속 시 "403 · 접근 권한 없음 / 관리자 전용 페이지입니다" 화면. 백엔드 API 는 정상 (CustomUserDetails 가 SUPER_ADMIN 에게 ROLE_ADMIN 도 부여), 프론트 가드만 차단.

## 원인

[frontend/src/app/admin/layout.tsx:46](../frontend/src/app/admin/layout.tsx:46) 가 `user?.role !== 'ADMIN'` 으로만 체크. `SUPER_ADMIN` 은 별도 role 문자열이라 이 비교에서 떨어짐.

같은 파일의 헤더 링크 가드 [Header.tsx](../frontend/src/components/layout/Header.tsx) 도 동일한 누락이었고, 이번 PR 의 직전 커밋에서 이미 수정. 레이아웃 가드는 그 수정에서 빠졌다가 SUPER_ADMIN 시드 계정으로 진입했을 때 비로소 노출됨.

Phase II 까지는 admin@devmatch.com 계정의 DB role 이 (구) `ADMIN` 이었던 환경에서 동작해 이 버그가 드러나지 않음. 이번에 SUPER_ADMIN 으로 업데이트하면서 처음 노출.

## 해결 방법

[frontend/src/app/admin/layout.tsx:46](../frontend/src/app/admin/layout.tsx:46) 의 가드를 두 role 모두 허용하도록 변경:

```tsx
// before
if (user?.role !== 'ADMIN') { ... 403 ... }

// after
if (user?.role !== 'ADMIN' && user?.role !== 'SUPER_ADMIN') { ... 403 ... }
```

상단 JSDoc 도 함께 갱신.

## 재발 방지 / 메모

- 프론트엔드 role gate 는 항상 `ADMIN || SUPER_ADMIN` 으로 묶는 게 안전. SUPER_ADMIN 은 ADMIN 권한의 상위 집합인데, 문자열 동치 비교는 그 관계를 표현하지 못함.
- 백엔드는 Spring Security 의 `hasRole("ADMIN")` 체크 + CustomUserDetails 가 SUPER_ADMIN 에게 ROLE_ADMIN 을 함께 부여하는 패턴으로 이미 호환됨 — 프론트만 추가 작업이 필요.
- 잔여 후보: [Header.tsx](../frontend/src/components/layout/Header.tsx) 의 roleLabel (`role === 'MENTOR' ? '멘토' : role === 'ADMIN' ? '관리자' : '멘티'`) 가 SUPER_ADMIN 에게 "멘티" 라벨을 줌. 별개 cosmetic 이슈로 이번 PR 범위 밖.
