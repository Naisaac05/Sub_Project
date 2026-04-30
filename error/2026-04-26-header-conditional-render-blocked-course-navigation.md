# Header conditional render blocked course navigation

- Date: 2026-04-26
- Area: frontend
- Severity: medium

## Symptoms

Clicking a mentoring course did not open the course detail page, and Next.js reported `Unexpected token header` from `frontend/src/components/layout/Header.tsx`.

## Cause

`Header.tsx` was still partially conflicted after resolving the admin-console menu. The mentor menu conditional render had been left as `condition ? (...)` without a `: null`, so the JSX parser failed before rendering pages that import the header, including `/mentors/[id]`.

## Fix

Restored the missing `: null` branches and kept the admin-console link available for both `ADMIN` and `SUPER_ADMIN` in `frontend/src/components/layout/Header.tsx:95`.

## Prevention / Notes

After resolving JSX conflicts, run a syntax-only check or start the affected route before assuming the conflict is resolved. A header syntax error can block many unrelated pages because it is imported globally.
