# ICON_MAP 타입 불일치 — ComponentType<{size?: number}> vs LucideIcon

- 발생일: 2026-04-20
- 영역: frontend
- 심각도: low

## 증상

`frontend/src/app/mentors/[id]/page.tsx` 에 추가한 `ICON_MAP` 선언에서 `npx tsc --noEmit` 실행 시 9개 TS2322 에러 발생.

```
error TS2322: Type 'ForwardRefExoticComponent<Omit<LucideProps, "ref"> & RefAttributes<SVGSVGElement>>'
  is not assignable to type 'ComponentType<{ size?: number | undefined; }>'.
    ...Types of property 'size' are incompatible.
    Type 'Validator<string | number | null | undefined>' is not assignable to type 'Validator<number | null | undefined>'.
```

## 원인

lucide-react의 아이콘은 `LucideIcon` 타입(`ForwardRefExoticComponent<Omit<LucideProps, ...>>`)이며,
내부 `propTypes.size`는 `Validator<string | number | null | undefined>`로 선언되어 있다.
ICON_MAP을 `Record<string, React.ComponentType<{ size?: number }>>` 로 타이핑하면
`number | undefined`만 허용하는 더 좁은 타입이 되어, 더 넓은 lucide 타입과 할당 불가.

## 해결 방법

lucide-react가 export하는 `LucideIcon` 타입을 직접 사용하도록 변경.

```tsx
// 변경 전
import { Database, ... } from 'lucide-react';
const ICON_MAP: Record<string, React.ComponentType<{ size?: number }>> = { ... };

// 변경 후
import { Database, ..., type LucideIcon } from 'lucide-react';
const ICON_MAP: Record<string, LucideIcon> = { ... };
```

관련 파일: `frontend/src/app/mentors/[id]/page.tsx:13`
수정 커밋: `7d021c3`

## 재발 방지 / 메모

- lucide-react 아이콘 컴포넌트를 map에 담을 때는 `LucideIcon`을 타입으로 써야 한다.
- `React.ComponentType<{ size?: number }>` 처럼 직접 인터페이스를 좁게 쓰면 lucide propTypes 선언과 충돌한다.
- lucide-react v0.263+ 에서 `type LucideIcon` named export 가 공식 제공됨.
