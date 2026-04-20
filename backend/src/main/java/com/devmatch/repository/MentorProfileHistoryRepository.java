package com.devmatch.repository;

import com.devmatch.entity.MentorProfileHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MentorProfileHistoryRepository extends JpaRepository<MentorProfileHistory, Long> {

    Optional<MentorProfileHistory> findTopByUserIdOrderBySubmittedAtDesc(Long userId);
}
