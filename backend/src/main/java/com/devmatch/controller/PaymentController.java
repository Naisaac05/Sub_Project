package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.payment.PaymentCancelRequest;
import com.devmatch.dto.payment.PaymentConfirmRequest;
import com.devmatch.dto.payment.PaymentCreateRequest;
import com.devmatch.dto.payment.PaymentResponse;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Payment", description = "결제 API")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "결제 생성", description = "매칭에 대한 결제를 생성합니다")
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PaymentCreateRequest request) {

        PaymentResponse response = paymentService.createPayment(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("결제가 생성되었습니다", response));
    }

    @Operation(summary = "결제 승인", description = "토스페이먼츠 결제를 승인합니다")
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<PaymentResponse>> confirmPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PaymentConfirmRequest request) {

        PaymentResponse response = paymentService.confirmPayment(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("결제가 승인되었습니다", response));
    }

    @Operation(summary = "결제 취소", description = "결제를 취소합니다")
    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<ApiResponse<PaymentResponse>> cancelPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long paymentId,
            @Valid @RequestBody PaymentCancelRequest request) {

        PaymentResponse response = paymentService.cancelPayment(
                userDetails.getUserId(), paymentId, request);
        return ResponseEntity.ok(ApiResponse.success("결제가 취소되었습니다", response));
    }

    @Operation(summary = "내 결제 목록 조회", description = "본인의 결제 내역을 조회합니다")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getMyPayments(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        List<PaymentResponse> responses = paymentService.getMyPayments(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @Operation(summary = "결제 상세 조회", description = "결제 상세 정보를 조회합니다")
    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long paymentId) {

        PaymentResponse response = paymentService.getPayment(userDetails.getUserId(), paymentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
