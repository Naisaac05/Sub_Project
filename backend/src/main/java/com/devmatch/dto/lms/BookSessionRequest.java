package com.devmatch.dto.lms;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor @AllArgsConstructor
public class BookSessionRequest {

    @NotNull(message = "슬롯 ID는 필수입니다")
    private Long slotId;

    private String memo;
}
