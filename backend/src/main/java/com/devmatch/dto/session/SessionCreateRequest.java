package com.devmatch.dto.session;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@NoArgsConstructor
public class SessionCreateRequest {

    @NotNull(message = "매칭 ID는 필수입니다")
    private Long matchingId;

    @NotNull(message = "세션 날짜는 필수입니다")
    @Future(message = "세션 날짜는 미래여야 합니다")
    private LocalDate sessionDate;

    @NotNull(message = "시작 시간은 필수입니다")
    private LocalTime startTime;

    @NotNull(message = "종료 시간은 필수입니다")
    private LocalTime endTime;

    @Size(max = 1000, message = "메모는 1000자 이하여야 합니다")
    private String memo;
}
