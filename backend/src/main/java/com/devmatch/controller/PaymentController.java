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

@Tag(name = "Payment", description = "결제 API (토스페이먼츠 연동)")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "결제 생성", description = "결제를 생성합니다. orderId가 자동 생성되며, 프론트엔드에서 토스 SDK 호출에 사용됩니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody PaymentCreateRequest request
    ) {
        PaymentResponse response = paymentService.createPayment(user.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("결제가 생성되었습니다", response));
    }

    @Operation(summary = "결제 승인", description = "토스페이먼츠 결제를 승인합니다. 프론트엔드 토스 SDK 결제 완료 후 호출합니다.")
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<PaymentResponse>> confirmPayment(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody PaymentConfirmRequest request
    ) {
        PaymentResponse response = paymentService.confirmPayment(user.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("결제가 승인되었습니다", response));
    }

    @Operation(summary = "결제 취소", description = "승인된 결제를 취소합니다. 토스페이먼츠 환불 API와 연동됩니다.")
    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<ApiResponse<PaymentResponse>> cancelPayment(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long paymentId,
            @RequestBody PaymentCancelRequest request
    ) {
        PaymentResponse response = paymentService.cancelPayment(user.getUserId(), paymentId, request);
        return ResponseEntity.ok(ApiResponse.success("결제가 취소되었습니다", response));
    }

    @Operation(summary = "내 결제 목록", description = "본인의 결제 내역을 최신순으로 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getMyPayments(
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        List<PaymentResponse> payments = paymentService.getMyPayments(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(payments));
    }

    @Operation(summary = "결제 상세 조회", description = "특정 결제의 상세 정보를 조회합니다.")
    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long paymentId
    ) {
        PaymentResponse response = paymentService.getPayment(user.getUserId(), paymentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
