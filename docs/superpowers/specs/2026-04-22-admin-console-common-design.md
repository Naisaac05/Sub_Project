# 관리자 콘솔 Phase II — Common 설계 (Design Spec)

> 작성일: 2026-04-22
> 대상 Phase: II
> 관련 문서: [admin-mentor.md](../../mockups/admin-mentor.md) (Phase I), [admin-console-overview.md](../../mockups/admin-console-overview.md), [admin-users.md](../../mockups/admin-users.md), [admin-payments.md](../../mockups/admin-payments.md), [admin-posts.md](../../mockups/admin-posts.md)

---

## 1. 배경

Phase I에서 관리자 콘솔 기반(사이드바·403 페이지·멘토 심사 플로우)은 완성됐다. Phase II는 다음 세 기능을 추가한다:

1. 회원 관리 (멘티/멘토/관리자 조회 + 역할 변경)
2. 결제 관리 (결제 조회 + 관리자 강제 환불)
3. 게시물 관리 (커뮤니티 게시물 조회 + 강제 삭제)

이 문서는 **세 기능이 공유하는 기반 요소(Common)** 만 다룬다. 각 기능별 상세 설계는 별도 스펙으로 분리한다(후속).

## 2. 스코프

### In scope

- `AdminAuditLog` 엔티티 · `AdminActionType` enum · `AdminAuditLogService` 설계
- Phase I 멘토 승인/반려 플로우에 감사 로그 기록 **추가** (기존 `MentorProfileHistory` 유지 + `AdminAuditLog` 중복 기록)
- `app/admin` 프런트 사이드바를 1개 메뉴 → 4개 메뉴로 확장
- Phase II 각 feature 스펙이 따를 **감사 로그 기록 호출 규약**

### Out of scope (명시적 제외)

| 항목 | 이유 |
|------|------|
| 이메일·인앱 알림 발송 | 현재 Spring Mail 인프라 없음. Phase III 에서 통합 설계 |
| 공용 프런트 유틸(`<Pagination>`, `<DateRangePicker>`, `<DebouncedSearchInput>`) | 첫 feature(회원 관리)에서 인라인 구현 후 두 번째 feature 시점에 추출 (lazy) |
| `<AdminPageHeader>` 같은 페이지 래퍼 컴포넌트 | 구조가 3~4줄 JSX 수준이라 재사용 추상화가 과함 |
| AdminAuditLog 조회 UI | Phase III 대시보드 범위 |

## 3. 결정 사항 요약

| 항목 | 결정 | 대안 및 탈락 이유 |
|------|------|------------------|
| 감사 로그 테이블 구조 | **단일 `AdminAuditLog` 테이블** | 엔티티별 히스토리 테이블은 Phase III 대시보드의 "관리자 활동 통합 뷰" 구현을 복잡하게 함 |
| Phase I 멘토 승인/반려 처리 | **이중 기록** (기존 `MentorProfileHistory` 유지 + `AdminAuditLog` 에도 기록) | 전면 통합은 Phase I UI(`/admin/mentor/[id]` 이전 반려 사유) 재작성 필요 → 회귀 리스크 |
| 감사 로그 기록 메커니즘 | **서비스 레이어 직접 호출** (`@Transactional` 내부) | AOP/Event 기반은 학생 프로젝트 규모에 오버엔지니어링 |
| 이메일/알림 | **Phase II 범위 제외, Phase III 이관** | Spring Mail 의존성 추가 + 템플릿 작업이 스펙 범위를 크게 벌림 |
| `metadata` 컬럼 저장 형식 | **`TEXT` 컬럼에 JSON 문자열 직렬화** | MySQL `JSON` 타입은 H2(테스트) 환경과 비대칭, Phase II 용도(단순 표시)에 오버스펙 |
| 공용 FE 컴포넌트 | **lazy 추출** (첫 feature에서 인라인 → 두 번째에서 추출) | Phase I의 "페이지네이션 보류" 철학과 일관 |
| 사이드바 메뉴 정의 | **설정 배열 기반** | 하드코딩 분기는 Phase III 추가 시 재수정 필요 |

## 4. 백엔드 설계

### 4.1 엔티티: `AdminAuditLog`

