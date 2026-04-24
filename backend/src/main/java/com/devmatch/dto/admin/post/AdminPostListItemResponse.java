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
