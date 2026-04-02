# Phase 5: 결제 & 커뮤니티 — Backend 구현 결과서

> 구현일: 2026-04-02 | 프로젝트: DevMatch

---

## 1. 구현 완료 요약

| 항목 | 수량 |
|------|------|
| Phase 5 신규 Java 파일 | 25개 |
| Phase 5 수정 Java 파일 | 1개 (GlobalExceptionHandler) |
| 신규 API 엔드포인트 | 12개 |
| 전체 Java 파일 (Phase 2+3+4+5) | 109개 |
| 전체 API 엔드포인트 (Phase 2+3+4+5) | 37개 |

`gradle compileJava` 빌드 성공 확인 완료.

---

## 2. 신규 파일 구조

```
backend/src/main/java/com/devmatch/
├── entity/
│   ├── PaymentStatus.java            ← Enum (PENDING/CONFIRMED/CANCELLED/FAILED)
│   ├── Payment.java                  ← 결제 Entity
│   ├── Post.java                     ← 게시글 Entity
│   ├── Comment.java                  ← 댓글 Entity
│   └── PostLike.java                 ← 좋아요 Entity (unique: post_id + user_id)
├── repository/
│   ├── PaymentRepository.java
│   ├── PostRepository.java
│   ├── CommentRepository.java
│   └── PostLikeRepository.java
├── dto/payment/
│   ├── PaymentCreateRequest.java
│   ├── PaymentConfirmRequest.java
│   ├── PaymentCancelRequest.java
│   └── PaymentResponse.java
├── dto/community/
│   ├── PostCreateRequest.java
│   ├── PostResponse.java             ← liked 필드 포함 (현재 사용자 좋아요 여부)
│   ├── CommentCreateRequest.java
│   └── CommentResponse.java
├── service/
│   ├── TossPaymentService.java       ← 토스페이먼츠 API 연동 (스텁)
│   ├── PaymentService.java           ← 결제 생성/승인/취소/조회
│   └── PostService.java              ← 게시글 CRUD + 좋아요 + 댓글
├── controller/
│   ├── PaymentController.java        ← /api/payments
│   └── PostController.java           ← /api/posts
└── exception/
    ├── PaymentNotFoundException.java
    ├── DuplicatePaymentException.java
    ├── PaymentFailedException.java
    ├── PostNotFoundException.java
    ├── CommentNotFoundException.java
    └── UnauthorizedPostException.java
```

---

## 3. API 엔드포인트 (Phase 5 신규 12개)

### PaymentController — `/api/payments`

| HTTP | 경로 | 인증 | 설명 |
|------|------|------|------|
| POST | / | O | 결제 생성 (orderId 자동 생성) |
| POST | /confirm | O | 토스페이먼츠 결제 승인 |
| POST | /{paymentId}/cancel | O | 결제 취소 |
| GET | / | O | 내 결제 목록 조회 |
| GET | /{paymentId} | O | 결제 상세 조회 |

### PostController — `/api/posts`

| HTTP | 경로 | 인증 | 설명 |
|------|------|------|------|
| POST | / | O | 게시글 작성 |
| GET | / | O | 게시글 목록 (페이지네이션) |
| GET | /{postId} | O | 게시글 상세 |
| PUT | /{postId} | O | 게시글 수정 (작성자만) |
| DELETE | /{postId} | O | 게시글 삭제 (작성자만) |
| POST | /{postId}/like | O | 좋아요 토글 |
| POST | /{postId}/comments | O | 댓글 작성 |
| GET | /{postId}/comments | O | 댓글 목록 |
| DELETE | /{postId}/comments/{commentId} | O | 댓글 삭제 (작성자만) |

---

## 4. 핵심 비즈니스 로직

### 결제 흐름 (토스페이먼츠 연동)

