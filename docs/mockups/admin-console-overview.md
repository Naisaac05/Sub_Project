# 관리자 콘솔 개요 (Admin Console Overview)

> 여러 Phase 에 걸쳐 추가되는 관리자 기능의 **네비게이션 구조**와 **문서 인덱스**. 각 메뉴별 상세 목업은 해당 문서 참고.

---

## Phase별 메뉴 로드맵

| Phase | 메뉴 | 문서 | 상태 |
|-------|------|------|------|
| I | 멘토 심사 | [admin-mentor.md](./admin-mentor.md) | 2026-04-21 결정 확정, 구현 진행 중 |
| II | 회원 관리 | [admin-users.md](./admin-users.md) | 목업 작성 (2026-04-22) |
| II | 결제 관리 | [admin-payments.md](./admin-payments.md) | 목업 작성 (2026-04-22) |
| II | 게시물 관리 | [admin-posts.md](./admin-posts.md) | 목업 작성 (2026-04-22) |
| III | 대시보드 / 통계 | [admin-dashboard.md](./admin-dashboard.md) | 2026-04-24 구현 완료 |
| III (예정) | FAQ 관리 | — | ROADMAP `CRUD /api/admin/faqs` |

---

## 사이드바 최종 구조 (Phase II 완료 시)

```
┌──────────────┬─────────────────────────────────────────┐
│ 관리자 콘솔  │                                         │
├──────────────┤                                         │
│ 📊 대시보드  │                                         │
│ 🧑 멘토 심사 │                                         │
│ 👥 회원 관리 │                                         │
│ 💳 결제 관리 │                                         │
│ 📝 게시물    │                                         │
│              │                                         │
│ — (Phase III) │                                        │
│ ❓ FAQ        │                                         │
└──────────────┴─────────────────────────────────────────┘
```

- `admin-mentor.md` 결정사항 #5 에 따라 관리자 콘솔은 neutral 톤 헤더 + 고정 사이드바(220px).
- 사이드바 컴포넌트는 `components/admin/AdminSidebar.tsx` (신규) 로 통합 관리. Phase II 에서 4개 메뉴로 확장.
- 각 메뉴의 활성화 여부는 `usePathname()` 으로 판정.

---

## 라우팅 트리

```
/admin                          → 첫 메뉴로 리다이렉트 (현재 /admin/mentor)
/admin/mentor                   Phase I
/admin/mentor/[id]              Phase I
/admin/users                    Phase II
/admin/users/[id]               Phase II
/admin/payments                 Phase II
/admin/payments/[id]            Phase II
/admin/posts                    Phase II
/admin/posts/[id]               Phase II
/admin/*                        비-ADMIN → 403
```

---

## 백엔드 엔드포인트 요약 (Phase II 신규 작업량)

| 컨트롤러 | 엔드포인트 수 | 비고 |
|---------|--------------|------|
| `AdminUserController` (신규) | 3개 | 목록 / 상세 / 역할 변경 |
| `AdminPaymentController` (신규) | 4개 | 목록 / 상세 / 환불 / 통계 |
| `AdminPostController` (신규) | 4개 | 목록 / 상세 / 게시물 삭제 / 댓글 삭제 |
| **합계** | **11개** | — |

### 엔티티 변경

- `User.lastLoginAt` 추가 고려 (선택)
- `Payment.processedByAdminId`, `Payment.cancelledAt` 추가 고려
- `Post.deleted`, `Post.deletionReason`, `Post.deletedBy`, `Post.deletedAt` 추가 (소프트 삭제 시)
- `Comment` 에도 동일 소프트 삭제 컬럼

---

## shadcn 신규 컴포넌트 설치 요약 (Phase II)

이미 Phase I에서 설치된 것 (`tabs`, `table`, `dialog`, `sonner`) 외에 신규:

| 컴포넌트 | 어디서 사용 | 명령 |
|---------|-------------|------|
| `select` | 회원 역할 변경 / 게시물 카테고리 필터 | `npx shadcn@latest add select` |
| `pagination` | 세 목록 공통 | `npx shadcn@latest add pagination` |
| `popover` + `calendar` | 결제·게시물 기간 필터 | `npx shadcn@latest add popover calendar` |

---

## 구현 권장 순서

1. **공통 선작업**
   - `components/admin/AdminSidebar.tsx` 4개 메뉴로 확장
   - 공용 `<Pagination />`, `<DateRangePicker />`, `<SearchInput debounced />` 유틸 컴포넌트
   - shadcn 신규 컴포넌트 설치 커밋
2. **회원 관리** (가장 단순, 백엔드 가벼움)
3. **결제 관리** (토스 환불 연동 재활용 가능)
4. **게시물 관리** (소프트 삭제 엔티티 변경 범위가 가장 큼)

---

## 결정 필요 사항 (상위)

1. **Phase II 를 하나의 큰 PR 로?** 아니면 **메뉴별 PR 분리**?
   - 공통 작업(사이드바·페이지네이션 유틸) 을 먼저 분리 PR 로 머지 후, 메뉴별 PR 3개 권장.
2. **감사 로그 테이블** 통합 관리 여부
   - 역할 변경·결제 환불·게시물 삭제가 각각 이력을 남김 → 공통 `AdminAuditLog` 엔티티 신설 vs 엔티티별 분리.
3. **알림(이메일) 우선순위**
   - 결제 환불, 게시물 삭제 모두 "작성자/사용자에게 이메일" 을 목업에 포함했음. 이메일 인프라(현재 SMTP 설정 여부) 확인 후 축소 가능성 있음.

---

## 다음 단계

1. 이 개요 + 3개 메뉴별 상세 목업 문서 **사용자 리뷰**.
2. 결정 사항 확정.
3. `admin-mentor.md` 의 "다음 단계" 와 동일한 방식으로 각 메뉴별 `claude/admin-users-ui`, `claude/admin-payments-ui`, `claude/admin-posts-ui` worktree 분기.
