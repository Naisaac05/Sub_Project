package com.devmatch.controller;

import com.devmatch.dto.admin.payment.*;
import com.devmatch.dto.common.ApiResponse;
import com.devmatch.entity.PaymentStatus;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.AdminPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "Admin Payment", description = "관리자 결제 관리 API")
@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final AdminPaymentService adminPaymentService;

    @Operation(summary = "결제 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminPaymentListItemResponse>>> list(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            Pageable pageable
    ) {
        Page<AdminPaymentListItemResponse> page = adminPaymentService.listPayments(
                new AdminPaymentFilter(status, q, from, to), pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @Operation(summary = "결제 요약 카드")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AdminPaymentSummaryResponse>> summary(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminPaymentService.getSummary(from, to)));
    }

    @Operation(summary = "결제 상세 조회")
    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<AdminPaymentDetailResponse>> detail(
            @PathVariable Long paymentId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminPaymentService.getDetail(paymentId)));
    }

    @Operation(summary = "관리자 강제 환불")
    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<ApiResponse<AdminPaymentDetailResponse>> refund(
            @AuthenticationPrincipal CustomUserDetails admin,
            @PathVariable Long paymentId,
            @Valid @RequestBody AdminPaymentRefundRequest request
    ) {
        AdminPaymentDetailResponse res = adminPaymentService.refundPayment(
                paymentId, admin.getUserId(), request.reason());
        return ResponseEntity.ok(ApiResponse.success("환불 처리되었습니다", res));
    }
}
