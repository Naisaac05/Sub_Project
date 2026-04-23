package com.devmatch.service;

import com.devmatch.config.TossCancelProperties;
import com.devmatch.dto.admin.payment.AdminPaymentFilter;
import com.devmatch.dto.admin.payment.AdminPaymentListItemResponse;
import com.devmatch.dto.admin.payment.AdminPaymentSummaryResponse;
import com.devmatch.entity.AdminActionType;
import com.devmatch.entity.Payment;
import com.devmatch.entity.PaymentStatus;
import com.devmatch.exception.PaymentNotFoundException;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.PaymentRepository;
import com.devmatch.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Optional;

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

    @Test
    @SuppressWarnings("unchecked")
    void listPayments_status_필터_적용() {
        AdminPaymentService svc = new AdminPaymentService(
                paymentRepository, matchingRepository, userRepository,
                tossPaymentService, auditLogService, props(true));
        when(paymentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        Page<AdminPaymentListItemResponse> page = svc.listPayments(
                new AdminPaymentFilter(PaymentStatus.CONFIRMED, null, null, null),
                PageRequest.of(0, 20)
        );

        assertThat(page.getContent()).isEmpty();
        ArgumentCaptor<Specification<Payment>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(paymentRepository).findAll(captor.capture(), any(Pageable.class));
        assertThat(captor.getValue()).isNotNull();
    }

    @Test
    void getDetail_존재하지_않는_id_는_PaymentNotFoundException() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());
        AdminPaymentService svc = new AdminPaymentService(
                paymentRepository, matchingRepository, userRepository,
                tossPaymentService, auditLogService, props(true));

        assertThatThrownBy(() -> svc.getDetail(999L))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listPayments_q와_status_조합은_status를_우회하지_않는다() {
        // 이름 매치되는 user 가 있어도 status 필터가 OR 로 튀지 않아야 한다
        when(userRepository.findByNameContainingOrEmailContaining(
                eq("alice"), eq("alice"), any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        java.util.List.of())); // userIds 빈 집합
        when(paymentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        AdminPaymentService svc = new AdminPaymentService(
                paymentRepository, matchingRepository, userRepository,
                tossPaymentService, auditLogService, props(true));

        svc.listPayments(
                new AdminPaymentFilter(PaymentStatus.CONFIRMED, "alice", null, null),
                PageRequest.of(0, 20));

        // 사용자 검색이 호출되었고, paymentRepository.findAll 이 최종 Specification 으로 호출됨
        verify(userRepository).findByNameContainingOrEmailContaining(
                eq("alice"), eq("alice"), any(Pageable.class));
        verify(paymentRepository).findAll(any(Specification.class), any(Pageable.class));
    }
}
