package com.devmatch.dto.course;

import com.devmatch.entity.MentoringCourse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Getter
@Builder
@AllArgsConstructor
public class CourseResponse {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Long id;
    private String courseKey;
    private String title;
    private String subtitle;
    private String iconString;
    private String descriptionTitle;
    private String descriptionText;
    private List<Map<String, Object>> boxes;
    private Integer displayOrder;
    private Boolean active;

    public static CourseResponse from(MentoringCourse c) {
        return CourseResponse.builder()
                .id(c.getId())
                .courseKey(c.getCourseKey())
                .title(c.getTitle())
                .subtitle(c.getSubtitle())
                .iconString(c.getIconString())
                .descriptionTitle(c.getDescriptionTitle())
                .descriptionText(c.getDescriptionText())
                .boxes(parseBoxes(c.getBoxesJson()))
                .displayOrder(c.getDisplayOrder())
                .active(c.getActive())
                .build();
    }

    private static List<Map<String, Object>> parseBoxes(String boxesJson) {
        if (boxesJson == null || boxesJson.isBlank()) return Collections.emptyList();
        try {
            return MAPPER.readValue(boxesJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("boxesJson 파싱 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
