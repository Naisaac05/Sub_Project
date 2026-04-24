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
