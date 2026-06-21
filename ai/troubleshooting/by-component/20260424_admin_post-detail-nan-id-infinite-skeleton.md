---
type: troubleshooting
category: inference
status: active
updated: 2026-06-18
description: "새로고침 이외 방법이 없다"

---

# Admin 게시물 상세 페이지 — 비정수 경로 진입 시 스켈레톤 무한 렌더

- 발생 일시: 2026-04-24
- 영역: frontend
- 심각도: medium

## 증상

`/admin/posts/abc` 처럼 `params.id`가 숫자로 변환되지 않는 경로로 진입하면 로딩 스켈레톤이 영원히 표시된다. 에러 메시지도 없고, 재시도 경로도 없어 사용자는 새 탭/새로고침 이외 방법이 없다.

## 원인

[frontend/src/app/admin/posts/[id]/page.tsx:20](frontend/src/app/admin/posts/[id]/page.tsx:20) 에서 `const id = Number(params.id)` 이후 effect 진입 시 `if (!id) return;` 로 가드했는데, `Number("abc")` → `NaN` → `!NaN === true` 로 early return이 발생한다.

- 초기 `loading` 상태는 `true`
- early return 한 이후 `setLoading(false)` 가 호출되지 않음
- 결과: 렌더 분기는 항상 `if (loading) return <Skeleton ... />` 에 머무름

즉 `useParams().id` 가 문자열이 맞게 왔지만 숫자 변환이 실패한 경우, 에러 상태가 아니라 로딩 상태에 갇히는 FSM 누락.

## 해결 방법

effect 첫 줄에서 유효 id 여부를 엄밀히 판별한 뒤 loading/error 상태를 즉시 확정한다:

```ts
useEffect(() => {
  if (!Number.isFinite(id) || id <= 0) {
    setError("잘못된 경로입니다");
    setLoading(false);
    return;
  }
  // ...fetch
}, [id, retryTick]);
```

동시에 에러 상태에 `재시도` 버튼을 추가하여 transient 5xx 등 다른 원인으로 에러에 빠진 경우에도 새로고침 없이 복구할 수 있게 했다. `retryTick` state를 effect deps 에 포함해 버튼 클릭 시 재실행되는 패턴.

수정 파일:
- [frontend/src/app/admin/posts/[id]/page.tsx:26](frontend/src/app/admin/posts/[id]/page.tsx:26) — `retryTick` 상태 추가
- [frontend/src/app/admin/posts/[id]/page.tsx:30-35](frontend/src/app/admin/posts/[id]/page.tsx:30) — `Number.isFinite` 가드
- [frontend/src/app/admin/posts/[id]/page.tsx:51-64](frontend/src/app/admin/posts/[id]/page.tsx:51) — 에러 Alert 내부에 재시도 버튼

커밋: `3c49554`

## 재발 방지 / 메모

- Next.js App Router의 `useParams()` 는 `string | string[] | undefined` 를 반환하므로 `Number(...)` 결과에 대해서는 반드시 `Number.isFinite + > 0` 조합으로 판별해야 안전하다. `if (!id)` 만으로는 `0` 과 `NaN` 이 구분되지 않아 FSM 상 혼란이 생긴다.
- 다른 상세 페이지들(`/admin/users/[id]`, `/admin/payments/[id]` 등)도 동일 패턴일 가능성 있음 — 점검 대상.
- 원래는 코드 리뷰 단계(superpowers:code-reviewer)에서 발견되었다. 상세 페이지 작성 시 "로딩/에러/정상/비정상 id" 4분기 렌더 매트릭스를 체크리스트화하면 사전 예방 가능.
