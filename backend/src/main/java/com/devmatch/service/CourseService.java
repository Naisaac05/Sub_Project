package com.devmatch.service;

import com.devmatch.dto.course.CourseResponse;
import com.devmatch.entity.MentoringCourse;
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
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 코스: " + courseKey));
        return CourseResponse.from(course);
    }

    public List<MentoringCourse> findActiveByKeys(List<String> courseKeys) {
        List<MentoringCourse> courses = courseRepository.findAllByCourseKeyInAndActiveTrue(courseKeys);
        if (courses.size() != courseKeys.size()) {
            throw new IllegalArgumentException("존재하지 않거나 비활성화된 코스 키가 포함되어 있습니다");
        }
        return courses;
    }
}