```java
package com.devmatch.entity;

@Entity
@Table(name = "admin_audit_log", indexes = {
    @Index(name = "idx_audit_admin_createdAt", columnList = "admin_id, createdAt"),
    @Index(name = "idx_audit_target", columnList = "target_type, target_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor @Builder
public class AdminAuditLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;          // 행위 주체 관리자 User.id

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    private AdminActionType actionType;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;     // "USER" / "PAYMENT" / "POST" / "COMMENT" / "MENTOR_PROFILE"

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(length = 500)
    private String reason;         // nullable (역할 변경엔 사유 생략 허용)

    @Column(columnDefinition = "TEXT")
    private String metadata;       // JSON 직렬화 문자열, nullable

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

**설계 근거**
- `adminId` 는 `User.id` 참조이지만 `@ManyToOne` 으로 묶지 않고 단순 Long — 감사 로그는 조회 시 username 만 필요하며, `User` 삭제 제약을 걸지 않기 위해 의도적으로 외래키 제약 생략
- `targetType` 을 enum 이 아닌 String 으로 둠 — 미래 확장 시 enum 변경·마이그레이션 부담 제거
- `metadata`는 `columnDefinition = "TEXT"` — MySQL/H2 공통 지원, 길이 무제한

### 4.2 Enum: `AdminActionType`

```java
package com.devmatch.entity;

public enum AdminActionType {
    // Phase II 사용
    USER_ROLE_CHANGE,
    PAYMENT_REFUND,
    POST_DELETE,
    COMMENT_DELETE,

    // Phase I 소급 적용 (결정 사항 §3)
    MENTOR_APPROVE,
    MENTOR_REJECT
}
```

### 4.3 서비스: `AdminAuditLogService`

```java
package com.devmatch.service;

@Service
@RequiredArgsConstructor
public class AdminAuditLogService {

    private final AdminAuditLogRepository repository;
    private final ObjectMapper objectMapper;   // Spring Boot 기본 bean

    /**
     * 관리자 행위 감사 로그를 기록한다.
     * 호출 컨텍스트는 반드시 @Transactional 안에서여야 한다
     * (도메인 변경이 롤백되면 감사 로그도 함께 롤백되어야 하기 때문).
     *
     * @param adminId    행위 주체 관리자 User.id
     * @param actionType 행위 유형
     * @param targetType 대상 엔티티 타입 식별자 ("USER"/"PAYMENT"/...)
     * @param targetId   대상 엔티티 PK
     * @param reason     사유 (nullable, 500자 이내)
     * @param metadata   추가 정보 (nullable, Map → JSON 문자열 직렬화)
     */
    public void record(Long adminId, AdminActionType actionType,
                       String targetType, Long targetId,
                       String reason, Map<String, Object> metadata) {
        String metadataJson = serialize(metadata);
        repository.save(AdminAuditLog.builder()
                .adminId(adminId)
                .actionType(actionType)
                .targetType(targetType)
                .targetId(targetId)
                .reason(reason)
                .metadata(metadataJson)
                .build());
    }

    private String serialize(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("감사 로그 metadata 직렬화 실패", e);
        }
    }
}
```

**설계 근거**
- 메서드 한 개만 공개 — 호출자 코드가 단순해짐
- 직렬화 실패는 `IllegalStateException` 으로 변환 (실제로 런타임 도달 불가한 경우)
- Repository 는 `JpaRepository<AdminAuditLog, Long>` 표준 상속만 (Phase II에선 조회 메서드 불필요)

### 4.4 호출 규약 (Phase II feature 스펙이 따라야 할 패턴)

**규칙**
1. 호출은 각 관리자 서비스 메서드의 `@Transactional` **내부**에서만 — 도메인 변경과 같은 트랜잭션으로 묶일 것
2. 호출 시점은 도메인 변경 **직후**, return 직전
3. `metadata` Map 의 값 타입은 **`String`, `Number`, `Boolean`, `Enum.name()` 4종으로만 제한** — 객체/리스트 전달 시 JSON 직렬화 예외로 트랜잭션 전체가 롤백될 수 있음
4. `targetType` 문자열 값은 이 스펙 §4.1 의 예시 값(`"USER"`, `"PAYMENT"`, `"POST"`, `"COMMENT"`, `"MENTOR_PROFILE"`) 중 하나로만 — 새 값이 필요하면 이 스펙을 업데이트한 후 사용

**예시**:

```java
// 예시: AdminUserService.changeRole()
@Transactional
public UserResponse changeRole(Long adminId, Long userId, Role newRole) {
    User user = userRepository.findById(userId).orElseThrow(...);
    Role oldRole = user.getRole();

    user.updateRole(newRole);   // 도메인 변경

    auditLogService.record(         // 감사 로그 (같은 트랜잭션)
        adminId,
        AdminActionType.USER_ROLE_CHANGE,
        "USER",
        user.getId(),
        null,
        Map.of("from", oldRole.name(), "to", newRole.name())
    );

    return UserResponse.from(user);
}
```

### 4.5 Phase I 소급 적용

`AdminMentorService` 의 기존 승인/반려 메서드에 호출을 **추가**한다 (기존 `MentorProfileHistory` 기록은 유지):

```java
// AdminMentorService.approve()
@Transactional
public void approve(Long adminId, Long profileId) {
    MentorProfile profile = ...;
    profile.approve();
    mentorProfileHistoryRepository.save(...);   // 기존 유지

    auditLogService.record(                     // Phase II에서 추가
        adminId, MENTOR_APPROVE, "MENTOR_PROFILE", profileId,
        null, null
    );
}

