package com.devmatch.dto.session;

import com.devmatch.entity.MentorAvailability;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalTime;

@Getter
@AllArgsConstructor
@Builder
public class AvailabilityResponse {

    private Long id;
    private Long mentorId;
    private String dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private Boolean isActive;

    public static AvailabilityResponse from(MentorAvailability availability) {
        return AvailabilityResponse.builder()
                .id(availability.getId())
                .mentorId(availability.getMentorId())
                .dayOfWeek(availability.getDayOfWeek())
                .startTime(availability.getStartTime())
                .endTime(availability.getEndTime())
                .isActive(availability.getIsActive())
                .build();
    }
}
