# `/lms/assignments` 빌드 실패 — `useSearchParams()` 를 Suspense 경계 없이 사용

- 발생일: 2026-04-21
- 영역: frontend (Next.js 14 App Router)
- 심각도: medium (로컬 `next build` 가 실패하여 CI 통과 불가)

## 증상

`admin-mentor-ui` 브랜치에서 최초 `npx next build` 실행 시 다음 에러로 정적 페이지 생성 단계가 실패.

```
⨯ useSearchParams() should be wrapped in a suspense boundary at page "/lms/assignments".
  Read more: https://nextjs.org/docs/messages/missing-suspense-with-csr-bailout

Error occurred prerendering page "/lms/assignments". Read more: https://nextjs.org/docs/messages/prerender-error

> Export encountered errors on following paths:
    /lms/assignments/page: /lms/assignments
```

해당 브랜치의 내 커밋들은 원인이 아니었음 — `main` (3b5fabe) 에서도 동일 에러 재현. PR #33 (`83eab53 fix: 멘티 신청 흐름 복원 및 LMS 과제·화상회의 화면 정리`) 이 머지된 이후 빌드가 깨져 있었음.

## 원인

Next.js 14.2 부터 **App Router + `'use client'` + `useSearchParams()`** 조합은 **반드시 상위에 `<Suspense>` 경계**를 요구함. `/lms/assignments/page.tsx` 는 페이지 최상단 클라이언트 컴포넌트에서 직접 `useSearchParams()` 를 호출하고 있었음 ([frontend/src/app/lms/assignments/page.tsx:38](../frontend/src/app/lms/assignments/page.tsx:38)).

정적 프리렌더링 시점에 `useSearchParams()` 가 서스펜드를 발생시키는데 감쌀 바운더리가 없어 에러가 npm/next 빌더로 전파되어 페이지 export 가 실패.

참고: `export const dynamic = 'force-dynamic'` 만으로는 충분하지 않음 (프리렌더링 시 metadata/edge 에셋 생성 경로를 탈때 동일 경로에서 에러). Suspense 래핑이 정석.

## 해결 방법

기존 함수 본문을 `AssignmentsPageInner` 로 이름만 바꾸고, 새 `default export AssignmentsPage` 에서 `<Suspense fallback={...}>` 로 감싸는 최소 변경:

```tsx
// frontend/src/app/lms/assignments/page.tsx
import { Suspense, useEffect, useMemo, useState } from 'react';
...

function AssignmentsPageInner() { /* 원래 520줄 본문 그대로 */ }

export default function AssignmentsPage() {
  return (
    <Suspense fallback={<div className="mx-auto max-w-5xl px-4 py-10">
      <div className="h-8 w-48 animate-pulse rounded bg-slate-200" />
    </div>}>
      <AssignmentsPageInner />
    </Suspense>
  );
}
```

수정 후 `npx next build` 성공 (`○ /lms/assignments` 로 정적 프리렌더링됨).

## 재발 방지 / 메모

- **`useSearchParams()` 포함 페이지 작성 체크리스트:**
  - 페이지 default export 는 `<Suspense>` 로 감쌀 것.
  - 훅은 Suspense 내부의 inner 컴포넌트에서 호출.
  - fallback 은 실제 UI 와 layout shift 가 최소화되도록 skeleton 제공.
- 같은 세션에서 작성한 `/admin/mentor/page.tsx` 는 처음부터 Suspense 래핑 패턴을 적용 — 신규 페이지는 모두 이 패턴을 따른다.
- `main` 에 이미 머지된 상태였음 → 다른 기존 페이지에도 같은 이슈가 있을 가능성 점검 필요 (follow-up). `useSearchParams` 를 grep 해서 래핑 여부 전수 검증을 다음 이터레이션에 수행.
- **2026-04-21 follow-up audit 결과 — 전수 통과.** `useSearchParams` 사용 15 파일 모두 안전:
  - Suspense 로 감싼 7 페이지: `survey`, `payment/success`, `matching/recommend`, `lms/assignments`, `auth/login`, `apply/payment`, `admin/mentor` → ○ 정적 프리렌더.
  - [`(dashboard)/layout.tsx`](../frontend/src/app/lms/(dashboard)/layout.tsx:3) 의 `export const dynamic = 'force-dynamic'` 덕에 `lms/(dashboard)/*` 7 페이지 + `LmsSidebar` 는 ƒ 동적 렌더 → 프리렌더 스킵이라 Suspense 불필요. `next build` 30/30 통과로 실증됨.
