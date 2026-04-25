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

    /**
     * 후보 멘토 조회 — 카테고리 정규화 부분 매칭(소문자, 공백·하이픈 제거 후 LIKE)으로
     * 다음 사례까지 모두 매칭되도록 한다.
     * <ul>
     *   <li>"Java Backend" (공백) ↔ courseKey "java-backend" (하이픈)</li>
     *   <li>"backend" ↔ courseKey "java-backend"/"node-backend"/"python-backend"</li>
     *   <li>title 도 같은 방식으로 정규화 비교</li>
     * </ul>
     * 정확 매칭만 했을 때 멘티 신청서의 category 와 등록 코스의 식별자가
     * 다른 표기로 들어와 후보가 0명이 되는 사례가 있어 부분 매칭으로 완화.
     */
    @Query("""
        SELECT DISTINCT mp FROM MentorProfile mp
        JOIN mp.courses c
        WHERE mp.status = com.devmatch.entity.MentorStatus.APPROVED
          AND mp.user.id <> :excludeUserId
          AND (
            LOWER(REPLACE(REPLACE(c.courseKey, '-', ''), ' ', ''))
              LIKE CONCAT('%', LOWER(REPLACE(REPLACE(:category, '-', ''), ' ', '')), '%')
            OR LOWER(REPLACE(REPLACE(c.title, '-', ''), ' ', ''))
              LIKE CONCAT('%', LOWER(REPLACE(REPLACE(:category, '-', ''), ' ', '')), '%')
          )
          AND (:keyword = '' OR LOWER(mp.user.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
        """)
    Page<MentorProfile> findApprovedByCategoryAndKeyword(
            @Param("category") String category,
            @Param("excludeUserId") Long excludeUserId,
            @Param("keyword") String keyword,
            Pageable pageable);

}
