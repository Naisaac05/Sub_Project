package com.devmatch.repository;

import com.devmatch.entity.MentorProfile;
import com.devmatch.entity.MentorStatus;
import com.devmatch.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MentorProfileRepository extends JpaRepository<MentorProfile, Long> {

    Optional<MentorProfile> findByUser(User user);

    Optional<MentorProfile> findByUserId(Long userId);

    List<MentorProfile> findByStatus(MentorStatus status);

    Page<MentorProfile> findByStatus(MentorStatus status, Pageable pageable);

    boolean existsByUserId(Long userId);

    @Query("""
        SELECT DISTINCT mp FROM MentorProfile mp
        JOIN mp.courses c
        WHERE mp.status = com.devmatch.entity.MentorStatus.APPROVED
          AND mp.user.id <> :excludeUserId
          AND (c.courseKey = :category OR c.title = :category)
          AND (:keyword = '' OR LOWER(mp.user.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
        """)
    Page<MentorProfile> findApprovedByCategoryAndKeyword(
            @Param("category") String category,
            @Param("excludeUserId") Long excludeUserId,
            @Param("keyword") String keyword,
            Pageable pageable);
}
