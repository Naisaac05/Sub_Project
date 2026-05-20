package com.devmatch.repository;

import com.devmatch.entity.AiReviewCandidate;
import com.devmatch.entity.AiReviewCandidateStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiReviewCandidateRepository extends JpaRepository<AiReviewCandidate, Long> {

    List<AiReviewCandidate> findAllByOrderByCreatedAtDesc();

    boolean existsByExternalCandidateId(String externalCandidateId);

    Optional<AiReviewCandidate> findByExternalCandidateId(String externalCandidateId);

    boolean existsByTermIgnoreCaseAndCategoryIgnoreCase(String term, String category);

    long countByStatus(AiReviewCandidateStatus status);
}
