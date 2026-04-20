# Phase E — 멘토 신청 플로우 목업 (Markdown 버전)

> Pencil MCP 연결이 불가하여 마크다운으로 대체합니다. 이전 Pencil 작업의 구조를 그대로 재현했습니다.
> 각 섹션에 shadcn/ui 컴포넌트 매핑을 함께 기재합니다.

---

## E1. `/mentor/apply` — 멘토 신청 폼

**접근 권한:** 로그인 + MENTOR 역할 + `mentorStatus ∈ {NEW, REJECTED}` 만 허용
**레이아웃:** 최대 너비 880px, 세로 스크롤. 각 섹션은 Card로 구분.

```
┌─────────────────────────────────────────────────────────┐
│  [← 뒤로]  멘토 신청                                    │  ← Header
│  프로필과 경력을 입력해 멘토링 매칭에 활용됩니다.       │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐ ← REJECTED 일 때만
│ ⚠  이전 신청이 반려되었습니다                           │   Alert (variant="warning"/amber)
│    사유: "경력 증빙 자료가 부족합니다"                  │   icon: triangle-alert
│    내용을 수정하고 재제출해 주세요.                     │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ A. 기본 정보                                            │  Card
│ ─────────────────────────────────────────────────────── │  CardHeader + CardContent
│                                                         │
│  자기소개 *                                             │  Label
│  ┌───────────────────────────────────────────────────┐  │  Textarea (rows=6)
│  │ 멘토링에서 어떤 도움을 줄 수 있는지 작성하세요... │  │  최소 50자, 최대 500자
│  │                                                   │  │
│  └───────────────────────────────────────────────────┘  │
│                                        132 / 500        │  문자 카운터 (muted)
│                                                         │
│  경력 연차 *                                            │  Label
│  ┌─────────┐                                            │  Input (type=number, min=0)
│  │   5     │ 년                                         │
│  └─────────┘                                            │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ B. 전문 분야                                            │  Card
│ ─────────────────────────────────────────────────────── │
│                                                         │
│  제공 가능한 코스 * (복수 선택)                         │  Label
│  ☑ Frontend     ☑ Backend    ☐ Mobile                   │  Checkbox × N (2열 그리드)
│  ☐ DevOps       ☐ Data       ☐ AI/ML                    │  courseKeys ← /courses API
│                                                         │
│  기술 스택 *                                            │  Label
│  ┌───────────────────────────────────────────────────┐  │  Tag Input
│  │ [React ×] [TypeScript ×] [Node.js ×] │ +추가      │  │  (Input + 엔터/쉼표로 태그 추가)
│  └───────────────────────────────────────────────────┘  │
│                                                         │
│  선호 멘티 레벨 *                                       │  Label
│  ○ 주니어   ● 미들   ○ 시니어   ○ 무관                  │  RadioGroup (⚠ add 필요)
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ C. 경력 & 자격                                          │  Card
│ ─────────────────────────────────────────────────────── │
│                                                         │
│  현재 소속 (선택)                                       │  Label
│  ┌───────────────────────────────────────────────────┐  │  Input
│  │ 회사/기관명                                       │  │
│  └───────────────────────────────────────────────────┘  │
│                                                         │
│  보유 자격증 (선택)                                     │  Label
│  ┌───────────────────────────────────────────────────┐  │  Tag Input
│  │ [정보처리기사 ×] │ +추가                          │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ D. 동의                                                 │  Card
│ ─────────────────────────────────────────────────────── │
│  ☑ 제출한 정보가 사실과 다를 경우 승인이 취소될 수      │  Checkbox (필수)
│    있음에 동의합니다.                                   │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│           [ 취소 ]              [ 제출하기 → ]          │  Button × 2
│                                                         │  variant="outline" | "default"
└─────────────────────────────────────────────────────────┘
```

### shadcn 컴포넌트 매핑

| 섹션 | UI 요소 | shadcn 컴포넌트 | 비고 |
|------|---------|----------------|------|
| 헤더 | 뒤로가기 | `Button` variant="ghost" + `ArrowLeft` | |
| 반려 배너 | 경고 박스 | `Alert` variant="destructive" (amber로 커스텀) | icon: `TriangleAlert` |
| 섹션 래퍼 | A/B/C/D | `Card` + `CardHeader` + `CardTitle` + `CardContent` | |
| 자기소개 | 긴 텍스트 | `Textarea` + 문자 카운터 | rows=6, 50–500자 |
| 경력 연차 | 숫자 | `Input type="number"` | min=0 |
| 코스 선택 | 다중 체크 | `Checkbox` × N (grid-cols-2) | `/courses` API 응답 |
| 기술 스택 | 태그 | `Input` + 직접 구현한 TagList | |
| 멘티 레벨 | 단일 선택 | `RadioGroup` + `RadioGroupItem` | **`npx shadcn@latest add radio-group` 필요** |
| 소속/자격증 | 자유 입력 | `Input` / TagList | |
| 동의 | 필수 체크 | `Checkbox` + `Label` | |
| 푸터 | 제출/취소 | `Button` variant="outline" / "default" | |

