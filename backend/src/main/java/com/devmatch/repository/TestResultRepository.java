package com.devmatch.repository;

import com.devmatch.entity.TestResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TestResultRepository extends JpaRepository<TestResult, Long> {

    List<TestResult> findByUserIdOrderBySubmittedAtDesc(Long userId);

    Optional<TestResult> findTopByUserIdAndTest_CategoryOrderBySubmittedAtDesc(Long userId, String category);
}
