package com.devmatch.repository;

import com.devmatch.entity.MentoringCourse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MentoringCourseRepository extends JpaRepository<MentoringCourse, Long> {

    Optional<MentoringCourse> findByCourseKey(String courseKey);

    List<MentoringCourse> findAllByActiveTrueOrderByDisplayOrderAsc();

    List<MentoringCourse> findAllByCourseKeyInAndActiveTrue(List<String> courseKeys);

    boolean existsByCourseKey(String courseKey);
}
