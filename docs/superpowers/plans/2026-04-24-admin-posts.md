# Admin Posts Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Phase II Feature 3 — 관리자가 커뮤니티 게시물/댓글을 조회하고 사유와 함께 강제로 소프트 삭제할 수 있게 한다 (감사 로그 연동, 사용자측 조회 회귀 방지 포함).

**Architecture:** `Post`/`Comment` 엔티티에 soft delete 4컬럼(`deleted/deletionReason/deletedBy/deletedAt`) + `softDelete(reason, adminId)` 도메인 메서드 추가. 관리자측은 `AdminPostController` → `AdminPostService` → (JPA Specification 기반 동적 쿼리) 경로로 조회/삭제하고 `AdminAuditLogService.record()` 로 감사 로그를 동일 트랜잭션에 기록. 사용자측은 Repository 시그니처를 `...Deleted[False]...` 형태로 리네임하고 `PostService` 개별 조회에 `isDeleted()` 가드를 추가해 삭제된 글이 사용자 눈에 노출되지 않도록 막는다. 프런트는 `/admin/posts` 목록 + `/admin/posts/[id]` 상세 두 페이지이며 shadcn 공용 컴포넌트(`DebouncedSearchInput`, `AdminDateRangePicker`, `Pagination`, `Dialog`+`Textarea`+`Alert`)를 재사용한다. **프런트 구현 착수 전 Pencil 목업 승인 게이트**가 강제된다 (memory `feedback_frontend_preview.md`).

**Tech Stack:** Spring Boot 3 (Java 17), JPA + Hibernate + `JpaSpecificationExecutor`, Spring Security, Bean Validation, JUnit 5 + Mockito + `@WebMvcTest` / Next.js 14 App Router, TypeScript, axios, shadcn/ui, sonner toast, react-hook-form + zod.

**Spec:** `docs/superpowers/specs/2026-04-24-admin-posts-design.md`

---

## File Structure

### 신규 (백엔드)
- `backend/src/main/java/com/devmatch/dto/admin/post/AdminPostListItemResponse.java` — 목록 한 행 record
- `backend/src/main/java/com/devmatch/dto/admin/post/AdminPostDetailResponse.java` — 상세 record
- `backend/src/main/java/com/devmatch/dto/admin/post/AdminPostCommentResponse.java` — 상세 내부 댓글 record
- `backend/src/main/java/com/devmatch/dto/admin/post/AdminPostFilter.java` — 서비스 내부 필터 record
- `backend/src/main/java/com/devmatch/dto/admin/post/AdminPostDeleteRequest.java` — 삭제 body record (게시물/댓글 공용)
- `backend/src/main/java/com/devmatch/repository/spec/PostSpecifications.java` — 관리자 목록 동적 쿼리 빌더
- `backend/src/main/java/com/devmatch/service/AdminPostService.java` — 비즈니스 로직
- `backend/src/main/java/com/devmatch/controller/AdminPostController.java` — 4개 엔드포인트
- `backend/src/test/java/com/devmatch/entity/PostSoftDeleteTest.java` — Post.softDelete 단위 테스트
- `backend/src/test/java/com/devmatch/entity/CommentSoftDeleteTest.java` — Comment.softDelete 단위 테스트
- `backend/src/test/java/com/devmatch/service/AdminPostServiceTest.java` — 서비스 단위 테스트
- `backend/src/test/java/com/devmatch/controller/AdminPostControllerTest.java` — 컨트롤러 슬라이스

### 수정 (백엔드)
- `backend/src/main/java/com/devmatch/entity/Post.java` — 4컬럼 + `softDelete(..)` + `isDeleted()` getter
- `backend/src/main/java/com/devmatch/entity/Comment.java` — 4컬럼 + `softDelete(..)` + `isDeleted()` getter
- `backend/src/main/java/com/devmatch/repository/PostRepository.java` — 사용자측 쿼리 `deleted=false` 반영 + `JpaSpecificationExecutor<Post>` 상속
- `backend/src/main/java/com/devmatch/repository/CommentRepository.java` — 사용자측 쿼리 리네임
- `backend/src/main/java/com/devmatch/service/PostService.java` — 조회/업데이트/삭제 경로에 `isDeleted()` 가드 + 리네임된 메서드 호출
- `backend/src/test/java/com/devmatch/controller/PostControllerTest.java` (있으면) — 회귀 테스트 추가. 없으면 새로 작성

### 신규 (프런트)
- `docs/mockups/admin-posts.pen` — Pencil 목업 (승인 게이트)
- `frontend/src/lib/admin/posts.ts` — API 클라이언트
- `frontend/src/app/admin/posts/page.tsx` — 목록
- `frontend/src/app/admin/posts/[id]/page.tsx` — 상세
- `frontend/src/app/admin/posts/_components/PostDeleteDialog.tsx` — 게시물 삭제 모달
- `frontend/src/app/admin/posts/_components/CommentDeleteDialog.tsx` — 댓글 삭제 모달

### 신규 (문서)
- `docs/smoke/2026-04-24-admin-posts-smoke.md` — 수동 스모크 가이드
- `ROADMAP.md` 배포 체크리스트 블록 갱신

---

## Task 1: Post 엔티티 soft delete 필드 + 도메인 메서드

**Files:**
- Test: `backend/src/test/java/com/devmatch/entity/PostSoftDeleteTest.java`
- Modify: `backend/src/main/java/com/devmatch/entity/Post.java`

- [ ] **Step 1: Write failing test for `softDelete` happy path + re-delete guard**

Create `backend/src/test/java/com/devmatch/entity/PostSoftDeleteTest.java`:

```java
package com.devmatch.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostSoftDeleteTest {

    private Post newPost() {
        return Post.builder()
                .title("t")
                .content("c")
                .category("질문")
                .likeCount(0).commentCount(0).viewCount(0)
                .build();
    }

    @Test
    void softDelete_최초_호출은_4필드를_세팅() {
        Post post = newPost();

        post.softDelete("스팸 광고성 게시물이므로 삭제합니다", 99L);

        assertThat(post.isDeleted()).isTrue();
        assertThat(post.getDeletionReason()).isEqualTo("스팸 광고성 게시물이므로 삭제합니다");
        assertThat(post.getDeletedBy()).isEqualTo(99L);
        assertThat(post.getDeletedAt()).isNotNull();
    }

    @Test
    void softDelete_이미_삭제된_게시물_재호출은_IllegalStateException() {
        Post post = newPost();
        post.softDelete("사유1사유1사유1사유1", 1L);

        assertThatThrownBy(() -> post.softDelete("사유2사유2사유2사유2", 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 삭제된 게시물");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests com.devmatch.entity.PostSoftDeleteTest`
Expected: FAIL — `softDelete`/`isDeleted`/getters not defined.

- [ ] **Step 3: Add fields + methods to `Post.java`**

Edit `backend/src/main/java/com/devmatch/entity/Post.java` — add 4 fields directly below `private LocalDateTime updatedAt;` and a `softDelete` method alongside the other domain methods. The entity already has `@Getter` at class level so column getters are auto-generated; `isDeleted()` is manual only to match the spec's naming convention.

```java
    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @Column(name = "deletion_reason", length = 500)
    private String deletionReason;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public boolean isDeleted() {
        return Boolean.TRUE.equals(this.deleted);
    }

    public void softDelete(String reason, Long adminId) {
        if (isDeleted()) {
            throw new IllegalStateException("이미 삭제된 게시물입니다");
        }
        this.deleted = true;
        this.deletionReason = reason;
        this.deletedBy = adminId;
        this.deletedAt = LocalDateTime.now();
    }
```

Also add `@Index(name = "idx_posts_deleted_created", columnList = "deleted, created_at")` to the `@Table` annotation. The existing annotation is `@Table(name = "posts")` — change to:

```java
@Table(name = "posts", indexes = {
        @Index(name = "idx_posts_deleted_created", columnList = "deleted, created_at")
})
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests com.devmatch.entity.PostSoftDeleteTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devmatch/entity/Post.java backend/src/test/java/com/devmatch/entity/PostSoftDeleteTest.java
git commit -m "feat(admin-post): Post soft delete 4컬럼 + softDelete 도메인 메서드"
```

---

## Task 2: Comment 엔티티 soft delete 필드 + 도메인 메서드

**Files:**
- Test: `backend/src/test/java/com/devmatch/entity/CommentSoftDeleteTest.java`
- Modify: `backend/src/main/java/com/devmatch/entity/Comment.java`

- [ ] **Step 1: Write failing test**

Create `backend/src/test/java/com/devmatch/entity/CommentSoftDeleteTest.java`:

