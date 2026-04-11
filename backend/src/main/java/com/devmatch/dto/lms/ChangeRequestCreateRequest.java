package com.devmatch.dto.lms;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter @NoArgsConstructor @AllArgsConstructor
public class ChangeRequestCreateRequest {

    @NotNull(message = "세션 ID는 필수입니다")
    private Long sessionId;

    @NotNull(message = "변경 희망 날짜는 필수입니다")
    private LocalDate newDate;

    @NotNull(message = "변경 희망 시작 시간은 필수입니다")
    private LocalTime newStartTime;

    @NotNull(message = "변경 희망 종료 시간은 필수입니다")
    private LocalTime newEndTime;

    private String reason;
}
