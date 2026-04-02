package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.community.CommentCreateRequest;
import com.devmatch.dto.community.CommentResponse;
import com.devmatch.dto.community.PostCreateRequest;
import com.devmatch.dto.community.PostResponse;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Community", description = "커뮤니티 API")
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    // ===== 게시글 =====

    @Operation(summary = "게시글 작성", description = "커뮤니티에 게시글을 작성합니다")
    @PostMapping
    public ResponseEntity<ApiResponse<PostResponse>> createPost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PostCreateRequest request) {

        PostResponse response = postService.createPost(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("게시글이 작성되었습니다", response));
    }

    @Operation(summary = "게시글 목록 조회", description = "게시글 목록을 페이지네이션으로 조회합니다")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<PostResponse>>> getPosts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<PostResponse> responses = postService.getPosts(userDetails.getUserId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @Operation(summary = "게시글 상세 조회", description = "게시글 상세 정보를 조회합니다")
    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostResponse>> getPost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId) {

        PostResponse response = postService.getPost(userDetails.getUserId(), postId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "게시글 수정", description = "본인의 게시글을 수정합니다")
    @PutMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostResponse>> updatePost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @Valid @RequestBody PostCreateRequest request) {

        PostResponse response = postService.updatePost(userDetails.getUserId(), postId, request);
        return ResponseEntity.ok(ApiResponse.success("게시글이 수정되었습니다", response));
    }

    @Operation(summary = "게시글 삭제", description = "본인의 게시글을 삭제합니다")
    @DeleteMapping("/{postId}")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId) {

        postService.deletePost(userDetails.getUserId(), postId);
        return ResponseEntity.ok(ApiResponse.success("게시글이 삭제되었습니다", null));
    }

    // ===== 좋아요 =====

    @Operation(summary = "좋아요 토글", description = "게시글에 좋아요를 누르거나 취소합니다")
    @PostMapping("/{postId}/like")
    public ResponseEntity<ApiResponse<PostResponse>> toggleLike(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId) {

        PostResponse response = postService.toggleLike(userDetails.getUserId(), postId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ===== 댓글 =====

    @Operation(summary = "댓글 작성", description = "게시글에 댓글을 작성합니다")
    @PostMapping("/{postId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request) {

        CommentResponse response = postService.createComment(
                userDetails.getUserId(), postId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("댓글이 작성되었습니다", response));
    }

    @Operation(summary = "댓글 목록 조회", description = "게시글의 댓글 목록을 조회합니다")
    @GetMapping("/{postId}/comments")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> getComments(
            @PathVariable Long postId) {

        List<CommentResponse> responses = postService.getComments(postId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @Operation(summary = "댓글 삭제", description = "본인의 댓글을 삭제합니다")
    @DeleteMapping("/{postId}/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @PathVariable Long commentId) {

        postService.deleteComment(userDetails.getUserId(), postId, commentId);
        return ResponseEntity.ok(ApiResponse.success("댓글이 삭제되었습니다", null));
    }
}