```java
package com.devmatch.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommentSoftDeleteTest {

    private Comment newComment() {
        return Comment.builder()
                .content("c")
                .build();
    }

    @Test
    void softDelete_최초_호출은_4필드를_세팅() {
        Comment c = newComment();

        c.softDelete("욕설이 포함된 댓글이라 삭제합니다", 99L);

        assertThat(c.isDeleted()).isTrue();
        assertThat(c.getDeletionReason()).isEqualTo("욕설이 포함된 댓글이라 삭제합니다");
        assertThat(c.getDeletedBy()).isEqualTo(99L);
        assertThat(c.getDeletedAt()).isNotNull();
    }

    @Test
    void softDelete_이미_삭제된_댓글_재호출은_IllegalStateException() {
        Comment c = newComment();
        c.softDelete("사유1사유1사유1사유1", 1L);

        assertThatThrownBy(() -> c.softDelete("사유2사유2사유2사유2", 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 삭제된 댓글");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests com.devmatch.entity.CommentSoftDeleteTest`
Expected: FAIL.

- [ ] **Step 3: Add fields + methods to `Comment.java`**

Edit `backend/src/main/java/com/devmatch/entity/Comment.java` — add the same 4 fields below `private LocalDateTime createdAt;` and the `softDelete`/`isDeleted` methods:

```java
    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @Column(name = "deletion_reason", length = 500)
    private String deletionReason;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public boolean isDeleted() {
        return Boolean.TRUE.equals(this.deleted);
    }

    public void softDelete(String reason, Long adminId) {
        if (isDeleted()) {
            throw new IllegalStateException("이미 삭제된 댓글입니다");
        }
        this.deleted = true;
        this.deletionReason = reason;
        this.deletedBy = adminId;
        this.deletedAt = LocalDateTime.now();
    }
```

Also add the index:

```java
@Table(name = "comments", indexes = {
        @Index(name = "idx_comments_deleted_post", columnList = "deleted, post_id")
})
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests com.devmatch.entity.CommentSoftDeleteTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devmatch/entity/Comment.java backend/src/test/java/com/devmatch/entity/CommentSoftDeleteTest.java
git commit -m "feat(admin-post): Comment soft delete 4컬럼 + softDelete 도메인 메서드"
```

---

## Task 3: PostRepository / CommentRepository — 사용자측 쿼리 deleted 필터 + Specification 상속

**Files:**
- Modify: `backend/src/main/java/com/devmatch/repository/PostRepository.java`
- Modify: `backend/src/main/java/com/devmatch/repository/CommentRepository.java`
- Modify: `backend/src/main/java/com/devmatch/service/PostService.java` (메서드 호출부만)

- [ ] **Step 1: Modify `PostRepository.java` — deleted 필터 + Specification 상속**

Replace the file contents with:

```java
package com.devmatch.repository;

import com.devmatch.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {

    // 사용자측 커뮤니티 목록 — 삭제된 글 제외
    Page<Post> findByDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    // 특정 유저가 작성한 비삭제 게시물 수 (사용자 프로필 카운트)
    long countByAuthor_IdAndDeletedFalse(Long userId);
}
```

- [ ] **Step 2: Modify `CommentRepository.java` — 사용자측은 deleted 제외, 관리자측은 deleted 포함**

Replace the file contents with:

```java
package com.devmatch.repository;

import com.devmatch.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // 사용자측 — 삭제된 댓글 제외
    List<Comment> findByPostIdAndDeletedFalseOrderByCreatedAtAsc(Long postId);

    // 관리자측 — 삭제된 댓글 포함
    List<Comment> findByPostIdOrderByCreatedAtAsc(Long postId);
}
```

- [ ] **Step 3: Update `PostService.java` call sites — 리네임된 메서드 호출로 전환**

Open `backend/src/main/java/com/devmatch/service/PostService.java`. Find the current call sites and rename:

- `postRepository.findAllByOrderByCreatedAtDesc(pageable)` → `postRepository.findByDeletedFalseOrderByCreatedAtDesc(pageable)`
- `postRepository.countByAuthor_Id(userId)` → `postRepository.countByAuthor_IdAndDeletedFalse(userId)` (if the call exists — grep first; if it's only called from `UserService` / `AdminUserService`, rename there instead)
- `commentRepository.findByPostIdOrderByCreatedAtAsc(postId)` in the user-facing `getComments` method → `commentRepository.findByPostIdAndDeletedFalseOrderByCreatedAtAsc(postId)`

Confirm by grep:

```bash
grep -r "findAllByOrderByCreatedAtDesc\|countByAuthor_Id\|findByPostIdOrderByCreatedAtAsc" backend/src/main/java
```

Expected: every match in `src/main/java` points at repository declarations or service-layer rewrites just made. If there are stray callers in other services, update them too (use the user-facing variant when the caller is user-facing; leave admin/audit callers alone).

- [ ] **Step 4: Compile & run existing tests to confirm nothing else breaks**

Run: `cd backend && ./gradlew compileJava compileTestJava`
Expected: BUILD SUCCESSFUL (no unresolved symbol).

Run: `cd backend && ./gradlew test --tests "com.devmatch.service.PostServiceTest" --tests "com.devmatch.entity.*SoftDeleteTest"`
Expected: PASS (if `PostServiceTest` doesn't exist, only the `*SoftDeleteTest` tests run — that's fine).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devmatch/repository/PostRepository.java backend/src/main/java/com/devmatch/repository/CommentRepository.java backend/src/main/java/com/devmatch/service/PostService.java
git commit -m "refactor(post): 사용자측 Repository 쿼리에 deleted=false 필터 추가"
```

---

## Task 4: PostService / PostController — 사용자측 단건 조회에 deleted 가드

**Files:**
- Modify: `backend/src/main/java/com/devmatch/service/PostService.java`
- Modify: `backend/src/main/java/com/devmatch/controller/PostController.java` (필요 시 예외 매핑만)

- [ ] **Step 1: Add `isDeleted()` guards to `PostService`**

Edit `backend/src/main/java/com/devmatch/service/PostService.java`. For **each** method that loads a `Post` by id (`getPost`, `updatePost`, `deletePost`, `toggleLike`, `createComment`, `deleteComment`), after the `findById(...).orElseThrow(...)` call add:

```java
if (post.isDeleted()) {
    throw new EntityNotFoundException("게시물을 찾을 수 없습니다");
}
```

Also inside `deleteComment`, after loading the comment and checking it belongs to the post, add:

```java
if (comment.isDeleted()) {
    throw new EntityNotFoundException("댓글을 찾을 수 없습니다");
}
```

Rationale: 사용자측에선 삭제된 글/댓글은 404 로 응답해야 한다. `EntityNotFoundException` 이 기존 예외 매핑과 일관되도록 한다 (존재하지 않는 것과 동일 취급).

- [ ] **Step 2: Ensure `EntityNotFoundException` maps to 404**

Check `backend/src/main/java/com/devmatch/exception/GlobalExceptionHandler.java`. If no handler for `jakarta.persistence.EntityNotFoundException` exists, add one:

```java
@ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
public ResponseEntity<ApiResponse<Void>> handleEntityNotFound(jakarta.persistence.EntityNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(e.getMessage()));
}
```

Similarly add one for `IllegalStateException` mapped to 400:

```java
@ExceptionHandler(IllegalStateException.class)
public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(e.getMessage()));
}
```

If handlers already exist (grep first — `grep -n "EntityNotFoundException\|IllegalStateException" backend/src/main/java/com/devmatch/exception/GlobalExceptionHandler.java`), skip this step.

- [ ] **Step 3: Add regression test — `PostControllerTest` 단건 조회 404**

Create (or extend) `backend/src/test/java/com/devmatch/controller/PostControllerTest.java`. If the file exists, append; otherwise create with this content:

```java
package com.devmatch.controller;

import com.devmatch.entity.Post;
import com.devmatch.entity.User;
import com.devmatch.repository.PostRepository;
import com.devmatch.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostControllerDeletedGuardTest {

    @Autowired MockMvc mvc;
    @Autowired PostRepository postRepository;
    @Autowired UserRepository userRepository;

    @Test
    @WithMockUser(username = "u1@test", roles = {"USER"})
    void 삭제된_글_단건_조회는_404() throws Exception {
        User author = userRepository.save(User.builder()
                .email("u1@test").password("x").name("U1").role(com.devmatch.entity.Role.USER).build());
        Post p = postRepository.save(Post.builder()
                .author(author).title("t").content("c").category("질문")
                .likeCount(0).commentCount(0).viewCount(0).build());
        p.softDelete("삭제된 글 조회 테스트용입니다", 999L);
        postRepository.saveAndFlush(p);

        mvc.perform(get("/api/posts/" + p.getId())).andExpect(status().isNotFound());
    }
}
```

Note: if the project has an existing `@WebMvcTest`-based `PostControllerTest`, prefer mirroring that style instead; `@SpringBootTest` is a fallback when the existing test uses full context.

- [ ] **Step 4: Run regression test**

Run: `cd backend && ./gradlew test --tests "com.devmatch.controller.PostControllerDeletedGuardTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devmatch/service/PostService.java backend/src/main/java/com/devmatch/exception/GlobalExceptionHandler.java backend/src/test/java/com/devmatch/controller/PostControllerDeletedGuardTest.java
git commit -m "fix(post): 사용자측 단건 조회에 deleted 가드 + 404 매핑"
```

---

## Task 5: Admin Post DTO 5종

**Files:**
- Create: `backend/src/main/java/com/devmatch/dto/admin/post/AdminPostListItemResponse.java`
- Create: `backend/src/main/java/com/devmatch/dto/admin/post/AdminPostDetailResponse.java`
- Create: `backend/src/main/java/com/devmatch/dto/admin/post/AdminPostCommentResponse.java`
- Create: `backend/src/main/java/com/devmatch/dto/admin/post/AdminPostFilter.java`
- Create: `backend/src/main/java/com/devmatch/dto/admin/post/AdminPostDeleteRequest.java`

- [ ] **Step 1: Create `AdminPostListItemResponse.java`**

```java
package com.devmatch.dto.admin.post;

