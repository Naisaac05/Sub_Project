package com.devmatch.repository;

import com.devmatch.entity.RecommendedMentor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RecommendedMentorRepository extends JpaRepository<RecommendedMentor, Long> {
    List<RecommendedMentor> findBySurveyResponseIdOrderByMatchScoreDesc(Long surveyId);
}
