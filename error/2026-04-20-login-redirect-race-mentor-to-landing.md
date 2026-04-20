# 로그인 직후 MENTOR가 `/mentor/status`가 아닌 랜딩으로 가는 경쟁 조건

- 발생일: 2026-04-20
- 영역: frontend
- 심각도: medium

## 증상

멘토로 회원가입 → 로그인 페이지에서 로그인 → 기대: `/mentor/status` 로 이동.
실제: 곧바로 `/` (랜딩) 으로 이동.

## 원인

`frontend/src/app/auth/login/page.tsx` 에서 로그인 후 라우팅 로직이 두 군데로 갈라져 있었고 **경쟁 조건**이 발생.

1. 상단 `useEffect` — 이미 로그인된 유저를 로그인 페이지에서 쫓아내기 위한 것:
   ```ts
   useEffect(() => {
     if (!authLoading && isLoggedIn) {
       router.replace(redirectParam || '/');
     }
   }, [authLoading, isLoggedIn, router, redirectParam]);
   ```
2. `handleSubmit` 내부:
   ```ts
   await login(email, password);
   // ... role 조회 후 MENTOR이면 router.push('/mentor/status'), 아니면 '/'
   ```

`login()` 이 성공하면 `AuthContext` 가 `user` 와 `isLoggedIn` 을 갱신 → 리렌더 시 `useEffect` 가 먼저 발화해 `router.replace('/')` 를 실행 → `handleSubmit` 의 `router.push('/mentor/status')` 는 이미 `/` 로 넘어간 뒤 실행되어 묻힘.

## 해결 방법

라우팅 로직을 `useEffect` 한 곳으로 합치고, `user.role` 을 읽어 역할 기반 분기를 수행하도록 수정.

`frontend/src/app/auth/login/page.tsx:31-48`:
```ts
useEffect(() => {
  if (authLoading) return;
  if (!isLoggedIn || !user) return;

  if (redirectParam) {
    router.replace(redirectParam);
  } else if (user.role === 'MENTOR') {
    router.replace('/mentor/status');
  } else {
    router.replace('/');
  }
}, [authLoading, isLoggedIn, user, router, redirectParam]);
```

`handleSubmit` 은 `await login(...)` 까지만 수행하고 라우팅은 하지 않음 — `user` 상태가 반영되면 위 `useEffect` 가 한 번만 올바른 목적지로 보낸다.

사용하지 않게 된 `import * as authService from '@/lib/auth'` 도 제거.

## 재발 방지 / 메모

- React 에서 "로그인 직후 라우팅" 을 두 군데 (mutation 핸들러 + `isLoggedIn` 의존 `useEffect`) 에 동시에 두면 항상 경쟁 조건 위험. **한쪽으로 통일**한다.
- 역할/상태에 의존하는 라우팅은 인증 상태가 확정된 뒤에만 계산되도록 `useEffect` 에 `user`, `user.role` 을 의존성으로 넣는다.
- 이번 턴에서는 `/mentor/status` 가 이동 목적지였지만, 같은 패턴이 다른 역할 기반 랜딩(예: ADMIN → `/admin`) 에도 그대로 적용된다.
