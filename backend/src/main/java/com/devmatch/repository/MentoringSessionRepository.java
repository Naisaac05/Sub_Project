package com.devmatch.repository;

import com.devmatch.entity.MentoringSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MentoringSessionRepository extends JpaRepository<MentoringSession, Long> {

    List<MentoringSession> findByMenteeIdOrderBySessionDateDesc(Long menteeId);

    List<MentoringSession> findByMentorIdOrderBySessionDateDesc(Long mentorId);

    Optional<MentoringSession> findByMatchingId(Long matchingId);

    boolean existsByMatchingId(Long matchingId);
}