### 폼 검증 (react-hook-form + zod)

```ts
z.object({
  bio: z.string().min(50).max(500),
  yearsOfExperience: z.number().int().min(0),
  courseKeys: z.array(z.string()).min(1),
  techStack: z.array(z.string()).min(1),
  preferredMenteeLevel: z.enum(["JUNIOR","MID","SENIOR","ANY"]),
  company: z.string().optional(),
  certifications: z.array(z.string()).optional(),
  agreed: z.literal(true),
})
```

---

## E2. `/mentor/status` — 신청 상태 조회

**접근 권한:** 로그인 + MENTOR 역할 전부 (상태별로 다른 카드 렌더)
**레이아웃:** 최대 너비 880px, 단일 Card 중앙 배치.

### 상태 1: PENDING (검토 중)

```
┌─────────────────────────────────────────────────────────┐
│                                                         │
│                  ⏳ (Hourglass 48px)                    │  icon: Hourglass (amber)
│                                                         │
│              [ 검토 중 ]  ← Badge (amber)               │  Badge variant="secondary"
│                                                         │
│          신청서가 관리자에게 전달되었습니다             │  h2
│                                                         │
│   일반적으로 1~3 영업일 내에 검토가 완료됩니다.         │  muted-foreground
│   결과는 이메일로도 안내드립니다.                       │
│                                                         │
│                  [ 홈으로 돌아가기 ]                    │  Button variant="outline"
└─────────────────────────────────────────────────────────┘
```

### 상태 2: REJECTED (반려)

```
┌─────────────────────────────────────────────────────────┐
│                                                         │
│                    ✕ (X 48px, red)                      │  icon: XCircle (destructive)
│                                                         │
│              [ 반려됨 ]  ← Badge (red)                  │  Badge variant="destructive"
│                                                         │
│            안타깝게도 신청이 반려되었습니다             │  h2
│                                                         │
│  ┌───────────────────────────────────────────────────┐  │  Alert variant="destructive"
│  │ 💬 반려 사유                                      │  │  (인용 박스 스타일)
│  │    "경력 증빙 자료가 부족합니다"                  │  │
│  └───────────────────────────────────────────────────┘  │
│                                                         │
│   내용을 보완한 뒤 다시 신청하실 수 있습니다.           │
│                                                         │
│           [ 홈 ]        [ 재신청하기 → ]                │  Button × 2
│                          (variant="default")            │
└─────────────────────────────────────────────────────────┘
```

### 상태 3: APPROVED (승인)

```
┌─────────────────────────────────────────────────────────┐
│                                                         │
│                  ✓ (Check 48px, green)                  │  icon: CheckCircle2 (emerald)
│                                                         │
│             [ 승인됨 ]  ← Badge (green)                 │  Badge className="bg-emerald-..."
│                                                         │
│              멘토 자격이 승인되었습니다! 🎉             │  h2
│                                                         │
│   이제 멘티의 매칭 요청을 수락하고 멘토링을             │
│   시작하실 수 있습니다.                                 │
│                                                         │
│        [ 홈 ]      [ 멘토 대시보드로 → ]                │  Button × 2
│                     (variant="default")                 │
└─────────────────────────────────────────────────────────┘
```

### shadcn 컴포넌트 매핑

| 상태 | UI 요소 | shadcn 컴포넌트 | 비고 |
|------|---------|----------------|------|
| 공통 | 카드 | `Card` + `CardContent` (p-12 text-center) | |
| 공통 | 상태 라벨 | `Badge` | variant 또는 className 커스텀 |
| PENDING | 아이콘 | `Hourglass` (lucide) | amber-500 |
| REJECTED | 아이콘 | `XCircle` (lucide) | destructive |
| REJECTED | 사유 박스 | `Alert` variant="destructive" | 💬 이모지 + 인용 |
| APPROVED | 아이콘 | `CheckCircle2` (lucide) | emerald-500 |
| 액션 버튼 | 이동 | `Button` variant="outline" / "default" | Link wrap |

### 라우팅

- PENDING: `/` + `/mentor/status` (자동 리다이렉트 없음)
- REJECTED: 재신청 버튼 → `/mentor/apply` (NEW와 동일 경로, 서버가 상태 판정)
- APPROVED: 멘토 대시보드 → `/mentor/dashboard` (기존 페이지가 있다면 연결, 없으면 `/` 임시)

---

## 공통 규칙

- 모든 텍스트 한국어
- Pretendard 폰트 (globals.css에 이미 로드됨)
- 로딩 중: `Skeleton` 또는 Spinner
- API 에러: `Alert variant="destructive"` + `toast`(있다면)
- 제출 성공: `/mentor/status` 로 router.push

---

## 확인 사항

1. 위 구조(E1 폼 4섹션, E2 상태 3케이스)로 구현 진행해도 될까요?
2. `radio-group` 컴포넌트 설치해도 될까요? (`npx shadcn@latest add radio-group`)
3. 기술 스택/자격증의 **TagList는 직접 구현** 예정입니다 (shadcn에는 없음). 괜찮나요?
