package com.devmatch.repository;

import com.devmatch.entity.AiReviewSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiReviewSessionRepository extends JpaRepository<AiReviewSession, Long> {

    Optional<AiReviewSession> findTopByUserIdAndTestResultIdOrderByCreatedAtDesc(Long userId, Long testResultId);
}