import com.devmatch.entity.Post;
import com.devmatch.util.UserDisplay;

import java.time.LocalDateTime;

public record AdminPostListItemResponse(
        Long id,
        String title,
        String category,
        Long authorId,
        String authorName,
        Integer likeCount,
        Integer commentCount,
        Integer viewCount,
        LocalDateTime createdAt,
        boolean deleted,
        LocalDateTime deletedAt
) {
    public static AdminPostListItemResponse from(Post post) {
        return new AdminPostListItemResponse(
                post.getId(),
                post.getTitle(),
                post.getCategory(),
                post.getAuthor() != null ? post.getAuthor().getId() : null,
                UserDisplay.displayName(post.getAuthor()),
                post.getLikeCount(),
                post.getCommentCount(),
                post.getViewCount(),
                post.getCreatedAt(),
                post.isDeleted(),
                post.getDeletedAt()
        );
    }
}
```

- [ ] **Step 2: Create `AdminPostCommentResponse.java`**

```java
package com.devmatch.dto.admin.post;

import com.devmatch.entity.Comment;
import com.devmatch.util.UserDisplay;

import java.time.LocalDateTime;

public record AdminPostCommentResponse(
        Long id,
        Long authorId,
        String authorName,
        String content,
        LocalDateTime createdAt,
        boolean deleted,
        String deletionReason,
        Long deletedBy,
        LocalDateTime deletedAt
) {
    public static AdminPostCommentResponse from(Comment comment) {
        return new AdminPostCommentResponse(
                comment.getId(),
                comment.getAuthor() != null ? comment.getAuthor().getId() : null,
                UserDisplay.displayName(comment.getAuthor()),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.isDeleted(),
                comment.getDeletionReason(),
                comment.getDeletedBy(),
                comment.getDeletedAt()
        );
    }
}
```

- [ ] **Step 3: Create `AdminPostDetailResponse.java`**

```java
package com.devmatch.dto.admin.post;

import com.devmatch.entity.Post;
import com.devmatch.entity.User;
import com.devmatch.util.UserDisplay;

import java.time.LocalDateTime;
import java.util.List;

public record AdminPostDetailResponse(
        Long id,
        String title,
        String content,
        String category,
        Long authorId,
        String authorName,
        String authorEmail,
        String authorRole,
        Integer likeCount,
        Integer commentCount,
        Integer viewCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean deleted,
        String deletionReason,
        Long deletedBy,
        LocalDateTime deletedAt,
        List<AdminPostCommentResponse> comments
) {
    public static AdminPostDetailResponse of(Post post, List<AdminPostCommentResponse> comments) {
        User author = post.getAuthor();
        return new AdminPostDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getCategory(),
                author != null ? author.getId() : null,
                UserDisplay.displayName(author),
                author != null ? author.getEmail() : null,
                author != null && author.getRole() != null ? author.getRole().name() : null,
                post.getLikeCount(),
                post.getCommentCount(),
                post.getViewCount(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.isDeleted(),
                post.getDeletionReason(),
                post.getDeletedBy(),
                post.getDeletedAt(),
                comments
        );
    }
}
```

- [ ] **Step 4: Create `AdminPostFilter.java`**

```java
package com.devmatch.dto.admin.post;

import java.time.LocalDate;

public record AdminPostFilter(
        String category,
        String q,
        LocalDate from,
        LocalDate to,
        boolean includeDeleted
) {}
```

- [ ] **Step 5: Create `AdminPostDeleteRequest.java`**

```java
package com.devmatch.dto.admin.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminPostDeleteRequest(
        @NotBlank(message = "사유를 입력하세요")
        @Size(min = 10, max = 500, message = "사유는 10~500자로 입력하세요")
        String reason
) {}
```

- [ ] **Step 6: Compile check + commit**

```bash
cd backend && ./gradlew compileJava
```
Expected: BUILD SUCCESSFUL.

```bash
git add backend/src/main/java/com/devmatch/dto/admin/post/
git commit -m "feat(admin-post): Admin post DTO 5종 (list/detail/comment/filter/deleteRequest)"
```

---

## Task 6: `PostSpecifications` — 동적 쿼리 빌더

**Files:**
- Create: `backend/src/main/java/com/devmatch/repository/spec/PostSpecifications.java`

- [ ] **Step 1: Create the class**

```java
package com.devmatch.repository.spec;

import com.devmatch.entity.Post;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 관리자 게시물 목록 조회 필터용 JPA Specification 빌더.
 *
 * <p>검색어 `q` 는 제목(title) + 내용(content) LIKE OR 로 처리한다.
 * 작성자 이름 기반 검색은 서비스 레이어에서 `UserRepository` 로 matched userId 집합을
 * 구해 {@link #authorIdIn(Collection)} 와 AND 결합하는 방식으로 확장할 수 있다.
 */
public final class PostSpecifications {

    private PostSpecifications() {}

    public static Specification<Post> withFilter(String category,
                                                 String q,
                                                 LocalDateTime from,
                                                 LocalDateTime toExclusive,
                                                 boolean includeDeleted) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (!includeDeleted) {
                ps.add(cb.equal(root.get("deleted"), false));
            }
            if (category != null && !category.isBlank()) {
                ps.add(cb.equal(root.get("category"), category.trim()));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (toExclusive != null) {
                ps.add(cb.lessThan(root.get("createdAt"), toExclusive));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim() + "%";
                ps.add(cb.or(
                        cb.like(root.get("title"), like),
                        cb.like(root.get("content"), like)
                ));
            }
            return ps.isEmpty() ? cb.conjunction() : cb.and(ps.toArray(new Predicate[0]));
        };
    }

    public static Specification<Post> authorIdIn(Collection<Long> authorIds) {
        return (root, query, cb) -> {
            if (authorIds == null || authorIds.isEmpty()) {
                return cb.disjunction();
            }
            return root.get("author").get("id").in(authorIds);
        };
    }
}
```

- [ ] **Step 2: Compile check**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/devmatch/repository/spec/PostSpecifications.java
git commit -m "feat(admin-post): PostSpecifications 동적 쿼리 빌더"
```

---

## Task 7: `AdminPostService` — 목록/카테고리/상세/삭제 + 감사 로그

**Files:**
- Create: `backend/src/main/java/com/devmatch/service/AdminPostService.java`
- Reference callers: `PostRepository`, `CommentRepository`, `UserRepository`, `AdminAuditLogService`

- [ ] **Step 1: Add `findDistinctCategories` to `PostRepository`**

Edit `backend/src/main/java/com/devmatch/repository/PostRepository.java` — add:

```java
    @org.springframework.data.jpa.repository.Query(
        "SELECT DISTINCT p.category FROM Post p WHERE p.deleted = false ORDER BY p.category"
    )
    java.util.List<String> findDistinctCategories();
```

- [ ] **Step 2: Create `AdminPostService.java`**

