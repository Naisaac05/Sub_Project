package com.devmatch.repository;

import com.devmatch.entity.AiReviewMessage;
import com.devmatch.entity.AiReviewMessageMode;
import com.devmatch.entity.AiReviewMessageRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AiReviewMessageRepository extends JpaRepository<AiReviewMessage, Long> {

    List<AiReviewMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    List<AiReviewMessage> findBySessionIdAndIdGreaterThanOrderByCreatedAtAsc(Long sessionId, Long id);

    Optional<AiReviewMessage> findTopBySessionIdOrderByIdDesc(Long sessionId);

    Optional<AiReviewMessage> findTopBySessionIdAndRoleOrderByCreatedAtDesc(Long sessionId, AiReviewMessageRole role);

    Optional<AiReviewMessage> findByStreamRequestId(String streamRequestId);

    long countBySessionIdAndQuestionIdAndRole(Long sessionId, Long questionId, AiReviewMessageRole role);

    long countBySessionIdAndQuestionIdAndRoleAndModeIn(
            Long sessionId,
            Long questionId,
            AiReviewMessageRole role,
            Collection<AiReviewMessageMode> modes
    );

    // 🧪 테스트 전용 세션 초기화에서 사용 — AiReviewSession에 messages 관계가 없어 JPA cascade 미동작
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM AiReviewMessage m WHERE m.session.id = :sessionId")
    long deleteBySessionId(Long sessionId);
}
