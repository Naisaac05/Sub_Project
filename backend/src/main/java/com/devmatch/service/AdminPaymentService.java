package com.devmatch.service;

import com.devmatch.config.TossCancelProperties;
import com.devmatch.dto.admin.payment.*;
import com.devmatch.entity.AdminActionType;
import com.devmatch.entity.MatchingStatus;
import com.devmatch.entity.Payment;
import com.devmatch.entity.PaymentStatus;
import com.devmatch.entity.User;
import com.devmatch.exception.PaymentFailedException;
import com.devmatch.exception.PaymentNotFoundException;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.PaymentRepository;
import com.devmatch.repository.UserRepository;
import com.devmatch.repository.spec.PaymentSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

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

    // 이름/이메일 검색 시 관리자 목록 필터용 userId 집합 상한.
    // 과도한 매칭으로 IN(...) 절이 폭주하는 것을 방지한다.
    private static final int USER_LOOKUP_LIMIT = 1000;

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

        String q = (filter.q() != null && !filter.q().isBlank()) ? filter.q().trim() : null;

        // base: status + date-range (orderId 는 아래서 별도 처리)
        Specification<Payment> spec = PaymentSpecifications.withFilter(
                filter.status(), null, range[0], range[1]);

        if (q != null) {
            Specification<Payment> byOrderId = (root, query, cb) ->
                    cb.like(root.get("orderId"), "%" + q + "%");

            var userIds = userRepository.findByNameContainingOrEmailContaining(
                            q, q, PageRequest.of(0, USER_LOOKUP_LIMIT))
                    .map(u -> u.getId()).getContent();

            Specification<Payment> textSpec = userIds.isEmpty()
                    ? byOrderId
                    : byOrderId.or(PaymentSpecifications.userIdIn(userIds));
            spec = spec.and(textSpec);
        }

        Page<Payment> page = paymentRepository.findAll(spec, pageable);

        // N+1 방지: userId 일괄 조회
        var pageUserIds = page.getContent().stream().map(Payment::getUserId).distinct().toList();
        var userMap = userRepository.findAllById(pageUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

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

    @Transactional
    public AdminPaymentDetailResponse refundPayment(Long paymentId, Long adminId, String reason) {
        var payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("결제 정보를 찾을 수 없습니다: " + paymentId));

        // 1) status 가드 — CONFIRMED 만 환불 가능
        if (payment.getStatus() != PaymentStatus.CONFIRMED) {
            throw new PaymentFailedException("승인된 결제만 환불할 수 있습니다. 현재 상태: " + payment.getStatus());
        }

        // 2) 토스 호출 (플래그 on + paymentKey 존재)
        if (tossCancelProperties.tossCancelEnabled()) {
            if (payment.getPaymentKey() == null || payment.getPaymentKey().isBlank()) {
                throw new PaymentFailedException("환불을 위한 결제키가 없습니다 (paymentKey NULL)");
            }
            boolean ok = tossPaymentService.cancelPayment(payment.getPaymentKey(), reason);
            if (!ok) {
                throw new PaymentFailedException("토스 환불에 실패했습니다");
            }
        } else {
            log.warn("[AdminPayment] toss-cancel-enabled=false — 토스 호출 skip, 내부 상태만 변경 (paymentId={})", paymentId);
        }

        // 3) Payment 상태 전이
        payment.cancel(reason);
        payment.markProcessedByAdmin(adminId, LocalDateTime.now());

        // 4) Matching 소프트 캐스케이드
        boolean matchingAffected = false;
        Long matchingId = payment.getMatchingId();
        if (matchingId != null) {
            var matching = matchingRepository.findById(matchingId).orElse(null);
            if (matching != null &&
                    (matching.getStatus() == MatchingStatus.PENDING ||
                     matching.getStatus() == MatchingStatus.ACCEPTED ||
                     matching.getStatus() == MatchingStatus.TRIAL)) {
                matching.cancel(reason);
                matchingAffected = true;
            }
        }

        // 5) 감사 로그
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("refundAmount", payment.getAmount());
        metadata.put("matchingAffected", matchingAffected);
        if (matchingId != null) {
            metadata.put("matchingId", matchingId);
        }
        auditLogService.record(
                adminId,
                AdminActionType.PAYMENT_REFUND,
                "PAYMENT",
                payment.getId(),
                reason,
                metadata
        );

        log.info("[AdminPayment] 환불 완료 — paymentId={}, adminId={}, matchingAffected={}",
                paymentId, adminId, matchingAffected);

        return getDetail(paymentId);
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
