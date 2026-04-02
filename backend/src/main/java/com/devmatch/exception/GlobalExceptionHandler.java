package com.devmatch.exception;

import com.devmatch.dto.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ─── Phase 2 예외 (팀원 영역과 동일한 형태로 추가) ───

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateEmail(DuplicateEmailException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserNotFound(UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidToken(InvalidTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(AlreadyAppliedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAlreadyApplied(AlreadyAppliedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
    }

    // ─── Phase 4 예외 (세션/캘린더) ───

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleSessionNotFound(SessionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(SessionAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleSessionAlreadyExists(SessionAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(InvalidSessionStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidSessionState(InvalidSessionStateException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
    }

    // ─── Phase 5 예외 (결제) ───

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentNotFound(PaymentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(DuplicatePaymentException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicatePayment(DuplicatePaymentException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(PaymentFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentFailed(PaymentFailedException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
    }

    // ─── 공통 예외 ───

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception e) {
        log.error("[GlobalException] 서버 내부 오류: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("서버 내부 오류가 발생했습니다"));
    }
}
