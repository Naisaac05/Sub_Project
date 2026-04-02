package com.devmatch.repository;

import com.devmatch.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByTestIdOrderByOrderIndexAsc(Long testId);
}
