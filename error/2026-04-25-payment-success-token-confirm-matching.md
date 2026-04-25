# Payment success page did not confirm application matching

- Date: 2026-04-25
- Area: frontend / auth / matching
- Severity: medium

## Symptoms

The user reached the payment success screen, but the latest mentee application stayed in `SUBMITTED` status and no `matchings` row was created. The success screen still showed old copy about waiting for mentor approval even though the current policy is automatic matching.

## Cause

`frontend/src/app/payment/success/page.tsx` called `/api/applications/{id}/confirm-payment` with a raw `fetch` and read `localStorage.getItem('token')`. The app stores the access token under `accessToken` via `frontend/src/lib/token.ts`, so the request was sent without the real JWT and failed. `frontend/src/app/survey/page.tsx` had the same stale token lookup pattern.

## Fix

Added a shared `confirmApplicationPayment` API helper that uses the configured `apiClient`, then updated payment success and survey pages to call it. This lets the existing interceptor attach the correct `Authorization` header. The payment success copy was also changed to automatic matching language.

- `frontend/src/lib/application.ts:33` - added `confirmApplicationPayment`
- `frontend/src/app/payment/success/page.tsx:6` - switched success confirmation to the shared API helper
- `frontend/src/app/survey/page.tsx:6` - switched survey confirmation to the shared API helper

Verification:

```text
cd frontend
npm.cmd run build
Compiled successfully
```

## Prevention / Notes

Do not read auth tokens directly from `localStorage` in page components. API calls that require authentication should go through `apiClient` so token storage and refresh behavior stay centralized.
