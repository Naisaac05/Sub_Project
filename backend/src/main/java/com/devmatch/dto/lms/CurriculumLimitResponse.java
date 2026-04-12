package com.devmatch.dto.lms;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter @AllArgsConstructor @Builder
public class CurriculumLimitResponse {
    private Integer maxWeeks;
    private Integer monthsBundled;
    private LocalDate paymentDate;
    private boolean hasConfirmedPayment;
}
