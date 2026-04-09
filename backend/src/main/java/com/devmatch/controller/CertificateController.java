package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.lms.CertificateEligibilityResponse;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.CertificateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "LMS - Certificate", description = "수료증 API")
@RestController
@RequestMapping("/api/lms/certificate")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;

    @Operation(summary = "수료 자격 확인")
    @GetMapping("/eligibility/{matchingId}")
    public ResponseEntity<ApiResponse<CertificateEligibilityResponse>> checkEligibility(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId) {
        return ResponseEntity.ok(ApiResponse.success(
                certificateService.checkEligibility(user.getUserId(), matchingId)));
    }

    @Operation(summary = "수료증 PDF 다운로드")
    @GetMapping("/{matchingId}/download")
    public ResponseEntity<byte[]> download(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId) {
        byte[] pdfBytes = certificateService.generatePdf(user.getUserId(), matchingId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=certificate.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}