```
1. POST /api/payments  { matchingId, amount }
   → Matching ACCEPTED 상태 확인
   → 멘티 본인 확인
   → 중복 결제 확인
   → orderId 자동 생성 (DEVMATCH-XXXXXXXX)
   → Payment 생성 (status: PENDING)
   ← PaymentResponse (orderId 포함)

2. [프론트엔드에서 토스 결제 위젯 호출]

3. POST /api/payments/confirm  { paymentKey, orderId, amount }
   → orderId로 Payment 조회
   → 결제 소유자 확인
   → 금액 일치 검증
   → TossPaymentService.confirmPayment() 호출
   → Payment 상태 → CONFIRMED + paymentKey 저장
   ← PaymentResponse

4. POST /api/payments/{id}/cancel  { cancelReason }
   → 결제 소유자 확인
   → TossPaymentService.cancelPayment() 호출
   → Payment 상태 → CANCELLED + cancelReason 저장
   ← PaymentResponse
```

### 토스페이먼츠 연동 설계

- `TossPaymentService`는 현재 **스텁(stub) 구현**
- `confirmPayment()` / `cancelPayment()` 모두 로그만 기록하고 `true` 반환
- 실제 연동 시 RestTemplate/WebClient로 토스 API 호출 코드로 교체
- 결제 승인 실패 시 Payment 상태를 FAILED로 변경

### 커뮤니티 기능

```
게시글 CRUD
  → 작성: 제목(200자 이하) + 내용(필수)
  → 수정/삭제: 작성자 본인만 가능
  → 목록: 페이지네이션 (기본 10건, 최신순)

좋아요 토글
  → POST /api/posts/{postId}/like
  → 이미 좋아요 → 취소 (PostLike 삭제 + likeCount--)
  → 미좋아요 → 추가 (PostLike 생성 + likeCount++)
  → unique constraint (post_id, user_id)로 중복 방지

댓글
  → 작성: 1000자 이하 + commentCount++ 자동 증가
  → 삭제: 작성자 본인만 가능
  → 목록: 작성일 오름차순 (오래된 순)
```

---

## 5. 예외 처리 (Phase 5 추가)

| 예외 | HTTP | 상황 |
|------|------|------|
| `PaymentNotFoundException` | 404 | 결제 정보 미존재 |
| `DuplicatePaymentException` | 409 | 동일 매칭 결제 중복 |
| `PaymentFailedException` | 400 | 결제 승인/취소 실패, 금액 불일치, 권한 없음 |
| `PostNotFoundException` | 404 | 게시글 미존재 |
| `CommentNotFoundException` | 404 | 댓글 미존재 |
| `UnauthorizedPostException` | 403 | 본인 게시글/댓글이 아닌 경우 |

---

## 6. DB 스키마

```sql
CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    matching_id BIGINT NOT NULL UNIQUE,
    order_id VARCHAR(100) NOT NULL UNIQUE,
    payment_key VARCHAR(200) UNIQUE,
    amount INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    cancel_reason VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);

CREATE TABLE posts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    like_count INT NOT NULL DEFAULT 0,
    comment_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);

CREATE TABLE comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL
);

CREATE TABLE post_likes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    UNIQUE KEY uk_post_user (post_id, user_id)
);
```

---

## 7. 전체 API 엔드포인트 현황 (Phase 2+3+4+5 = 37개)

| Phase | Controller | 엔드포인트 수 |
|-------|-----------|--------------|
| 2 | AuthController | 4 |
| 2 | UserController | 2 |
| 2 | MentorController | 2 |
| 3 | TestController | 4 |
| 3 | MatchingController | 5 |
| 4 | SessionController | 4 |
| 4 | AvailabilityController | 4 |
| 5 | PaymentController | 5 |
| 5 | PostController | 7 |
| **합계** | | **37** |

---

## 8. 향후 작업

- 토스페이먼츠 실제 API 연동 (`TossPaymentService` 스텁 교체)
- Google Calendar 실제 연동 (`GoogleCalendarService` 스텁 교체)
- Phase 6: Admin API + 배포
