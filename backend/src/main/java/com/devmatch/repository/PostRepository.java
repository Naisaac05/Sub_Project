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
