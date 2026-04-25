package com.devmatch.repository;

import com.devmatch.entity.MentorChangeRequest;
import com.devmatch.entity.MentorChangeRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MentorChangeRequestRepository extends JpaRepository<MentorChangeRequest, Long> {

    boolean existsByMenteeIdAndStatus(Long menteeId, MentorChangeRequestStatus status);

    Optional<MentorChangeRequest> findFirstByMenteeIdOrderByCreatedAtDesc(Long menteeId);

    Page<MentorChangeRequest> findByStatus(MentorChangeRequestStatus status, Pageable pageable);

    @Query("""
        SELECT r FROM MentorChangeRequest r
        WHERE (:status IS NULL OR r.status = :status)
          AND (:menteeId IS NULL OR r.menteeId = :menteeId)
        """)
    Page<MentorChangeRequest> search(@Param("status") MentorChangeRequestStatus status,
                                     @Param("menteeId") Long menteeId,
                                     Pageable pageable);
}
