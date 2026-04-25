package com.devmatch.service;

import com.devmatch.dto.course.CourseResponse;
import com.devmatch.entity.MentorStatus;
import com.devmatch.entity.MentoringCourse;
import com.devmatch.exception.CourseNotFoundException;
import com.devmatch.repository.MentorProfileRepository;
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
    private final MentorProfileRepository mentorProfileRepository;

    public List<CourseResponse> findAllActive() {
        return courseRepository.findAllByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(CourseResponse::from)
                .toList();
    }

    public List<CourseResponse> findActiveWithAvailableMentors() {
        return courseRepository.findAllByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .filter(course -> countAvailableMentors(course.getCourseKey()) > 0)
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

    /**
     * 특정 코스에 배정 가능한 승인된 멘토 수를 반환합니다.
     */
    public long countAvailableMentors(String courseKey) {
        return mentorProfileRepository.findByStatus(MentorStatus.APPROVED).stream()
                .filter(profile -> profile.getCourses() != null
                        && profile.getCourses().stream()
                                .anyMatch(course -> course.getCourseKey().equals(courseKey)))
                .count();
    }
}
