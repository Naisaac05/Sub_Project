# Admin 대시보드 API 호출 경로에 `/api` 가 중복돼 500 발생

- 발생 일시: 2026-04-25
- 영역: frontend
- 심각도: medium

## 증상

`/admin/dashboard` 진입 시 KPI · 차트 · 처리 큐 모든 섹션에 "데이터를 불러오지 못했습니다." 빨간 박스만 렌더. F12 Network 탭에서 요청 URL 이 `http://localhost:3000/api/api/admin/dashboard` 로 찍히고 status 가 `500 Internal Server Error`.

## 원인

`frontend/src/lib/api.ts` 의 axios 클라이언트는 `baseURL: '/api'` 를 갖고 있어 호출부는 `/admin/...` 으로 시작해야 함. Phase III 대시보드 작업 중 새로 만든 `frontend/src/lib/admin/dashboard.ts` 에서 절대 경로 `/api/admin/dashboard` 로 호출하면서 `/api` 가 두 번 붙어 `/api/api/admin/dashboard` 가 됨. 같은 `frontend/src/lib/admin/` 디렉토리의 [users.ts](../frontend/src/lib/admin/users.ts) · [posts.ts](../frontend/src/lib/admin/posts.ts) 는 모두 `/admin/...` 으로 호출하는데 이 패턴을 놓침.

서버 응답이 404 가 아닌 500 인 건 Next.js dev rewrite + Spring Security 의 fallback 동작 조합 때문이지만, 1차 원인은 클라이언트 경로 중복.

## 해결 방법

[frontend/src/lib/admin/dashboard.ts](../frontend/src/lib/admin/dashboard.ts) 의 두 호출에서 `/api` 프리픽스 제거:

```ts
// before
apiClient.get<...>('/api/admin/dashboard')
apiClient.get<...>('/api/admin/dashboard/audit-log')

// after
apiClient.get<...>('/admin/dashboard')
apiClient.get<...>('/admin/dashboard/audit-log')
```

## 재발 방지 / 메모

- 새 admin lib 파일을 만들 때 같은 디렉토리 기존 파일 (예: `users.ts`) 의 호출 패턴을 한 번 확인할 것 — `apiClient` 가 baseURL 을 들고 있는 구조라 절대 경로를 쓰면 항상 중복됨.
- 스펙·플랜 문서에 적어둔 백엔드 라우트 (`/api/admin/dashboard`) 는 서버 입장의 풀 경로일 뿐이라 클라이언트 코드에 그대로 복붙하면 안 됨. Plan Task 16 의 코드 블록도 동일한 함정을 갖고 있어 그대로 복사하면 같은 버그가 재현됨.
- 가능하면 `lib/admin/` 에 공용 helper(`adminGet(path)`) 를 두는 것도 고려 가능하지만, 현재 파일 수가 4개 (users/posts/dashboard/+ 매핑 안 된 것) 라 YAGNI 로 보류.
