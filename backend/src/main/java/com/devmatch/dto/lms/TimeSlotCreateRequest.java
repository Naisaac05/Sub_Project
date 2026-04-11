package com.devmatch.dto.lms;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter @NoArgsConstructor @AllArgsConstructor
public class TimeSlotCreateRequest {

    @NotNull(message = "날짜는 필수입니다")
    private LocalDate slotDate;

    @NotNull(message = "시작 시간은 필수입니다")
    private LocalTime startTime;

    @NotNull(message = "종료 시간은 필수입니다")
    private LocalTime endTime;
}
