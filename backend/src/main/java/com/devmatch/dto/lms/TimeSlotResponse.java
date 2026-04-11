package com.devmatch.dto.lms;

import com.devmatch.entity.MentorTimeSlot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter @AllArgsConstructor @Builder
public class TimeSlotResponse {
    private Long id;
    private Long matchingId;
    private LocalDate slotDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Boolean isBooked;

    public static TimeSlotResponse from(MentorTimeSlot slot) {
        return TimeSlotResponse.builder()
                .id(slot.getId())
                .matchingId(slot.getMatchingId())
                .slotDate(slot.getSlotDate())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .isBooked(slot.getIsBooked())
                .build();
    }
}
