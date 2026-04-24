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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
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
