package com.devmatch.repository;

import com.devmatch.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findByPaymentKey(String paymentKey);

    Optional<Payment> findByMatchingId(Long matchingId);

    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByMatchingId(Long matchingId);
}