```java
package com.devmatch.service;

import com.devmatch.dto.admin.post.AdminPostCommentResponse;
import com.devmatch.dto.admin.post.AdminPostDetailResponse;
import com.devmatch.dto.admin.post.AdminPostFilter;
import com.devmatch.dto.admin.post.AdminPostListItemResponse;
import com.devmatch.entity.AdminActionType;
import com.devmatch.entity.Comment;
import com.devmatch.entity.Post;
import com.devmatch.entity.User;
import com.devmatch.repository.CommentRepository;
import com.devmatch.repository.PostRepository;
import com.devmatch.repository.UserRepository;
import com.devmatch.repository.spec.PostSpecifications;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPostService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final AdminAuditLogService auditLogService;

    public Page<AdminPostListItemResponse> listPosts(AdminPostFilter filter, Pageable pageable) {
        LocalDateTime fromDt = filter.from() != null ? filter.from().atStartOfDay() : null;
        LocalDateTime toExclusive = filter.to() != null ? filter.to().plusDays(1).atStartOfDay() : null;

        Specification<Post> spec = PostSpecifications.withFilter(
                filter.category(), filter.q(), fromDt, toExclusive, filter.includeDeleted());

        // 작성자 이름 검색 확장 — q 가 있으면 user table 도 찾아 authorId OR 결합
        if (filter.q() != null && !filter.q().isBlank()) {
            Set<Long> authorIds = userRepository
                    .findByNameContainingIgnoreCase(filter.q().trim())
                    .stream().map(User::getId).collect(Collectors.toSet());
            if (!authorIds.isEmpty()) {
                Specification<Post> base = PostSpecifications.withFilter(
                        filter.category(), null, fromDt, toExclusive, filter.includeDeleted());
                Specification<Post> byAuthor = base.and(PostSpecifications.authorIdIn(authorIds));
                spec = spec.or(byAuthor);
            }
        }

        return postRepository.findAll(spec, pageable).map(AdminPostListItemResponse::from);
    }

    public List<String> listDistinctCategories() {
        return postRepository.findDistinctCategories();
    }

    public AdminPostDetailResponse getDetail(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("게시물을 찾을 수 없습니다"));
        List<AdminPostCommentResponse> comments = commentRepository
                .findByPostIdOrderByCreatedAtAsc(postId).stream()
                .map(AdminPostCommentResponse::from)
                .toList();
        return AdminPostDetailResponse.of(post, comments);
    }

    @Transactional
    public AdminPostDetailResponse deletePost(Long postId, Long adminId, String reason) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("게시물을 찾을 수 없습니다"));

        post.softDelete(reason, adminId);  // 이미 삭제면 IllegalStateException → 400

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", post.getTitle());
        metadata.put("category", post.getCategory());
        metadata.put("authorId", post.getAuthor() != null ? post.getAuthor().getId() : null);
        metadata.put("commentCount", post.getCommentCount());

        auditLogService.record(adminId, AdminActionType.POST_DELETE,
                "POST", postId, reason, metadata);

        List<AdminPostCommentResponse> comments = commentRepository
                .findByPostIdOrderByCreatedAtAsc(postId).stream()
                .map(AdminPostCommentResponse::from)
                .toList();
        return AdminPostDetailResponse.of(post, comments);
    }

    @Transactional
    public AdminPostCommentResponse deleteComment(Long postId, Long commentId,
                                                  Long adminId, String reason) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("댓글을 찾을 수 없습니다"));

        if (comment.getPost() == null || !comment.getPost().getId().equals(postId)) {
            throw new IllegalArgumentException("해당 게시물의 댓글이 아닙니다");
        }

        comment.softDelete(reason, adminId);  // 이미 삭제면 IllegalStateException → 400
        comment.getPost().decrementCommentCount();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("postId", postId);
        metadata.put("authorId", comment.getAuthor() != null ? comment.getAuthor().getId() : null);
        metadata.put("content", comment.getContent());

        auditLogService.record(adminId, AdminActionType.COMMENT_DELETE,
                "COMMENT", commentId, reason, metadata);

        return AdminPostCommentResponse.from(comment);
    }
}
```

- [ ] **Step 3: Ensure `UserRepository.findByNameContainingIgnoreCase` exists**

Check:
```bash
grep -n "findByNameContainingIgnoreCase" backend/src/main/java/com/devmatch/repository/UserRepository.java
```

If missing, add to `UserRepository.java`:

```java
    java.util.List<User> findByNameContainingIgnoreCase(String name);
```

- [ ] **Step 4: Ensure `IllegalArgumentException` maps to 400 in `GlobalExceptionHandler`**

Grep:
```bash
grep -n "IllegalArgumentException" backend/src/main/java/com/devmatch/exception/GlobalExceptionHandler.java
```

If no handler, add:

```java
@ExceptionHandler(IllegalArgumentException.class)
public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(e.getMessage()));
}
```

- [ ] **Step 5: Compile check**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/devmatch/service/AdminPostService.java backend/src/main/java/com/devmatch/repository/PostRepository.java backend/src/main/java/com/devmatch/repository/UserRepository.java backend/src/main/java/com/devmatch/exception/GlobalExceptionHandler.java
git commit -m "feat(admin-post): AdminPostService 목록/상세/삭제 + 감사 로그"
```

---

## Task 8: `AdminPostServiceTest` — 서비스 단위 테스트

**Files:**
- Create: `backend/src/test/java/com/devmatch/service/AdminPostServiceTest.java`

- [ ] **Step 1: Write failing test suite**

```java
package com.devmatch.service;

