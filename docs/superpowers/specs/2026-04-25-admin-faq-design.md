# Phase III Feature 2 — FAQ 관리 (admin-faq) 디자인 스펙

> 2026-04-25 작성 · 브레인스토밍 결과 정리

## 배경 / 동기

`/faq` 공개 페이지는 이미 존재하지만 [frontend/src/app/faq/page.tsx](../../../frontend/src/app/faq/page.tsx) 에 FAQ 9건이 **하드코딩** 돼 있다. 운영자가 FAQ 를 추가/수정하려면 코드 변경 + 배포가 필요한 상태. 이번 단계에서 DB 기반으로 전환하고 어드민 콘솔에 FAQ 관리 메뉴를 추가한다. ROADMAP §10 의 admin API 표 마지막 항목 (`CRUD /api/admin/faqs`) 을 마무리하는 작업이다.

어드민 콘솔 메뉴 6개 (대시보드 / 멘토 심사 / 회원 / 결제 / 게시물 / FAQ) 가 모두 채워지며 Phase III Feature 1 (대시보드) 와 짝을 이뤄 Phase III 가 완결된다.

## 작업 범위

### 포함

- 새 엔티티 `Faq` + DB 테이블 + Repository
- 백엔드 5 endpoint (공개 1 + 어드민 4)
- 어드민 페이지 `/admin/faqs` (테이블 + 다이얼로그 + 순서 변경)
- 공개 페이지 `/faq` 의 데이터 출처를 하드코딩 → API 호출로 교체 (UI 레이아웃 동일)
- DataInitializer 에 기존 9개 FAQ 시드
- 어드민 사이드바에 메뉴 1개 추가
- 배포 시 수동 DDL (ROADMAP §10 에 추가)

### 명시적 제외 (YAGNI)

| 항목 | 제외 사유 |
|------|----------|
| 카테고리 자체의 CRUD | enum 5개 고정으로 충분 |
| 검색 (공개·어드민 양쪽) | FAQ 50건 미만 가정 — 시각 스캔으로 충분 |
| 드래그앤드롭 정렬 | 위/아래 화살표로 인접 swap 만 |
| Markdown / 리치 텍스트 | 기존 9건 모두 plain prose, 운영 부담만 ↑ |
| Soft delete + 감사 로그 | FAQ 삭제는 운영 정보가 아닌 정리 작업 |
| 페이지네이션 | 50건 미만 가정 |
| 다국어 | enum 영문값만 노출, 한글 라벨은 프론트에서만 |

## 도메인 모델

### `Faq` 엔티티

| 컬럼 | 타입 | 제약 | 비고 |
|------|------|------|------|
| `id` | BIGINT PK | auto-increment | |
| `category` | VARCHAR(20) | NOT NULL | enum 문자열 (아래 참고) |
| `question` | VARCHAR(200) | NOT NULL | 짧은 질문 1줄 |
| `answer` | TEXT | NOT NULL | plain text, 줄바꿈만 |
| `order_index` | INT | NOT NULL DEFAULT 0 | 카테고리 내 정렬 키 (작을수록 위) |
| `published` | BOOLEAN | NOT NULL DEFAULT TRUE | 공개 페이지 노출 여부 |
| `created_at` | DATETIME(6) | NOT NULL | |
| `updated_at` | DATETIME(6) | NOT NULL | |

복합 인덱스 `(published, category, order_index)` — 공개 페이지 쿼리 (`WHERE published=true ORDER BY category, order_index`) 최적화.

### `FaqCategory` enum (백엔드 + 프론트 모두)

```
SERVICE_INTRO   → 서비스 소개
TEST            → 실력 테스트
MENTORING       → 멘토링
PAYMENT         → 결제/환불
MENTOR_APPLY    → 멘토 지원
```

영문 enum 만 백엔드/JSON 으로 노출하고, 한국어 표시명 매핑은 **프론트에서만** 처리한다. i18n 의 첫걸음이며, 향후 영어 페이지가 생겨도 백엔드 변경 없이 매핑만 추가 가능.

## 백엔드 API

