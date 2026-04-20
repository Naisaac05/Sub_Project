# 멘토 프로필 반려 사유(`rejectedReason`) 백엔드 누락

- 발생일: 2026-04-20
- 영역: backend (+ frontend 계약 불일치)
- 심각도: medium

## 증상

Phase G 시나리오 3 (REJECTED → 재신청) 테스트 중:

1. DB 에서 `mentor_profiles.status='REJECTED'` 로 강제 전환 후 `/mentor/status` 접속 → 빨간 REJECTED 카드는 정상 노출.
2. 카드에서 "재신청하기" 버튼 → `/mentor/apply` 로 이동. 이전 값 프리필은 정상 (`bio`, `careerYears`, `courseKeys`, `techStacks`, `preferredMenteeLevel`, `company`, `jobTitle`, `education`).
3. 그러나 페이지 상단에 있어야 하는 **"이전 신청 반려됨 — 사유: …"** 빨간 배너가 표시되지 않음.

프론트 조건식:
```tsx
// frontend/src/app/mentor/apply/page.tsx:336
{rejectedReason && (
  <div className="rounded-lg border border-red-200 bg-red-50 ...">
    이전 신청 반려됨 — 사유: {rejectedReason}
  </div>
)}
```

`rejectedReason` 이 `null` 이라 조건이 거짓 → 배너가 렌더되지 않는다.

## 원인

백엔드 `MentorProfile` 엔티티에 **반려 사유 컬럼 자체가 없음**.

- `backend/src/main/java/com/devmatch/entity/MentorProfile.java` — 필드에 `status` (enum `MentorStatus`) 는 있지만 `rejectedReason` (String) 에 해당하는 컬럼/게터가 없다.
- `markRejected()` 메서드 역시 사유를 파라미터로 받지 않는다:
  ```java
  public void markRejected() {
      this.status = MentorStatus.REJECTED;
  }
  ```
- 결과적으로 `/api/mentor/me` 응답 DTO 에 `rejectedReason` 필드가 있어도 **항상 `null`** 이 직렬화된다.

추가로 **관리자 승인/반려 엔드포인트 자체가 구현되어 있지 않다**:
- `backend/src/main/java/com/devmatch/controller/MentorController.java` — `/apply` (POST), `/me` (GET) 두 개만 존재.
- `/api/mentor/{id}/approve`, `/api/mentor/{id}/reject` 가 없어서, 운영자가 사유와 함께 반려를 수행할 진입점 자체가 없다.
- 테스트 중에는 DB 에서 직접 `UPDATE mentor_profiles SET status='REJECTED' WHERE id=6;` 로 강제 전환했기 때문에 사유가 들어갈 방법이 없었다.

즉 프론트는 `rejectedReason` 이 있을 것을 **기대**하지만, 백엔드는 **그 데이터를 저장할 자리도, 채울 진입점도 없음**. 계약이 프론트에만 존재한다.

## 해결 방법

지금 턴에서는 **갭만 기록하고 수정하지 않았다**. Phase G E2E 검증 범위를 벗어나는 백엔드 변경이기 때문. 추후 진행 시 아래 순서로 처리한다.

### 1. 엔티티·DB 마이그레이션

```java
// backend/src/main/java/com/devmatch/entity/MentorProfile.java
@Column(name = "rejected_reason", length = 500)
private String rejectedReason;

public void markRejected(String reason) {
    this.status = MentorStatus.REJECTED;
    this.rejectedReason = reason;
}

public void markApproved() {
    this.status = MentorStatus.APPROVED;
    this.rejectedReason = null; // 재신청 승인 시 사유 초기화
}
```

`spring.jpa.hibernate.ddl-auto=update` 환경이면 컬럼은 자동 추가되지만, 운영 DB 는 반드시 마이그레이션 스크립트로 추가해야 한다.

### 2. DTO

`MentorProfileResponse` 에 `rejectedReason` 필드를 추가하고 `MentorProfile.getRejectedReason()` 을 매핑.

### 3. 관리자 엔드포인트

`MentorController` (또는 별도 `AdminMentorController`) 에 아래 두 개 추가 — `ROLE_ADMIN` 만 접근.

```java
@PostMapping("/{profileId}/approve")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<Void> approve(@PathVariable Long profileId) { ... }

@PostMapping("/{profileId}/reject")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<Void> reject(@PathVariable Long profileId, @RequestBody RejectRequest body) {
    // body.reason() 을 markRejected 에 전달
}
```

### 4. 재신청 시 초기화

`MentorController.apply()` 에서 기존 REJECTED 프로필을 업데이트할 때 `rejectedReason = null` 로 덮어쓰기 (재신청하면 이전 사유가 남지 않도록).

## 재발 방지 / 메모

- **프론트 타입이 먼저 만들어지고 백엔드가 따라오지 못한 케이스**. `MentorProfileResponse` (프론트 타입) 와 백엔드 DTO 를 한 곳에 모아두거나, OpenAPI 스펙 → 타입 자동 생성으로 계약을 한 소스에서 관리하는 편이 안전.
- 지금 턴의 Phase G 테스트에서는 DB 를 직접 조작해서 REJECTED 상태를 만들었기 때문에 갭이 드러났다. 실제로 운영 시나리오에 들어가기 전에 반드시 `/approve`·`/reject` 엔드포인트가 있어야 한다.
- 프론트에서는 지금은 `rejectedReason` 이 없으면 단순히 배너를 숨길 뿐이라 깨지지는 않지만, 사용자는 "왜 반려됐는지" 를 알 방법이 없다. 이 상태로 배포하면 UX 가 깨지므로 반드시 백엔드가 선행돼야 한다.
- 관련 프론트 참조:
  - `frontend/src/app/mentor/apply/page.tsx:336` — 배너 조건부 렌더
  - `frontend/src/lib/mentor.ts` — `MentorProfileResponse` 타입 정의 (`rejectedReason?: string | null`)
