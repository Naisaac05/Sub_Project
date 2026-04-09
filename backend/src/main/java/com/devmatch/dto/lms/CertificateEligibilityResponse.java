package com.devmatch.dto.lms;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter @AllArgsConstructor @Builder
public class CertificateEligibilityResponse {
    private boolean eligible;
    private int progressRate;
    private int attendanceRate;
    private int assignmentSubmitRate;
    private int requiredProgress;
    private int requiredAttendance;
    private int requiredAssignmentRate;
}