| Method | Path | 권한 | 응답 |
|--------|------|------|------|
| GET | `/api/faqs` | 누구나 (비로그인 포함) | 공개 FAQ 목록 — `published=true` 만, `(category, order_index)` 정렬 |
| GET | `/api/admin/faqs` | ADMIN+ | 전체 FAQ — published 무관, 같은 정렬, 페이지네이션 X |
| POST | `/api/admin/faqs` | ADMIN+ | 생성 — order_index 는 **해당 카테고리 내** `MAX(order_index)+1` 자동 할당 |
| PUT | `/api/admin/faqs/{id}` | ADMIN+ | 부분 수정 — 모든 필드 + `published` 토글, `order_index` 는 별도 endpoint 가 아닌 이 PUT 으로도 가능 |
| DELETE | `/api/admin/faqs/{id}` | ADMIN+ | hard delete — 감사 로그 안 남김 |

### 순서 변경 시 동작

어드민 UI 의 "위/아래" 버튼은 **같은 카테고리 내** 인접한 두 FAQ 의 `order_index` 만 swap 하는 PUT 두 번을 호출. (다른 카테고리 항목과는 상호작용 안 함.) 카테고리 내 첫 항목의 위 화살표, 마지막 항목의 아래 화살표는 비활성화. 백엔드는 단일 PUT `/admin/faqs/{id}` 만 노출하고, swap 로직은 프론트 책임. 작업 단순성을 위해.

대안 (PUT `/admin/faqs/{id}/reorder?direction=up|down`) 은 검토했으나 백엔드 컨트롤러가 단순 CRUD 4개로 끝나는 게 더 깔끔하다고 판단.

### 권한

ADMIN+ = `ADMIN` 또는 `SUPER_ADMIN`. SecurityConfig 의 `/api/admin/**` → `hasRole("ADMIN")` 룰을 그대로 따름. SUPER_ADMIN 은 CustomUserDetails 가 ROLE_ADMIN 도 함께 부여하므로 자동 호환.

공개 GET `/api/faqs` 는 `permitAll()` 추가 필요.

## 어드민 UI — `/admin/faqs`

### 레이아웃

회원관리·게시물관리와 동일한 패턴:
- 페이지 헤더: "FAQ 관리" + "+ FAQ 추가" 버튼
- 카테고리 필터 (탭) — 전체 / 서비스 소개 / 실력 테스트 / 멘토링 / 결제/환불 / 멘토 지원
- 테이블 1개:
  - 컬럼: 카테고리 / 질문 (text-truncate) / 공개 / 순서 / 액션
  - "공개" 컬럼은 토글 스위치 (즉시 PUT)
  - "순서" 컬럼은 위/아래 화살표 두 개 (인접 swap, 끝 항목은 비활성화)
  - "액션" 컬럼은 수정 / 삭제 두 버튼

### 다이얼로그 (생성·수정 공용)

- 카테고리: shadcn `Select` (5개 옵션)
- 질문: `Input` (max 200)
- 답변: `Textarea` (rows=6, max 2000 — 컬럼 TEXT 제한은 훨씬 크지만 UX 가이드)
- 공개 여부: shadcn `Switch` (생성 시 기본 ON)
- 저장 / 취소

### 삭제 확인

기존 어드민 패턴대로 shadcn `AlertDialog` 로 한 번 확인.

## 공개 페이지 — `/faq`

기존 [page.tsx](../../../frontend/src/app/faq/page.tsx) 의 UI (히어로 섹션 + 카테고리 필터 + 아코디언 + 문의 배너) **레이아웃 그대로 유지**, 데이터 출처만 교체:

- 페이지 진입 시 `GET /api/faqs` 한 번 호출
- 응답을 `useState` 로 보관, 카테고리 필터 + 아코디언 로직은 동일
- 로딩 중: 카테고리 탭 자리는 비워두고 카드 영역에 스켈레톤 3~4개
- 에러: "FAQ 를 불러오지 못했습니다 / 재시도" 카드 (대시보드의 SectionError 와 같은 패턴, 단 페이지 단독이라 컴포넌트 재사용보단 inline 으로 처리)

기존 하드코딩 데이터가 9건뿐이고 카테고리도 enum 5개와 동일한 매핑이라 마이그레이션이 매끄럽다.

## 시드 데이터

