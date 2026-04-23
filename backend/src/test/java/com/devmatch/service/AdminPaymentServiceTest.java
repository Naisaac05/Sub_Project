package com.devmatch.service;

import com.devmatch.config.TossCancelProperties;
import com.devmatch.dto.admin.payment.AdminPaymentSummaryResponse;
import com.devmatch.entity.AdminActionType;
import com.devmatch.entity.PaymentStatus;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.PaymentRepository;
import com.devmatch.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private MatchingRepository matchingRepository;
    @Mock private UserRepository userRepository;
    @Mock private TossPaymentService tossPaymentService;
    @Mock private AdminAuditLogService auditLogService;

    private TossCancelProperties props(boolean enabled) {
        return new TossCancelProperties(enabled);
    }

    @Test
    void getSummary_확정_환불_0건이면_환불률은_0() {
        when(paymentRepository.sumAmountByStatusAndCreatedBetween(eq(PaymentStatus.CONFIRMED), any(), any()))
                .thenReturn(0L);
        when(paymentRepository.countByStatusAndCreatedBetween(eq(PaymentStatus.CONFIRMED), any(), any()))
                .thenReturn(0L);
        when(paymentRepository.sumAmountByStatusAndCreatedBetween(eq(PaymentStatus.CANCELLED), any(), any()))
                .thenReturn(0L);
        when(paymentRepository.countByStatusAndCreatedBetween(eq(PaymentStatus.CANCELLED), any(), any()))
                .thenReturn(0L);

        AdminPaymentService svc = new AdminPaymentService(
                paymentRepository, matchingRepository, userRepository,
                tossPaymentService, auditLogService, props(false));

        AdminPaymentSummaryResponse res = svc.getSummary(LocalDate.of(2026,4,1), LocalDate.of(2026,4,30));

        assertThat(res.refundRate()).isEqualTo(0.0);
        assertThat(res.totalAmount()).isZero();
    }

    @Test
    void getSummary_요약_계산_정상() {
        when(paymentRepository.sumAmountByStatusAndCreatedBetween(eq(PaymentStatus.CONFIRMED), any(), any()))
                .thenReturn(12_450_000L);
        when(paymentRepository.countByStatusAndCreatedBetween(eq(PaymentStatus.CONFIRMED), any(), any()))
                .thenReturn(142L);
        when(paymentRepository.sumAmountByStatusAndCreatedBetween(eq(PaymentStatus.CANCELLED), any(), any()))
                .thenReturn(820_000L);
        when(paymentRepository.countByStatusAndCreatedBetween(eq(PaymentStatus.CANCELLED), any(), any()))
                .thenReturn(8L);

        AdminPaymentService svc = new AdminPaymentService(
                paymentRepository, matchingRepository, userRepository,
                tossPaymentService, auditLogService, props(true));

        AdminPaymentSummaryResponse res = svc.getSummary(LocalDate.of(2026,3,22), LocalDate.of(2026,4,22));

        assertThat(res.totalAmount()).isEqualTo(12_450_000);
        assertThat(res.confirmedCount()).isEqualTo(142);
        assertThat(res.refundedAmount()).isEqualTo(820_000);
        assertThat(res.refundRate()).isEqualTo(8.0 / (142 + 8));
    }

    @Test
    void getSummary_기간_역전은_IllegalArgumentException() {
        AdminPaymentService svc = new AdminPaymentService(
                paymentRepository, matchingRepository, userRepository,
                tossPaymentService, auditLogService, props(true));
        assertThatThrownBy(() -> svc.getSummary(LocalDate.of(2026,5,1), LocalDate.of(2026,4,1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