import com.devmatch.dto.admin.post.AdminPostCommentResponse;
import com.devmatch.dto.admin.post.AdminPostDetailResponse;
import com.devmatch.dto.admin.post.AdminPostFilter;
import com.devmatch.entity.*;
import com.devmatch.repository.CommentRepository;
import com.devmatch.repository.PostRepository;
import com.devmatch.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminPostServiceTest {

    @Mock PostRepository postRepository;
    @Mock CommentRepository commentRepository;
    @Mock UserRepository userRepository;
    @Mock AdminAuditLogService auditLogService;

    @InjectMocks AdminPostService service;

    private User user(Long id, String name) {
        return User.builder().id(id).email(name + "@t").name(name)
                .role(Role.USER).status(UserStatus.ACTIVE).build();
    }

    private Post post(Long id, User author) {
        return Post.builder().id(id).author(author).title("t").content("c")
                .category("질문").likeCount(0).commentCount(1).viewCount(0).build();
    }

    private Comment comment(Long id, Post post, User author) {
        return Comment.builder().id(id).post(post).author(author).content("cc").build();
    }

    @Test
    void deletePost_정상_플로우_감사로그_호출() {
        User author = user(10L, "auth");
        Post p = post(1L, author);
        when(postRepository.findById(1L)).thenReturn(Optional.of(p));
        when(commentRepository.findByPostIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

        AdminPostDetailResponse res = service.deletePost(1L, 99L, "스팸 광고 이유로 삭제합니다");

        assertThat(p.isDeleted()).isTrue();
        assertThat(res.deleted()).isTrue();
        ArgumentCaptor<Map<String, Object>> metaCap = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).record(eq(99L), eq(AdminActionType.POST_DELETE),
                eq("POST"), eq(1L), eq("스팸 광고 이유로 삭제합니다"), metaCap.capture());
        assertThat(metaCap.getValue()).containsEntry("authorId", 10L)
                .containsEntry("category", "질문");
    }

    @Test
    void deletePost_존재하지_않으면_EntityNotFoundException() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePost(99L, 1L, "사유사유사유사유사유"))
                .isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void deletePost_이미_삭제된_경우_IllegalStateException() {
        User author = user(10L, "auth");
        Post p = post(1L, author);
        p.softDelete("기존사유기존사유", 1L);
        when(postRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.deletePost(1L, 99L, "새사유새사유새사유새사유"))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void deleteComment_commentCount_감소_및_감사로그() {
        User author = user(10L, "auth");
        Post p = post(1L, author);
        Comment c = comment(2L, p, author);
        when(commentRepository.findById(2L)).thenReturn(Optional.of(c));

        service.deleteComment(1L, 2L, 99L, "욕설포함사유욕설포함");

        assertThat(c.isDeleted()).isTrue();
        assertThat(p.getCommentCount()).isZero();
        verify(auditLogService).record(eq(99L), eq(AdminActionType.COMMENT_DELETE),
                eq("COMMENT"), eq(2L), eq("욕설포함사유욕설포함"), anyMap());
    }

    @Test
    void deleteComment_postId_불일치시_IllegalArgumentException() {
        User author = user(10L, "auth");
        Post p = post(1L, author);
        Comment c = comment(2L, p, author);
        when(commentRepository.findById(2L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.deleteComment(777L, 2L, 99L, "사유사유사유사유사유"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(auditLogService);
    }
}
```

- [ ] **Step 2: Run & verify pass**

Run: `cd backend && ./gradlew test --tests com.devmatch.service.AdminPostServiceTest`
Expected: PASS (5 tests).

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/devmatch/service/AdminPostServiceTest.java
git commit -m "test(admin-post): AdminPostService 단위 테스트 (deletePost/deleteComment 시나리오)"
```

---

## Task 9: `AdminPostController` — 4개 엔드포인트

**Files:**
- Create: `backend/src/main/java/com/devmatch/controller/AdminPostController.java`

- [ ] **Step 1: Create the controller**

```java
package com.devmatch.controller;

import com.devmatch.dto.admin.post.AdminPostCommentResponse;
import com.devmatch.dto.admin.post.AdminPostDeleteRequest;
import com.devmatch.dto.admin.post.AdminPostDetailResponse;
import com.devmatch.dto.admin.post.AdminPostFilter;
import com.devmatch.dto.admin.post.AdminPostListItemResponse;
import com.devmatch.dto.common.ApiResponse;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.AdminPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Admin Post", description = "관리자 게시물 관리 API")
@RestController
@RequestMapping("/api/admin/posts")
@RequiredArgsConstructor
public class AdminPostController {

    private final AdminPostService adminPostService;

    @Operation(summary = "게시물 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminPostListItemResponse>>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "true") boolean includeDeleted,
            Pageable pageable
    ) {
        Page<AdminPostListItemResponse> page = adminPostService.listPosts(
                new AdminPostFilter(category, q, from, to, includeDeleted), pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @Operation(summary = "카테고리 distinct 목록")
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<String>>> categories() {
        return ResponseEntity.ok(ApiResponse.success(adminPostService.listDistinctCategories()));
    }

    @Operation(summary = "게시물 상세 조회 (댓글 포함, 삭제된 것도 포함)")
    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<AdminPostDetailResponse>> detail(
            @PathVariable Long postId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminPostService.getDetail(postId)));
    }

    @Operation(summary = "게시물 강제 삭제 (soft delete)")
    @DeleteMapping("/{postId}")
    public ResponseEntity<ApiResponse<AdminPostDetailResponse>> deletePost(
            @AuthenticationPrincipal CustomUserDetails admin,
            @PathVariable Long postId,
            @Valid @RequestBody AdminPostDeleteRequest request
    ) {
        AdminPostDetailResponse res = adminPostService.deletePost(
                postId, admin.getUserId(), request.reason());
        return ResponseEntity.ok(ApiResponse.success("게시물이 삭제되었습니다", res));
    }

    @Operation(summary = "댓글 강제 삭제 (soft delete)")
    @DeleteMapping("/{postId}/comments/{commentId}")
    public ResponseEntity<ApiResponse<AdminPostCommentResponse>> deleteComment(
            @AuthenticationPrincipal CustomUserDetails admin,
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @Valid @RequestBody AdminPostDeleteRequest request
    ) {
        AdminPostCommentResponse res = adminPostService.deleteComment(
                postId, commentId, admin.getUserId(), request.reason());
        return ResponseEntity.ok(ApiResponse.success("댓글이 삭제되었습니다", res));
    }
}
```

- [ ] **Step 2: Verify `/api/admin/**` 가드 존재**

Grep:
```bash
grep -n "admin" backend/src/main/java/com/devmatch/config/SecurityConfig.java
```

Expected: existing rule like `.requestMatchers("/api/admin/**").hasRole("ADMIN")`. If missing, add it. No other change required.

- [ ] **Step 3: Compile**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/devmatch/controller/AdminPostController.java
git commit -m "feat(admin-post): AdminPostController — list/categories/detail/delete 엔드포인트"
```

---

## Task 10: `AdminPostControllerTest` — 슬라이스 테스트

**Files:**
- Create: `backend/src/test/java/com/devmatch/controller/AdminPostControllerTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.devmatch.controller;

import com.devmatch.dto.admin.post.AdminPostDetailResponse;
import com.devmatch.service.AdminPostService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminPostController.class)
class AdminPostControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @MockBean AdminPostService adminPostService;

    @Test
    @WithMockUser(roles = "USER")
    void 일반유저_접근은_403() throws Exception {
        mvc.perform(get("/api/admin/posts")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 관리자_목록_정상_200() throws Exception {
        when(adminPostService.listPosts(any(), any())).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/admin/posts")).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 삭제_사유_9자는_400() throws Exception {
        String body = om.writeValueAsString(Map.of("reason", "짧은사유9자ABC"));  // 10자 미만
        mvc.perform(delete("/api/admin/posts/1")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run**

Run: `cd backend && ./gradlew test --tests com.devmatch.controller.AdminPostControllerTest`
Expected: PASS (3 tests).

Note: If `@WebMvcTest` fails due to `SecurityConfig` dependencies, add `@Import(SecurityConfig.class)` or `@AutoConfigureMockMvc` overrides as other admin controller tests do. Check `AdminPaymentControllerTest` for the exact setup and mirror it.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/devmatch/controller/AdminPostControllerTest.java
git commit -m "test(admin-post): AdminPostController 슬라이스 — 권한/유효성/정상"
```

---

## Task 11: Pencil 목업 — 디자인 검증 게이트 (⛔ 프런트 구현 전 승인 필수)

> **이 태스크는 Pencil MCP 를 사용해 `.pen` 파일을 생성하고 스크린샷 3장을 사용자에게 보여준 뒤 명시적 승인을 받아야 다음 태스크(프런트 구현)로 넘어갈 수 있다.** Memory `feedback_frontend_preview.md` 강제 규칙.

**Files:**
- Create: `docs/mockups/admin-posts.pen`

- [ ] **Step 1: Create empty `.pen` via `open_document("new")` then save as `docs/mockups/admin-posts.pen`**

Use Pencil MCP `open_document(filePathOrNew: "new")` then `batch_design` to design the frames. Reference `docs/mockups/admin-payments.pen` if it exists (otherwise `docs/mockups/admin-users.pen`) for guideline styles.

- [ ] **Step 2: Design 3 frames (목록 / 상세 / 게시물 삭제 모달)**

Frame 1 — `/admin/posts` 목록:
- 헤더: "게시물 관리" h1 + mute description
- 필터 바 (좌→우): 카테고리 Select / 기간 DateRangePicker / 검색 Input / "삭제된 글 포함" 체크박스(기본 on)
- Table: 제목(링크) · 카테고리(Badge) · 작성자 · 👍 · 💬 · 👀 · 작성일 · 상태 — 5행 샘플 (행 2개는 정상, 1개는 dim+line-through+"삭제됨" red Badge)
- 하단: Pagination 1~5 페이지

Frame 2 — `/admin/posts/[id]` 상세:
- `← 목록` back link
- 제목 + 카테고리 Badge + "삭제됨" 적색 Badge (삭제된 예시)
- 본문 Card
- 작성자 Card: 이름/이메일/역할 + `[회원 상세 ›]`
- 댓글 섹션 4개 카드: 3개 정상 + 1개 "관리자에 의해 삭제됨" dim
- 각 댓글 카드 우측 `[ 삭제 ]` 버튼 (삭제된 카드는 버튼 숨김)
- Sticky footer: `[ 게시물 삭제 ]` variant=destructive (삭제되지 않은 경우)

Frame 3 — 게시물 삭제 Dialog:
- 제목: "게시물 삭제"
- 설명: "이 게시물은 소프트 삭제되어 사용자측에서 보이지 않게 됩니다."
- Textarea (rows=4) + 문자 카운터 (0/500, 10자 이상 필요)
- Alert destructive: 3줄 경고
  - "게시물 내용은 감사 로그에 기록됩니다."
  - "삭제 사유는 감사 로그에 남고 복구 시까지 변경되지 않습니다."
  - "이미 삭제된 게시물은 재삭제할 수 없습니다."
- DialogFooter: `[취소]` `[삭제 확정 variant=destructive]`

- [ ] **Step 3: Export 3 스크린샷 PNG**

Use Pencil MCP `export_nodes` for each frame → PNG. Save under `docs/mockups/admin-posts-preview/` (create dir).

- [ ] **Step 4: 사용자에게 스크린샷 3장 공유 + 명시적 승인 요청**

프롬프트 예시:
> "펜슬 목업 3화면 준비되었습니다 (docs/mockups/admin-posts.pen):
> 1. 목록 — 카테고리/기간/검색/삭제포함 토글 + 5행 예시
> 2. 상세 — 본문 + 작성자 + 댓글 4개(1개 삭제됨 dim) + sticky 삭제 버튼
> 3. 게시물 삭제 모달 — Textarea + 500자 카운터 + 3줄 경고
> 이대로 프런트 구현 들어가도 될까요? 수정사항 있으면 알려주세요."

- [ ] **Step 5: 승인 후 commit**

승인받은 후에만:

```bash
git add docs/mockups/admin-posts.pen docs/mockups/admin-posts-preview/
git commit -m "docs(admin-post): Pencil 목업 — 목록/상세/삭제모달 3화면 (승인됨)"
```

**⛔ 승인 미수신 시 Task 12 로 진행 금지.**

---

## Task 12: 프런트 API 클라이언트 `lib/admin/posts.ts`

**Files:**
- Create: `frontend/src/lib/admin/posts.ts`

- [ ] **Step 1: Create the API client file**

```typescript
import apiClient from '../api';
import type { ApiResponse } from '../types';

export interface AdminPostListItem {
  id: number;
  title: string;
  category: string;
  authorId: number | null;
  authorName: string;
  likeCount: number;
  commentCount: number;
  viewCount: number;
  createdAt: string;
  deleted: boolean;
  deletedAt: string | null;
}

export interface AdminPostCommentItem {
  id: number;
  authorId: number | null;
  authorName: string;
  content: string;
  createdAt: string;
  deleted: boolean;
  deletionReason: string | null;
  deletedBy: number | null;
  deletedAt: string | null;
}

export interface AdminPostDetail {
  id: number;
  title: string;
  content: string;
  category: string;
  authorId: number | null;
  authorName: string;
  authorEmail: string | null;
  authorRole: string | null;
  likeCount: number;
  commentCount: number;
  viewCount: number;
  createdAt: string;
  updatedAt: string;
  deleted: boolean;
  deletionReason: string | null;
  deletedBy: number | null;
  deletedAt: string | null;
  comments: AdminPostCommentItem[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface ListAdminPostsParams {
  page?: number;
  size?: number;
  sort?: string;
  category?: string;
  q?: string;
  from?: string; // YYYY-MM-DD
  to?: string;
  includeDeleted?: boolean;
}

export async function listAdminPosts(
  params: ListAdminPostsParams
): Promise<PageResponse<AdminPostListItem>> {
  const res = await apiClient.get<ApiResponse<PageResponse<AdminPostListItem>>>(
    '/admin/posts', { params }
  );
  return res.data.data!;
}

export async function listAdminPostCategories(): Promise<string[]> {
  const res = await apiClient.get<ApiResponse<string[]>>('/admin/posts/categories');
  return res.data.data!;
}

export async function getAdminPost(id: number): Promise<AdminPostDetail> {
  const res = await apiClient.get<ApiResponse<AdminPostDetail>>(`/admin/posts/${id}`);
  return res.data.data!;
}

export async function deleteAdminPost(
  id: number, reason: string
): Promise<AdminPostDetail> {
  const res = await apiClient.delete<ApiResponse<AdminPostDetail>>(
    `/admin/posts/${id}`, { data: { reason } }
  );
  return res.data.data!;
}

export async function deleteAdminComment(
  postId: number, commentId: number, reason: string
): Promise<AdminPostCommentItem> {
  const res = await apiClient.delete<ApiResponse<AdminPostCommentItem>>(
    `/admin/posts/${postId}/comments/${commentId}`, { data: { reason } }
  );
  return res.data.data!;
}
```

- [ ] **Step 2: Type check**

Run: `cd frontend && npx tsc --noEmit`
Expected: PASS (no errors).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/admin/posts.ts
git commit -m "feat(admin-post): 프런트 관리자 게시물 API 클라이언트"
```

---

## Task 13: `PostDeleteDialog` / `CommentDeleteDialog` 공용 모달

**Files:**
- Create: `frontend/src/app/admin/posts/_components/PostDeleteDialog.tsx`
- Create: `frontend/src/app/admin/posts/_components/CommentDeleteDialog.tsx`

- [ ] **Step 1: Create `PostDeleteDialog.tsx`**

```tsx
"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter,
  DialogHeader, DialogTitle,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { deleteAdminPost, type AdminPostDetail } from "@/lib/admin/posts";

const schema = z.object({
  reason: z.string().min(10, "사유는 10자 이상").max(500, "사유는 500자 이하"),
});
type FormValues = z.infer<typeof schema>;

interface Props {
  open: boolean;
  onOpenChange: (next: boolean) => void;
  postId: number;
  postTitle: string;
  onSuccess: (next: AdminPostDetail) => void;
}

export function PostDeleteDialog({ open, onOpenChange, postId, postTitle, onSuccess }: Props) {
  const [submitting, setSubmitting] = useState(false);
  const { register, handleSubmit, watch, reset, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { reason: "" },
  });
  const len = watch("reason")?.length ?? 0;

  async function onSubmit(v: FormValues) {
    setSubmitting(true);
    try {
      const next = await deleteAdminPost(postId, v.reason);
      toast.success("게시물이 삭제되었습니다");
      onSuccess(next);
      reset();
      onOpenChange(false);
    } catch (e) {
      const msg = (e as { response?: { data?: { message?: string } } })
        ?.response?.data?.message ?? "삭제에 실패했습니다";
      toast.error(msg);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>게시물 삭제</DialogTitle>
          <DialogDescription>
            &ldquo;{postTitle}&rdquo; 게시물을 소프트 삭제합니다.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <Textarea
              rows={4}
              placeholder="삭제 사유를 10~500자로 입력하세요"
              disabled={submitting}
              {...register("reason")}
            />
            <div className="mt-1 flex justify-between text-xs text-muted-foreground">
              <span className="text-destructive">{errors.reason?.message}</span>
              <span>{len}/500</span>
            </div>
          </div>

          <Alert variant="destructive">
            <AlertDescription>
              <ul className="list-disc pl-4 space-y-1">
                <li>게시물은 소프트 삭제되며 사용자측에서는 숨겨집니다.</li>
                <li>삭제 사유는 감사 로그에 기록됩니다.</li>
                <li>이미 삭제된 게시물은 재삭제할 수 없습니다.</li>
              </ul>
            </AlertDescription>
          </Alert>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={submitting}>
              취소
            </Button>
            <Button type="submit" variant="destructive" disabled={submitting}>
              {submitting ? "처리 중…" : "삭제 확정"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
```

- [ ] **Step 2: Create `CommentDeleteDialog.tsx`**

```tsx
"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter,
  DialogHeader, DialogTitle,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { deleteAdminComment, type AdminPostCommentItem } from "@/lib/admin/posts";

const schema = z.object({
  reason: z.string().min(10, "사유는 10자 이상").max(500, "사유는 500자 이하"),
});
type FormValues = z.infer<typeof schema>;

interface Props {
  open: boolean;
  onOpenChange: (next: boolean) => void;
  postId: number;
  commentId: number;
  onSuccess: (next: AdminPostCommentItem) => void;
}

export function CommentDeleteDialog({ open, onOpenChange, postId, commentId, onSuccess }: Props) {
  const [submitting, setSubmitting] = useState(false);
  const { register, handleSubmit, watch, reset, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { reason: "" },
  });
  const len = watch("reason")?.length ?? 0;

  async function onSubmit(v: FormValues) {
    setSubmitting(true);
    try {
      const next = await deleteAdminComment(postId, commentId, v.reason);
      toast.success("댓글이 삭제되었습니다");
      onSuccess(next);
      reset();
      onOpenChange(false);
    } catch (e) {
      const msg = (e as { response?: { data?: { message?: string } } })
        ?.response?.data?.message ?? "삭제에 실패했습니다";
      toast.error(msg);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>댓글 삭제</DialogTitle>
          <DialogDescription>이 댓글을 소프트 삭제합니다.</DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <Textarea
              rows={4}
              placeholder="삭제 사유를 10~500자로 입력하세요"
              disabled={submitting}
              {...register("reason")}
            />
            <div className="mt-1 flex justify-between text-xs text-muted-foreground">
              <span className="text-destructive">{errors.reason?.message}</span>
              <span>{len}/500</span>
            </div>
          </div>

          <Alert variant="destructive">
            <AlertDescription>
              삭제 사유는 감사 로그에 기록되며, 이미 삭제된 댓글은 재삭제할 수 없습니다.
            </AlertDescription>
          </Alert>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={submitting}>
              취소
            </Button>
            <Button type="submit" variant="destructive" disabled={submitting}>
              {submitting ? "처리 중…" : "삭제 확정"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
```

- [ ] **Step 3: Type check**

Run: `cd frontend && npx tsc --noEmit`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/admin/posts/_components/
git commit -m "feat(admin-post): 게시물/댓글 삭제 Dialog 공용 컴포넌트"
```

---

## Task 14: 목록 페이지 `/admin/posts`

**Files:**
- Create: `frontend/src/app/admin/posts/page.tsx`

- [ ] **Step 1: Inspect existing `/admin/payments/page.tsx` to mirror URL-synced state pattern**

Run: `cat frontend/src/app/admin/payments/page.tsx` — note how `useSearchParams`, `useRouter`, and `useEffect` sync URL ↔ local state.

- [ ] **Step 2: Create `page.tsx`**

```tsx
"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import type { DateRange } from "react-day-picker";
import { format } from "date-fns";

import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select";
import { Checkbox } from "@/components/ui/checkbox";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";

import { AdminListHeader } from "@/components/admin/common/AdminListHeader";
import { DebouncedSearchInput } from "@/components/admin/common/DebouncedSearchInput";
import { AdminDateRangePicker } from "@/components/admin/common/AdminDateRangePicker";
import { Pagination } from "@/components/admin/common/Pagination";

import {
  listAdminPosts, listAdminPostCategories,
  type AdminPostListItem, type PageResponse,
} from "@/lib/admin/posts";

const DEFAULT_SIZE = 20;
const ALL_CATEGORY = "__ALL__";

export default function AdminPostsPage() {
  const router = useRouter();
  const search = useSearchParams();

  const page = Number(search.get("page") ?? "0");
  const category = search.get("category") ?? "";
  const q = search.get("q") ?? "";
  const fromStr = search.get("from") ?? "";
  const toStr = search.get("to") ?? "";
  const includeDeleted = (search.get("includeDeleted") ?? "true") === "true";

  const [data, setData] = useState<PageResponse<AdminPostListItem> | null>(null);
  const [categories, setCategories] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const dateRange = useMemo<DateRange | undefined>(() => {
    if (!fromStr && !toStr) return undefined;
    return {
      from: fromStr ? new Date(fromStr) : undefined,
      to: toStr ? new Date(toStr) : undefined,
    };
  }, [fromStr, toStr]);

  const updateParam = useCallback((patch: Record<string, string | undefined>) => {
    const next = new URLSearchParams(search.toString());
    Object.entries(patch).forEach(([k, v]) => {
      if (v === undefined || v === "") next.delete(k);
      else next.set(k, v);
    });
    if (!("page" in patch)) next.set("page", "0");
    router.replace(`/admin/posts?${next.toString()}`);
  }, [search, router]);

  useEffect(() => {
    listAdminPostCategories().then(setCategories).catch(() => setCategories([]));
  }, []);

  useEffect(() => {
    setLoading(true);
    setError(null);
    listAdminPosts({
      page, size: DEFAULT_SIZE,
      category: category || undefined,
      q: q || undefined,
      from: fromStr || undefined,
      to: toStr || undefined,
      includeDeleted,
    })
      .then(setData)
      .catch((e: unknown) => {
        const msg = (e as { response?: { data?: { message?: string } } })
          ?.response?.data?.message ?? "목록을 불러오지 못했습니다";
        setError(msg);
      })
      .finally(() => setLoading(false));
  }, [page, category, q, fromStr, toStr, includeDeleted]);

  return (
    <div className="space-y-4">
      <AdminListHeader title="게시물 관리" description="커뮤니티 게시물을 조회·강제 삭제합니다." />

      <div className="flex flex-wrap items-center gap-2">
        <Select
          value={category || ALL_CATEGORY}
          onValueChange={(v) => updateParam({ category: v === ALL_CATEGORY ? undefined : v })}
        >
          <SelectTrigger className="w-40"><SelectValue placeholder="카테고리" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL_CATEGORY}>전체 카테고리</SelectItem>
            {categories.map((c) => (
              <SelectItem key={c} value={c}>{c}</SelectItem>
            ))}
          </SelectContent>
        </Select>

        <AdminDateRangePicker
          value={dateRange}
          onChange={(r) => updateParam({
            from: r?.from ? format(r.from, "yyyy-MM-dd") : undefined,
            to:   r?.to   ? format(r.to,   "yyyy-MM-dd") : undefined,
          })}
        />

        <DebouncedSearchInput
          value={q}
          onChange={(v) => updateParam({ q: v || undefined })}
          placeholder="제목/내용/작성자 검색"
        />

        <label className="flex items-center gap-2 text-sm text-muted-foreground ml-2">
          <Checkbox
            checked={includeDeleted}
            onCheckedChange={(v) => updateParam({ includeDeleted: v ? "true" : "false" })}
          />
          삭제된 글 포함
        </label>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertDescription className="flex items-center justify-between">
            <span>{error}</span>
            <Button variant="outline" size="sm" onClick={() => updateParam({})}>재시도</Button>
          </AlertDescription>
        </Alert>
      )}

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>제목</TableHead>
            <TableHead>카테고리</TableHead>
            <TableHead>작성자</TableHead>
            <TableHead className="text-right">👍</TableHead>
            <TableHead className="text-right">💬</TableHead>
            <TableHead className="text-right">👀</TableHead>
            <TableHead>작성일</TableHead>
            <TableHead>상태</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {loading && Array.from({ length: 5 }).map((_, i) => (
            <TableRow key={`sk-${i}`}>
              {Array.from({ length: 8 }).map((_, j) => (
                <TableCell key={j}><Skeleton className="h-4 w-full" /></TableCell>
              ))}
            </TableRow>
          ))}
          {!loading && data?.content.length === 0 && (
            <TableRow>
              <TableCell colSpan={8} className="h-24 text-center text-muted-foreground">
                조건에 맞는 게시물이 없습니다.
              </TableCell>
            </TableRow>
          )}
          {!loading && data?.content.map((p) => (
            <TableRow key={p.id} className={p.deleted ? "text-slate-400" : ""}>
              <TableCell className={p.deleted ? "line-through" : ""}>
                <Link href={`/admin/posts/${p.id}`} className="hover:underline">{p.title}</Link>
              </TableCell>
              <TableCell><Badge variant="outline">{p.category}</Badge></TableCell>
              <TableCell>{p.authorName}</TableCell>
              <TableCell className="text-right">{p.likeCount}</TableCell>
              <TableCell className="text-right">{p.commentCount}</TableCell>
              <TableCell className="text-right">{p.viewCount}</TableCell>
              <TableCell>{format(new Date(p.createdAt), "yyyy-MM-dd HH:mm")}</TableCell>
              <TableCell>
                {p.deleted
                  ? <Badge variant="destructive">삭제됨</Badge>
                  : <Badge>정상</Badge>}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      {data && (
        <Pagination
          page={data.number}
          totalPages={data.totalPages}
          onPageChange={(p) => updateParam({ page: String(p) })}
        />
      )}
    </div>
  );
}
```

- [ ] **Step 3: Type check + lint**

Run: `cd frontend && npx tsc --noEmit && npx next lint`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/admin/posts/page.tsx
git commit -m "feat(admin-post): 게시물 목록 페이지 (필터/페이지네이션/삭제배지)"
```

---

## Task 15: 상세 페이지 `/admin/posts/[id]`

**Files:**
- Create: `frontend/src/app/admin/posts/[id]/page.tsx`

- [ ] **Step 1: Create the detail page**

```tsx
"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { format } from "date-fns";
import { ArrowLeft } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Skeleton } from "@/components/ui/skeleton";

import { getAdminPost, type AdminPostDetail, type AdminPostCommentItem } from "@/lib/admin/posts";
import { PostDeleteDialog } from "../_components/PostDeleteDialog";
import { CommentDeleteDialog } from "../_components/CommentDeleteDialog";

export default function AdminPostDetailPage() {
  const params = useParams();
  const id = Number(params.id);

  const [detail, setDetail] = useState<AdminPostDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [postDelOpen, setPostDelOpen] = useState(false);
  const [commentDelTarget, setCommentDelTarget] = useState<number | null>(null);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    getAdminPost(id)
      .then(setDetail)
      .catch((e: unknown) => {
        const msg = (e as { response?: { data?: { message?: string } } })
          ?.response?.data?.message ?? "상세를 불러오지 못했습니다";
        setError(msg);
      })
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) return <div className="space-y-4"><Skeleton className="h-8 w-48" /><Skeleton className="h-64 w-full" /></div>;
  if (error)   return <Alert variant="destructive"><AlertDescription>{error}</AlertDescription></Alert>;
  if (!detail) return null;

  const onCommentSoftDeleted = (updated: AdminPostCommentItem) => {
    setDetail((d) => d ? {
      ...d,
      commentCount: Math.max(0, d.commentCount - 1),
      comments: d.comments.map((c) => c.id === updated.id ? updated : c),
    } : d);
  };

  return (
    <div className="space-y-6 pb-24">
      <Link href="/admin/posts" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="h-4 w-4" /> 목록
      </Link>

      <div className="flex flex-wrap items-center gap-2">
        <h1 className="text-2xl font-semibold">{detail.title}</h1>
        <Badge variant="outline">{detail.category}</Badge>
        {detail.deleted && <Badge variant="destructive">🗑 삭제됨</Badge>}
      </div>

      {detail.deleted && detail.deletionReason && (
        <Alert variant="destructive">
          <AlertDescription>
            <strong>삭제 사유:</strong> {detail.deletionReason}
            {detail.deletedAt && (
              <> · {format(new Date(detail.deletedAt), "yyyy-MM-dd HH:mm")}</>
            )}
          </AlertDescription>
        </Alert>
      )}

      <Card>
        <CardHeader><CardTitle className="text-base">본문</CardTitle></CardHeader>
        <CardContent className="whitespace-pre-wrap text-sm leading-6">
          {detail.content}
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle className="text-base">작성자</CardTitle></CardHeader>
        <CardContent className="flex items-center justify-between">
          <div className="space-y-1">
            <div>{detail.authorName}</div>
            <div className="text-sm text-muted-foreground">
              {detail.authorEmail} · {detail.authorRole}
            </div>
          </div>
          {detail.authorId && (
            <Link href={`/admin/users/${detail.authorId}`}>
              <Button variant="outline" size="sm">회원 상세 ›</Button>
            </Link>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">댓글 ({detail.commentCount})</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {detail.comments.length === 0 && (
            <div className="text-sm text-muted-foreground">댓글이 없습니다.</div>
          )}
          {detail.comments.map((c) => (
            <div key={c.id}
                 className={`rounded-md border p-3 ${c.deleted ? "opacity-60 bg-muted" : ""}`}>
              <div className="flex items-center justify-between text-sm">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{c.authorName}</span>
                  <span className="text-muted-foreground">
                    {format(new Date(c.createdAt), "yyyy-MM-dd HH:mm")}
                  </span>
                  {c.deleted && <Badge variant="destructive">삭제됨</Badge>}
                </div>
                {!c.deleted && (
                  <Button variant="outline" size="sm" onClick={() => setCommentDelTarget(c.id)}>
                    삭제
                  </Button>
                )}
              </div>
              <div className={`mt-2 text-sm ${c.deleted ? "line-through" : ""}`}>
                {c.deleted ? "관리자에 의해 삭제됨" : c.content}
              </div>
              {c.deleted && c.deletionReason && (
                <div className="mt-1 text-xs text-muted-foreground">사유: {c.deletionReason}</div>
              )}
            </div>
          ))}
        </CardContent>
      </Card>

      {!detail.deleted && (
        <div className="fixed bottom-0 left-0 right-0 border-t bg-background p-4 flex justify-end">
          <Button variant="destructive" onClick={() => setPostDelOpen(true)}>
            게시물 삭제
          </Button>
        </div>
      )}

      <PostDeleteDialog
        open={postDelOpen}
        onOpenChange={setPostDelOpen}
        postId={detail.id}
        postTitle={detail.title}
        onSuccess={(next) => setDetail(next)}
      />
      {commentDelTarget !== null && (
        <CommentDeleteDialog
          open={commentDelTarget !== null}
          onOpenChange={(v) => { if (!v) setCommentDelTarget(null); }}
          postId={detail.id}
          commentId={commentDelTarget}
          onSuccess={onCommentSoftDeleted}
        />
      )}
    </div>
  );
}
```

- [ ] **Step 2: Type check + lint**

Run: `cd frontend && npx tsc --noEmit && npx next lint`
Expected: PASS.

- [ ] **Step 3: 브라우저 수동 검증**

서버 시작: `cd frontend && npm run dev` — ADMIN 로그인 → `/admin/posts` 목록 → 1건 클릭 → 상세 진입 → 댓글 1개 삭제 시뮬레이션 → 게시물 삭제 → 삭제됨 배지 확인.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/admin/posts/[id]/page.tsx
git commit -m "feat(admin-post): 게시물 상세 페이지 (댓글 + 삭제 모달 연동)"
```

---

## Task 16: 스모크 가이드 + ROADMAP 배포 체크리스트

**Files:**
- Create: `docs/smoke/2026-04-24-admin-posts-smoke.md`
- Modify: `ROADMAP.md`

- [ ] **Step 1: Create smoke guide**

```markdown
# Admin Posts 수동 스모크 (2026-04-24)

전제: 마이그레이션 SQL(§4.1) 적용 완료. 시드 데이터에 게시물/댓글 있음.

1. ADMIN 계정 로그인 → `/admin/posts` 접근 → 목록 200 OK
2. 카테고리 Select → "질문" 선택 → 필터 적용됨
3. 검색 `JPA` 입력 → 300ms debounce 후 결과 갱신
4. 삭제된 글 포함 체크 해제 → deleted=false 만 노출
5. 임의 게시물 클릭 → `/admin/posts/{id}` 상세 진입
6. "게시물 삭제" 클릭 → 사유 9자 입력 시 에러 / 10자 이상 입력 후 "삭제 확정" → toast 성공 + "🗑 삭제됨" 배지
7. 동일 게시물에서 "게시물 삭제" 버튼 숨김 확인 (sticky footer 사라짐)
8. 일반 사용자 계정으로 `/posts/{방금삭제한id}` 접근 → 404
9. 일반 사용자 `/posts` 커뮤니티 목록에서 해당 글 안 보임
10. 상세에서 정상 댓글 1개 "삭제" → dim + "관리자에 의해 삭제됨" 라벨 + commentCount 감소
11. DB: `SELECT action_type, target_type, target_id, reason FROM admin_audit_log ORDER BY id DESC LIMIT 2;` → `POST_DELETE`/`COMMENT_DELETE` + sanitized metadata JSON
12. 이미 삭제된 게시물 DELETE API 재호출 → 400 "이미 삭제된 게시물입니다"
13. 비 ADMIN (USER) 계정으로 `/admin/posts` → 403 페이지
```

- [ ] **Step 2: Update `ROADMAP.md` 배포 체크리스트**

Locate the deploy-checklist section and append a new block (위에 결제 관련 ALTER TABLE 블록이 있다면 그 바로 아래):

```markdown
### Phase II Feature 3 — Admin Posts (2026-04-24)

배포 전 수동 실행 SQL:

\`\`\`sql
ALTER TABLE posts
  ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN deletion_reason VARCHAR(500) NULL,
  ADD COLUMN deleted_by BIGINT NULL,
  ADD COLUMN deleted_at DATETIME NULL,
  ADD INDEX idx_posts_deleted_created (deleted, created_at);

ALTER TABLE comments
  ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN deletion_reason VARCHAR(500) NULL,
  ADD COLUMN deleted_by BIGINT NULL,
  ADD COLUMN deleted_at DATETIME NULL,
  ADD INDEX idx_comments_deleted_post (deleted, post_id);
\`\`\`

- `ddl-auto=validate` prod 환경은 위 SQL 선행 필수
- feature flag 없음 (즉시 노출)
- 시드 데이터 변경 없음 (기존 행 `deleted=false` 기본값)
```

(HEREDOC 안의 내부 SQL 코드블록은 실제 파일에선 `\`\`\`` 이스케이프 제거)

- [ ] **Step 3: Commit**

```bash
git add docs/smoke/2026-04-24-admin-posts-smoke.md ROADMAP.md
git commit -m "docs(admin-post): 수동 스모크 가이드 + 배포 체크리스트 업데이트"
```

---

## Task 17: 전체 테스트 + 빌드 최종 검증 + PR 준비

- [ ] **Step 1: Run full backend test suite**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 2: Run full frontend type check + lint + build**

Run: `cd frontend && npx tsc --noEmit && npx next lint && npm run build`
Expected: 0 errors, build output generated.

- [ ] **Step 3: Manual smoke — follow `docs/smoke/2026-04-24-admin-posts-smoke.md` step-by-step**

각 스텝 체크하여 실패 시 해당 지점만 수정하고 재실행.

- [ ] **Step 4: 최종 커밋 (필요시) + PR 생성**

```bash
git log --oneline main..HEAD
gh pr create --title "feat(admin-post): Phase II Feature 3 — 게시물 관리 (soft delete + 감사 로그)" --body "$(cat <<'EOF'
## Summary
- Post/Comment soft delete (deleted/deletionReason/deletedBy/deletedAt) + softDelete(reason, adminId) 도메인 메서드
- AdminPostController 4개 엔드포인트 + AdminPostService + PostSpecifications
- 사용자측 Repository/Service 에 deleted=false 필터 + isDeleted 가드 추가
- AdminAuditLog 기록 (POST_DELETE / COMMENT_DELETE) 동일 tx
- Pencil 목업 승인 후 프런트 목록 + 상세 + 삭제 모달 구현
- 배포 체크리스트 SQL 2개 ALTER TABLE 추가

## Test plan
- [ ] `./gradlew test` backend 전체 통과
- [ ] `npm run build` frontend 빌드 통과
- [ ] `docs/smoke/2026-04-24-admin-posts-smoke.md` 12스텝 수동 스모크 통과

Spec: `docs/superpowers/specs/2026-04-24-admin-posts-design.md`
Plan: `docs/superpowers/plans/2026-04-24-admin-posts.md`

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Self-Review 결과 (계획 작성자 노트)

**Spec 커버리지:**
- ✅ §4.1 마이그레이션 SQL → Task 1/2 엔티티 + Task 16 ROADMAP
- ✅ §4.2 DTO 5종 → Task 5
- ✅ §4.3 Controller 4엔드포인트 → Task 9
- ✅ §4.4 Service + 감사 로그 → Task 7/8
- ✅ §4.5 Repository 변경 + 사용자측 영향 → Task 3/4
- ✅ §4.6 에러 시나리오 → Task 4/7/10 (GlobalExceptionHandler + 컨트롤러 슬라이스)
- ✅ §5 프런트 전체 → Task 12/13/14/15
- ✅ §6 Pencil 목업 승인 게이트 → Task 11 (프런트 태스크 이전 배치)
- ✅ §7 테스트 전략 → Task 1/2/8/10 + Task 4 회귀
- ✅ §8 배포 체크리스트 → Task 16
- ⚠️ §7.3 `GET /api/posts` 리스트에서 삭제 글 제외 회귀 테스트는 Task 3 의 Repository 시그니처 변경 + Task 4 의 PostController 단건 404 테스트로 커버되지만, 리스트 페이징 회귀가 명시적이지 않음 → Task 4 Step 3 에 기회 있을 때 확장하거나 수동 스모크 스텝 9 로 대체 가능.
- ✅ Spec §10 열린 이슈들은 out of scope 로 명시 — Task 없음 OK

**Placeholder 스캔:** 검색 결과 TBD/TODO 없음. 모든 step 에 실행 가능한 코드 또는 명령 포함.

**Type consistency:** `softDelete(reason, adminId)` 시그니처 Task 1/2/7/8 에서 일관. `AdminPostFilter` record 필드명 Task 5/7/9 일관. `listDistinctCategories()` 메서드명 Task 7/9 일관. `PostSpecifications.withFilter(category, q, from, toExclusive, includeDeleted)` 파라미터 순서 Task 6/7 일관.
