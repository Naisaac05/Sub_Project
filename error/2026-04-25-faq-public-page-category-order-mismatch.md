# FAQ 공개 페이지 카테고리 순서가 어드민·의도와 다른 알파벳 순으로 노출

- 발생 일시: 2026-04-25
- 영역: frontend
- 심각도: low

## 증상

`/faq` 공개 페이지를 "전체" 필터로 보면 카테고리가 **MENTORING → MENTOR_APPLY → PAYMENT → SERVICE_INTRO → TEST** 순서 (즉 enum 영문값 알파벳 순) 로 노출된다. 같은 데이터를 어드민 페이지(`/admin/faqs`) 의 "전체" 필터에서는 **서비스 소개 → 실력 테스트 → 멘토링 → 결제/환불 → 멘토 지원** 순서로 보임. 두 화면이 일관성을 깨고, 사용자가 처음 만나는 공개 페이지가 의도와 다른 순서를 갖는다.

## 원인

[frontend/src/lib/faqs.ts](../frontend/src/lib/faqs.ts) 의 `CATEGORY_ORDER` 상수가 카테고리의 표시 순서 (서비스 소개 먼저) 를 정의하지만, 백엔드 쿼리 [FaqRepository.findByPublishedTrueOrderByCategoryAscOrderIndexAsc()](../backend/src/main/java/com/devmatch/repository/FaqRepository.java) 는 `ORDER BY category ASC` — JPA 가 `@Enumerated(EnumType.STRING)` 로 저장된 enum 을 **DB 컬럼 문자열로 비교** 하므로 알파벳 순.

어드민 페이지 [page.tsx](../frontend/src/app/admin/faqs/page.tsx) 의 `sorted` useMemo 가 클라이언트에서 `CATEGORY_ORDER.indexOf` 로 재정렬해 의도된 순서를 보존했지만, 공개 페이지는 백엔드 응답을 그대로 렌더해 알파벳 순이 그대로 노출.

## 해결 방법

공개 페이지 [frontend/src/app/faq/page.tsx](../frontend/src/app/faq/page.tsx) 의 `filtered` useMemo 에 어드민과 동일한 `CATEGORY_ORDER.indexOf` 기반 sort 추가:

```tsx
const filtered = useMemo(() => {
  if (!faqs) return [];
  const list = selected === 'ALL' ? faqs : faqs.filter((f) => f.category === selected);
  return [...list].sort((a, b) => {
    const ai = CATEGORY_ORDER.indexOf(a.category);
    const bi = CATEGORY_ORDER.indexOf(b.category);
    if (ai !== bi) return ai - bi;
    return a.orderIndex - b.orderIndex;
  });
}, [faqs, selected]);
```

## 재발 방지 / 메모

- enum 의 "표시 순서" 와 "DB 정렬 순서" 가 일치하지 않을 때 발생하는 전형적인 함정. 한쪽 페이지에서만 클라이언트 재정렬을 하면 다른 페이지가 어긋남.
- 더 깨끗한 해결안은 백엔드에서 `CASE WHEN` 으로 우선순위를 직접 부여하거나 `@OrderBy` 를 쓰는 것이지만, 카테고리 추가/순서 변경 시 백엔드도 함께 바꿔야 해 결합도가 올라간다. 현재처럼 **표시 순서를 프론트의 `CATEGORY_ORDER` 단일 출처 (single source of truth) 로 관리** 하고 어드민·공개 양쪽에서 동일하게 적용하는 패턴이 단순하고 일관적.
- 향후 새 카테고리 추가 시 [frontend/src/lib/faqs.ts](../frontend/src/lib/faqs.ts) 의 `CATEGORY_ORDER` 와 `CATEGORY_LABEL` 두 곳만 갱신하면 두 페이지 자동 반영.
