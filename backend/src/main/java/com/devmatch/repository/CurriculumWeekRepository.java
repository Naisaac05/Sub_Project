package com.devmatch.repository;
import com.devmatch.entity.CurriculumWeek;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CurriculumWeekRepository extends JpaRepository<CurriculumWeek, Long> {
    List<CurriculumWeek> findByCurriculumIdOrderByWeekNumberAsc(Long curriculumId);
    long countByCurriculumIdAndIsCompletedTrue(Long curriculumId);
}
