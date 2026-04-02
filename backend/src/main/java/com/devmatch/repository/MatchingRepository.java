package com.devmatch.repository;

import com.devmatch.entity.Matching;
import com.devmatch.entity.MatchingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchingRepository extends JpaRepository<Matching, Long> {

    List<Matching> findByMenteeIdOrderByCreatedAtDesc(Long menteeId);

    List<Matching> findByMentorIdOrderByCreatedAtDesc(Long mentorId);

    List<Matching> findByMentorIdAndStatus(Long mentorId, MatchingStatus status);

    boolean existsByMenteeIdAndMentorIdAndStatus(Long menteeId, Long mentorId, MatchingStatus status);
}