`DataInitializer` 에 메서드 추가 — `if (faqRepository.count() == 0)` 일 때만 기존 9개 FAQ 를 enum 매핑으로 INSERT. 각 FAQ 의 `order_index` 는 카테고리 내 등장 순서 (0, 1, 2…).

```
서비스 소개 / DevMatch는 어떤 서비스인가요?           → SERVICE_INTRO, 0
서비스 소개 / 멘토는 어떤 분들인가요?                  → SERVICE_INTRO, 1
실력 테스트 / 실력 테스트는 무료인가요?                → TEST, 0
실력 테스트 / 테스트 결과는 어떻게 활용되나요?          → TEST, 1
멘토링 / 멘토링은 어떤 방식으로 진행되나요?            → MENTORING, 0
멘토링 / 멘토링 기간과 횟수는 어떻게 되나요?           → MENTORING, 1
결제/환불 / 결제는 어떻게 하나요?                      → PAYMENT, 0
결제/환불 / 환불 정책은 어떻게 되나요?                 → PAYMENT, 1
멘토 지원 / 멘토로 활동하고 싶은데 어떻게 지원하나요?  → MENTOR_APPLY, 0
```

## 사이드바 + 라우팅

`AdminSidebar.tsx` 의 `NAV_ITEMS` 배열에 항목 1개 추가 — 게시물 관리 다음 위치, 관리자 계정 (SUPER_ADMIN) 앞:

```
대시보드
멘토 심사
회원 관리
결제 관리
게시물 관리
FAQ 관리          ← 추가
관리자 계정 (SUPER_ADMIN)
```

아이콘은 `HelpCircle` (lucide-react). 기존 어드민 메뉴 아이콘과 톤 일관.

## 배포 시 수동 작업

prod 는 `ddl-auto: validate` 이므로 ROADMAP §10 배포 체크리스트에 항목 12 추가:

```sql
CREATE TABLE faq (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  category VARCHAR(20) NOT NULL,
  question VARCHAR(200) NOT NULL,
  answer TEXT NOT NULL,
  order_index INT NOT NULL DEFAULT 0,
  published BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  INDEX idx_faq_published_category_order (published, category, order_index)
);
```

환경변수 변경 없음, feature flag 없음.

## 테스트 전략

### 백엔드

- `FaqServiceTest` (Mockito) — order_index 자동 할당, published 토글, 카테고리별 조회
- `FaqControllerTest` (`@SpringBootTest + @AutoConfigureMockMvc`) — 권한 매트릭스
  - 비로그인 GET `/api/faqs` → 200
  - 비로그인 GET `/api/admin/faqs` → 403
  - MENTEE → 403
  - ADMIN GET / POST / PUT / DELETE 모두 200
  - SUPER_ADMIN 도 동일

### 프론트

- 타입 체크 + 빌드 통과
- 수동 검증: 어드민 CRUD 시나리오 + 공개 페이지가 정상 노출

## 작업 규모 추정

| 영역 | 커밋 수 |
|------|--------|
| 백엔드 (엔티티 / Repo / Service / Controller / 테스트) | 5~6 |
| 프론트 (API client / 어드민 페이지 / 다이얼로그 / 공개 페이지 마이그레이션 / 사이드바) | 5~6 |
| 시드 + DDL/ROADMAP/overview 문서 | 2~3 |
| **합계** | **12~15** |

회원관리(Phase II Feature 1) 와 비슷한 규모. 1~1.5일 분량.

## 결정 요약

| 결정 항목 | 선택 | 메모 |
|----------|------|------|
| 카테고리 모델링 | enum 5개 고정 | 카테고리 CRUD 없음 |
| 정렬 | `order_index` 명시 정렬 | 위/아래 버튼으로 인접 swap |
| 공개/비공개 | `published` boolean | 기본 TRUE |
| 답변 형식 | plain text (multi-line) | textarea + whitespace-pre-line |
| 검색 | 안 함 | 50건 미만 가정 |
| 어드민 UI | 회원/게시물과 동일 패턴 | 테이블 + 다이얼로그 |
| 삭제 | hard delete | 감사 로그 안 남김 |
| 권한 | ADMIN+ (ADMIN ∪ SUPER_ADMIN) | 공개 GET 만 permitAll |
| 페이지네이션 | 안 함 | 50건 미만 가정 |
| Markdown | 안 함 | YAGNI |
