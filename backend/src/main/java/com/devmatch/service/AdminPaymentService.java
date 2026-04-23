package com.devmatch.service;

import com.devmatch.config.TossCancelProperties;
import com.devmatch.dto.admin.payment.*;
import com.devmatch.entity.PaymentStatus;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.PaymentRepository;
import com.devmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
