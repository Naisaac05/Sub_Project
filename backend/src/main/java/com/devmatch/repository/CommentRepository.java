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
