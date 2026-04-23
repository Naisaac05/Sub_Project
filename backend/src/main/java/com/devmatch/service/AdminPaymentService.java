package com.devmatch.service;

import com.devmatch.config.TossCancelProperties;
import com.devmatch.dto.admin.payment.*;
import com.devmatch.entity.Payment;
import com.devmatch.entity.PaymentStatus;
import com.devmatch.exception.PaymentNotFoundException;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.PaymentRepository;
import com.devmatch.repository.UserRepository;
import com.devmatch.repository.spec.PaymentSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPaymentService {

    private final PaymentRepository paymentRepository;
    private final MatchingRepository matchingRepository;
    private final UserRepository userRepository;
    private final TossPaymentService tossPaymentService;
    private final AdminAuditLogService auditLogService;
    private final TossCancelProperties tossCancelProperties;

    public AdminPaymentSummaryResponse getSummary(LocalDate from, LocalDate to) {
        LocalDateTime[] range = resolveRange(from, to);
        long totalAmount   = paymentRepository.sumAmountByStatusAndCreatedBetween(PaymentStatus.CONFIRMED, range[0], range[1]);
        long confirmedCnt  = paymentRepository.countByStatusAndCreatedBetween(PaymentStatus.CONFIRMED, range[0], range[1]);
        long refundedAmt   = paymentRepository.sumAmountByStatusAndCreatedBetween(PaymentStatus.CANCELLED, range[0], range[1]);
        long refundedCnt   = paymentRepository.countByStatusAndCreatedBetween(PaymentStatus.CANCELLED, range[0], range[1]);
        double refundRate  = (confirmedCnt + refundedCnt) == 0
                ? 0.0
                : (double) refundedCnt / (confirmedCnt + refundedCnt);
        return new AdminPaymentSummaryResponse(totalAmount, confirmedCnt, refundedAmt, refundRate);
    }

    public Page<AdminPaymentListItemResponse> listPayments(AdminPaymentFilter filter, Pageable pageable) {
        LocalDateTime[] range = resolveRange(filter.from(), filter.to());
        Specification<Payment> spec = PaymentSpecifications.withFilter(
                filter.status(), filter.q(), range[0], range[1]);

        // 사용자 이름/이메일 검색: q 가 있을 때 userId 집합을 구해 추가 Spec 결합
        if (filter.q() != null && !filter.q().isBlank()) {
            var userIds = userRepository.findByNameContainingOrEmailContaining(
                            filter.q(), filter.q(), Pageable.unpaged())
                    .map(u -> u.getId()).getContent();
            if (!userIds.isEmpty()) {
                Specification<Payment> byUser = (root, query, cb) -> root.get("userId").in(userIds);
                // orderId LIKE(Spec 내부) OR userId IN 은 or 결합
                spec = spec.or(byUser);
            }
        }

        Page<Payment> page = paymentRepository.findAll(spec, pageable);

        // N+1 방지 위해 userId 들을 한 번에 조회
        var userIds = page.getContent().stream().map(Payment::getUserId).distinct().toList();
        var userMap = userRepository.findAllById(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(u -> u.getId(), u -> u));

        return page.map(p -> {
            var u = userMap.get(p.getUserId());
            return AdminPaymentListItemResponse.of(
                    p,
                    u != null ? u.getName() : "(알 수 없음)",
                    u != null ? u.getEmail() : "");
        });
    }

    public AdminPaymentDetailResponse getDetail(Long paymentId) {
        var payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("결제 정보를 찾을 수 없습니다: " + paymentId));
        var user = userRepository.findById(payment.getUserId()).orElse(null);
        var matching = payment.getMatchingId() != null
                ? matchingRepository.findById(payment.getMatchingId()).orElse(null)
                : null;
        var admin = payment.getProcessedByAdminId() != null
                ? userRepository.findById(payment.getProcessedByAdminId()).orElse(null)
                : null;

        // ApplicationSection 은 ApplicationRepository 로 조회하되 실패 시 null 로 내려보낸다.
        // 본 Task 는 최소 시그니처만 맞추고 Application 정보는 Task 9 컨트롤러 통합 시 확장.
        AdminPaymentDetailResponse.ApplicationSection appSection = null;

        AdminPaymentDetailResponse.UserSection userSection = user == null ? null
                : new AdminPaymentDetailResponse.UserSection(
                        user.getId(), user.getName(), user.getEmail(), user.getRole().name());

        AdminPaymentDetailResponse.MatchingSection matchingSection = matching == null ? null
                : new AdminPaymentDetailResponse.MatchingSection(
                        matching.getId(),
                        matching.getMentor() != null ? matching.getMentor().getName() : "",
                        matching.getStatus().name());

        AdminPaymentDetailResponse.RefundSection refundSection =
                (payment.getStatus() == PaymentStatus.CANCELLED && payment.getCancelledAt() != null)
                        ? new AdminPaymentDetailResponse.RefundSection(
                                payment.getProcessedByAdminId(),
                                admin != null ? admin.getName() : null,
                                payment.getCancelledAt(),
                                payment.getCancelReason())
                        : null;

        return new AdminPaymentDetailResponse(
                payment.getId(), payment.getOrderId(), payment.getPaymentKey(),
                payment.getAmount(), payment.getDiscountApplied(), payment.getInstallmentMonths(),
                payment.getCourseType(), payment.getMonthsBundled(), payment.getRenewalCount(),
                payment.getStatus(), payment.getCreatedAt(), payment.getCancelledAt(),
                payment.getCancelReason(),
                userSection, appSection, matchingSection, refundSection
        );
    }

    static LocalDateTime[] resolveRange(LocalDate from, LocalDate to) {
        LocalDate toDate   = to   != null ? to   : LocalDate.now();
        LocalDate fromDate = from != null ? from : toDate.minusDays(30);
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("from 이 to 보다 늦을 수 없습니다");
        }
        return new LocalDateTime[]{
                fromDate.atStartOfDay(),
                toDate.plusDays(1).atStartOfDay() // to-exclusive
        };
    }
}
