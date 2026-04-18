package com.devmatch.service;

import com.devmatch.dto.course.CourseResponse;
import com.devmatch.entity.MentoringCourse;
import com.devmatch.exception.CourseNotFoundException;
import com.devmatch.repository.MentoringCourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

    private final MentoringCourseRepository courseRepository;

    public List<CourseResponse> findAllActive() {
        return courseRepository.findAllByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(CourseResponse::from)
                .toList();
    }

    public CourseResponse findByKey(String courseKey) {
        MentoringCourse course = courseRepository.findByCourseKey(courseKey)
                .orElseThrow(() -> new CourseNotFoundException("코스를 찾을 수 없습니다: " + courseKey));
        return CourseResponse.from(course);
    }

    public List<MentoringCourse> findActiveByKeys(List<String> courseKeys) {
        return courseRepository.findAllByCourseKeyInAndActiveTrue(courseKeys);
    }
}
