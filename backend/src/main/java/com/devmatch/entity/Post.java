package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "posts", indexes = {
        @Index(name = "idx_posts_deleted_created", columnList = "deleted, created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false)
    @Builder.Default
    private Integer likeCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer commentCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @Column(name = "deletion_reason", length = 500)
    private String deletionReason;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public void update(String title, String content, String category) {
        this.title = title;
        this.content = content;
        this.category = category;
    }

    public void incrementLikeCount() {
        this.likeCount++;
    }

    public void decrementLikeCount() {
        if (this.likeCount > 0) this.likeCount--;
    }

    public void incrementCommentCount() {
        this.commentCount++;
    }

    public void decrementCommentCount() {
        if (this.commentCount > 0) this.commentCount--;
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

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
}
