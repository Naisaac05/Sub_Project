package com.devmatch.repository;

import com.devmatch.entity.TestAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestAnswerRepository extends JpaRepository<TestAnswer, Long> {

    List<TestAnswer> findByTestResultId(Long testResultId);
}
