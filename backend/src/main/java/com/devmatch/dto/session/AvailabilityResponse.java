package com.devmatch.dto.session;

import com.devmatch.entity.MentorAvailability;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalTime;

@Getter
@AllArgsConstructor
public class AvailabilityResponse {

    private Long id;
    private String dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private Boolean isActive;

    public static AvailabilityResponse from(MentorAvailability availability) {
        return new AvailabilityResponse(
                availability.getId(),
                availability.getDayOfWeek().name(),
                availability.getStartTime(),
                availability.getEndTime(),
                availability.getIsActive()
        );
    }
}
