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
