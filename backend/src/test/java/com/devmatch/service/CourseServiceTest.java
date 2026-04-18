package com.devmatch.service;

import com.devmatch.dto.course.CourseResponse;
import com.devmatch.entity.MentoringCourse;
import com.devmatch.repository.MentoringCourseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock private MentoringCourseRepository courseRepository;
    @InjectMocks private CourseService courseService;

    @Test
    void findAllActive_은_활성코스만_display_order로_반환한다() {
        MentoringCourse c1 = MentoringCourse.builder().courseKey("java-backend").title("Java").displayOrder(1).active(true).build();
        MentoringCourse c2 = MentoringCourse.builder().courseKey("kafka").title("Kafka").displayOrder(2).active(true).build();
        when(courseRepository.findAllByActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of(c1, c2));

        List<CourseResponse> result = courseService.findAllActive();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCourseKey()).isEqualTo("java-backend");
    }

    @Test
    void findByKey_존재하지_않으면_예외() {
        when(courseRepository.findByCourseKey("missing")).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> courseService.findByKey("missing"))
                .isInstanceOf(RuntimeException.class);
    }
}
