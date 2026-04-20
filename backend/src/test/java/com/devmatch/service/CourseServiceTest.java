package com.devmatch.service;

import com.devmatch.dto.course.CourseResponse;
import com.devmatch.entity.MentoringCourse;
import com.devmatch.exception.CourseNotFoundException;
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
        MentoringCourse c1 = MentoringCourse.builder().courseKey("java-backend").title("Java").iconString("☕").displayOrder(1).active(true).build();
        MentoringCourse c2 = MentoringCourse.builder().courseKey("kafka").title("Kafka").iconString("📨").displayOrder(2).active(true).build();
        when(courseRepository.findAllByActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of(c1, c2));

        List<CourseResponse> result = courseService.findAllActive();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCourseKey()).isEqualTo("java-backend");
        assertThat(result.get(1).getCourseKey()).isEqualTo("kafka");
        assertThat(result.get(0).getTitle()).isEqualTo("Java");
    }

    @Test
    void findByKey_존재하지_않으면_예외() {
        when(courseRepository.findByCourseKey("not-exist")).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> courseService.findByKey("not-exist"))
                .isInstanceOf(CourseNotFoundException.class)
                .hasMessageContaining("not-exist");
    }

    @Test
    void findActiveByKeys_은_입력_순서와_무관하게_활성만_반환한다() {
        MentoringCourse ca = MentoringCourse.builder().courseKey("a").title("A").displayOrder(1).active(true).build();
        MentoringCourse cb = MentoringCourse.builder().courseKey("b").title("B").displayOrder(2).active(true).build();
        when(courseRepository.findAllByCourseKeyInAndActiveTrue(List.of("a", "b", "c")))
                .thenReturn(List.of(ca, cb));

        List<MentoringCourse> result = courseService.findActiveByKeys(List.of("a", "b", "c"));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(MentoringCourse::getCourseKey).containsExactlyInAnyOrder("a", "b");
    }
}
