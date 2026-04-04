package com.devmatch.repository;

import com.devmatch.entity.Payment;
import com.devmatch.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findByPaymentKey(String paymentKey);

    boolean existsByApplicationId(Long applicationId);

    Optional<Payment> findByMatchingId(Long matchingId);

    // 연장 회차 계산용: 동일 사용자의 확정된 결제 수 조회
    long countByUserIdAndStatus(Long userId, PaymentStatus status);
}
