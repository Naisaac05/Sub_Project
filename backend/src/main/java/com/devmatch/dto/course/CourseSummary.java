package com.devmatch.dto.course;

import com.devmatch.entity.MentoringCourse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class CourseSummary {

    private String courseKey;
    private String title;
    private String iconString;

    public static CourseSummary from(MentoringCourse course) {
        return CourseSummary.builder()
                .courseKey(course.getCourseKey())
                .title(course.getTitle())
                .iconString(course.getIconString())
                .build();
    }
}
