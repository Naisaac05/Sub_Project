package com.devmatch.repository;

import com.devmatch.entity.AiReviewMessage;
import com.devmatch.entity.AiReviewMessageMode;
import com.devmatch.entity.AiReviewMessageRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AiReviewMessageRepository extends JpaRepository<AiReviewMessage, Long> {

    List<AiReviewMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    List<AiReviewMessage> findBySessionIdAndIdGreaterThanOrderByCreatedAtAsc(Long sessionId, Long id);

    Optional<AiReviewMessage> findTopBySessionIdOrderByIdDesc(Long sessionId);

    Optional<AiReviewMessage> findTopBySessionIdAndRoleOrderByCreatedAtDesc(Long sessionId, AiReviewMessageRole role);

    long countBySessionIdAndQuestionIdAndRole(Long sessionId, Long questionId, AiReviewMessageRole role);

    long countBySessionIdAndQuestionIdAndRoleAndModeIn(
            Long sessionId,
            Long questionId,
            AiReviewMessageRole role,
            Collection<AiReviewMessageMode> modes
    );
}
