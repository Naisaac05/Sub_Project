package com.devmatch.repository;

import com.devmatch.entity.ChangeRequestStatus;
import com.devmatch.entity.SessionChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionChangeRequestRepository extends JpaRepository<SessionChangeRequest, Long> {

    List<SessionChangeRequest> findBySessionIdOrderByCreatedAtDesc(Long sessionId);

    Optional<SessionChangeRequest> findBySessionIdAndStatus(Long sessionId, ChangeRequestStatus status);

    List<SessionChangeRequest> findBySessionIdInOrderByCreatedAtDesc(List<Long> sessionIds);

    boolean existsBySessionIdAndStatus(Long sessionId, ChangeRequestStatus status);
}