// AdminMentorService.reject()
@Transactional
public void reject(Long adminId, Long profileId, String reason) {
    ...
    mentorProfileHistoryRepository.save(...);   // 기존 유지

    auditLogService.record(                     // Phase II에서 추가
        adminId, MENTOR_REJECT, "MENTOR_PROFILE", profileId,
        reason, null
    );
}
```

**회귀 영향**
- 기존 `/admin/mentor` UI/API 는 변경 없음
- 기존 `MentorProfileHistory` 조회(이전 반려 사유 표시) 는 그대로 동작
- 테스트: 기존 승인/반려 테스트에 `AdminAuditLog` row 생성 assert 만 추가

### 4.6 보안

추가 작업 불필요. `SecurityConfig.java:49` 의 `/api/admin/**` → `hasRole("ADMIN")` 룰이 Phase II 신규 엔드포인트(`/api/admin/users`, `/api/admin/payments`, `/api/admin/posts`) 전부를 자동 가드한다.

각 컨트롤러는 `@AuthenticationPrincipal CustomUserDetails` 로 `adminId` 를 획득한다 (이미 Phase I 에서 사용 중인 패턴).

### 4.7 마이그레이션

- Dev(`ddl-auto: update`): Hibernate 가 `admin_audit_log` 테이블 자동 생성
- Prod(`ddl-auto: validate`): 배포 전 수동 DDL 필요. 다음 문을 배포 체크리스트에 추가:

```sql
CREATE TABLE admin_audit_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  admin_id BIGINT NOT NULL,
  action_type VARCHAR(30) NOT NULL,
  target_type VARCHAR(20) NOT NULL,
  target_id BIGINT NOT NULL,
  reason VARCHAR(500),
  metadata TEXT,
  created_at DATETIME(6) NOT NULL,
  INDEX idx_audit_admin_createdAt (admin_id, created_at),
  INDEX idx_audit_target (target_type, target_id)
);
```

## 5. 프런트 설계

### 5.1 사이드바 확장

기존 `components/admin/AdminSidebar.tsx` (Phase I 에서 멘토 심사 1개 메뉴만 하드코딩됨) 를 **설정 배열 기반**으로 리팩토링한다.

```tsx
// components/admin/AdminSidebar.tsx
import { UserCheck, Users, CreditCard, FileText } from "lucide-react";

type AdminMenuItem = {
  href: string;
  label: string;
  icon: LucideIcon;
};

const MENU: AdminMenuItem[] = [
  { href: "/admin/mentor",   label: "멘토 심사",   icon: UserCheck },
  { href: "/admin/users",    label: "회원 관리",   icon: Users },
  { href: "/admin/payments", label: "결제 관리",   icon: CreditCard },
  { href: "/admin/posts",    label: "게시물 관리", icon: FileText },
];

export function AdminSidebar() {
  const pathname = usePathname();
  return (
    <aside className="w-[220px] border-r ...">
      <nav>
        {MENU.map(item => {
          const active = pathname.startsWith(item.href);
          return (
            <Link key={item.href} href={item.href}
                  className={cn("...", active && "...")}>
              <item.icon className="size-4" />
              {item.label}
            </Link>
          );
        })}
      </nav>
    </aside>
  );
}
```

**활성화 판정**: `pathname.startsWith(item.href)` — `/admin/users/42` 같은 상세 경로에서도 "회원 관리" 메뉴가 강조되도록.

**Phase III 확장**: 배열에 항목 추가만 하면 됨(대시보드·FAQ 등).

### 5.2 `/admin` 루트 리다이렉트

`app/admin/page.tsx` 는 현재 `/admin/mentor` 로 리다이렉트 중. Phase II에서도 **변경 없음** (첫 메뉴 유지). Phase III 대시보드 도입 시 `/admin/dashboard` 로 전환 고려.

### 5.3 공용 컴포넌트 계획 (현 스펙 범위 밖)

첫 feature 스펙(회원 관리) 구현 중에 다음을 각 페이지 내부에 인라인 작성:
- 서버 페이지네이션 UI
- 디바운스 검색 input

두 번째 feature(결제 관리 또는 게시물 관리) 스펙 작성 시점에 필요성 확인 후 `components/admin/common/` 으로 추출 PR 분리. 이 스펙에서 사전 설계하지 않는다(Q5 결정).

## 6. 테스트 계획

### 6.1 `AdminAuditLogServiceTest` (신규)

- `record()` 호출 시 DB에 row 가 저장되는가
- `metadata` Map 이 JSON 문자열로 정확히 직렬화되는가
- `metadata` null/빈 Map 입력 시 컬럼이 null 로 저장되는가
- `reason` nullable 허용되는가

### 6.2 `AdminMentorServiceTest` (보강)

기존 테스트에 assert 추가:
- `approve()` 호출 후 `MENTOR_APPROVE` 타입의 감사 로그가 1건 생성됨
- `reject()` 호출 후 `MENTOR_REJECT` 타입의 감사 로그가 1건 생성되고 `reason` 필드가 일치함

### 6.3 통합 테스트 원칙 (Phase II 전체에 적용)

- 각 관리자 서비스 테스트는 **실제 `AdminAuditLogService` 를 주입**하고 DB row 를 검증 (모킹하지 않음)
- 도메인 변경·감사 로그 생성이 같은 트랜잭션인지 검증: 도메인 예외 발생 시 감사 로그도 저장되지 않아야 함

## 7. 리스크 및 완화

| 리스크 | 영향 | 완화 |
|--------|------|------|
| Phase I 소급 적용 시 기존 멘토 승인/반려 테스트 실패 | 회귀 | 테스트 보강을 서비스 변경과 **같은 커밋**에서 수행 |
| `metadata` JSON 직렬화 실패 | 감사 로그 저장 실패 → 도메인 변경까지 롤백 | §4.4 호출 규약 #3 로 값 타입을 String/Number/Boolean/Enum.name() 로 제한 |
| `AdminAuditLog` 테이블 급격한 증가 | 장기적 DB 부하 | Phase II 범위 밖. Phase III 대시보드에서 `created_at` 기준 파티셔닝 또는 월별 아카이브 전략 검토 |
| 사이드바 리팩토링으로 Phase I 회귀 | `/admin/mentor` 활성화 표시 깨짐 | E2E 또는 수동 스모크 테스트로 "멘토 심사" 활성화 확인 |

## 8. 구현 순서 (이 스펙만)

1. 백엔드
   1. `AdminActionType` enum 추가
   2. `AdminAuditLog` 엔티티 + `AdminAuditLogRepository` 추가
   3. `AdminAuditLogService` 구현 + 단위 테스트
   4. `AdminMentorService` 에 감사 로그 호출 추가 + 기존 테스트 보강
2. 프런트
   5. `AdminSidebar.tsx` 설정 배열 기반 리팩토링 (기존 활성화 동작 회귀 없도록 수동 확인)
3. 문서
   6. `docs/mockups/admin-console-overview.md` 에 "Common 스펙 확정됨" 섹션 추가 링크
4. PR 머지 후 → 회원 관리(feature 1) 스펙 브레인스토밍 시작

## 9. 후속 스펙

| 파일명 | 내용 |
|--------|------|
| `docs/superpowers/specs/YYYY-MM-DD-admin-users-design.md` | 회원 관리 상세 설계 |
| `docs/superpowers/specs/YYYY-MM-DD-admin-payments-design.md` | 결제 관리 상세 설계 |
| `docs/superpowers/specs/YYYY-MM-DD-admin-posts-design.md` | 게시물 관리 상세 설계 |

각 후속 스펙은 이 Common 스펙의 **§4.4 호출 규약**과 **§5.1 사이드바 배열**에 자동으로 의존한다.

---

## 변경 이력

| 일자 | 내용 |
|------|------|
| 2026-04-22 | 최초 작성 (브레인스토밍 Q1~Q5 + 확인 1~3 결정 반영) |
