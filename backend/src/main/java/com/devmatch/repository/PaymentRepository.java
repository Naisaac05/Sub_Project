package com.devmatch.repository;

import com.devmatch.entity.Payment;
import com.devmatch.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long>,
        JpaSpecificationExecutor<Payment> {

    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findByPaymentKey(String paymentKey);

    boolean existsByApplicationId(Long applicationId);

    Optional<Payment> findByMatchingId(Long matchingId);

    // ===== 동시성 제어 — 환불 흐름에서 row lock 으로 중복 Toss 호출 차단 =====
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    // 연장 회차 계산용: 동일 사용자의 확정된 결제 수 조회
    long countByUserIdAndStatus(Long userId, PaymentStatus status);

    long countByUserId(Long userId);

    // ===== Phase II 관리자 결제 관리 =====

    @Query("""
           select coalesce(sum(p.amount), 0) from Payment p
            where p.status = :status
              and p.createdAt >= :from and p.createdAt < :toExclusive
           """)
    long sumAmountByStatusAndCreatedBetween(@Param("status") PaymentStatus status,
                                            @Param("from") LocalDateTime from,
                                            @Param("toExclusive") LocalDateTime toExclusive);

    @Query("""
           select count(p) from Payment p
            where p.status = :status
              and p.createdAt >= :from and p.createdAt < :toExclusive
           """)
    long countByStatusAndCreatedBetween(@Param("status") PaymentStatus status,
                                        @Param("from") LocalDateTime from,
                                        @Param("toExclusive") LocalDateTime toExclusive);

    @Query("""
           select coalesce(sum(p.amount), 0) from Payment p
            where p.status = :status
              and p.cancelledAt >= :from and p.cancelledAt < :toExclusive
           """)
    long sumAmountByStatusAndCancelledBetween(@Param("status") PaymentStatus status,
                                              @Param("from") LocalDateTime from,
                                              @Param("toExclusive") LocalDateTime toExclusive);

    long countByStatus(PaymentStatus status);
}
