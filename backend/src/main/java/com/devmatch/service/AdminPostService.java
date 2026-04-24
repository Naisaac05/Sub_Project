package com.devmatch.service;

import com.devmatch.dto.admin.post.AdminPostCommentResponse;
import com.devmatch.dto.admin.post.AdminPostDetailResponse;
import com.devmatch.dto.admin.post.AdminPostFilter;
import com.devmatch.dto.admin.post.AdminPostListItemResponse;
import com.devmatch.entity.AdminActionType;
import com.devmatch.entity.Comment;
import com.devmatch.entity.Post;
import com.devmatch.entity.User;
import com.devmatch.exception.CommentNotFoundException;
import com.devmatch.exception.PostNotFoundException;
import com.devmatch.repository.CommentRepository;
import com.devmatch.repository.PostRepository;
import com.devmatch.repository.UserRepository;
import com.devmatch.repository.spec.PostSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                .orElseThrow(() -> new PostNotFoundException("게시글을 찾을 수 없습니다."));
        List<AdminPostCommentResponse> comments = commentRepository
                .findByPostIdOrderByCreatedAtAsc(postId).stream()
                .map(AdminPostCommentResponse::from)
                .toList();
        return AdminPostDetailResponse.of(post, comments);
    }

    @Transactional
    public AdminPostDetailResponse deletePost(Long postId, Long adminId, String reason) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("게시글을 찾을 수 없습니다."));

        post.softDelete(reason, adminId);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", post.getTitle());
        metadata.put("category", post.getCategory());
        if (post.getAuthor() != null) {
            metadata.put("authorId", post.getAuthor().getId());
        }
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
                .orElseThrow(() -> new CommentNotFoundException("댓글을 찾을 수 없습니다."));

        if (comment.getPost() == null || !comment.getPost().getId().equals(postId)) {
            throw new IllegalArgumentException("해당 게시글의 댓글이 아닙니다.");
        }

        comment.softDelete(reason, adminId);
        comment.getPost().decrementCommentCount();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("postId", postId);
        if (comment.getAuthor() != null) {
            metadata.put("authorId", comment.getAuthor().getId());
        }
        metadata.put("content", comment.getContent());

        auditLogService.record(adminId, AdminActionType.COMMENT_DELETE,
                "COMMENT", commentId, reason, metadata);

        return AdminPostCommentResponse.from(comment);
    }
}
