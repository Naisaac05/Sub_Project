package com.devmatch.service;

import com.devmatch.config.TossCancelProperties;
import com.devmatch.dto.admin.payment.AdminPaymentFilter;
import com.devmatch.dto.admin.payment.AdminPaymentListItemResponse;
import com.devmatch.dto.admin.payment.AdminPaymentSummaryResponse;
import com.devmatch.entity.AdminActionType;
import com.devmatch.entity.Matching;
import com.devmatch.entity.MatchingStatus;
import com.devmatch.entity.Payment;
import com.devmatch.entity.PaymentStatus;
import com.devmatch.exception.PaymentFailedException;
import com.devmatch.exception.PaymentNotFoundException;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.PaymentRepository;
import com.devmatch.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Test
    void refund_해피패스_Matching_ACCEPTED_도_CANCELLED_로_전이() {
        Payment p = Payment.builder()
                .id(1L).userId(10L).applicationId(100L).matchingId(50L)
                .orderId("ord_1").paymentKey("pk_live_abc").amount(150_000)
                .status(PaymentStatus.CONFIRMED).build();
        Matching m = Matching.builder().id(50L).status(MatchingStatus.ACCEPTED).build();
        when(paymentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(p));
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(p)); // getDetail 응답용
        when(matchingRepository.findById(50L)).thenReturn(Optional.of(m));
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        when(tossPaymentService.cancelPayment(eq("pk_live_abc"), anyString())).thenReturn(true);

        AdminPaymentService svc = new AdminPaymentService(
                paymentRepository, matchingRepository, userRepository,
                tossPaymentService, auditLogService, props(true));

        svc.refundPayment(1L, 99L, "결제 중복 — 고객 요청에 따라 환불");

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(p.getProcessedByAdminId()).isEqualTo(99L);
        assertThat(p.getCancelledAt()).isNotNull();
        assertThat(m.getStatus()).isEqualTo(MatchingStatus.CANCELLED);
        verify(tossPaymentService).cancelPayment(eq("pk_live_abc"), anyString());
        verify(auditLogService).record(
                eq(99L),
                eq(AdminActionType.PAYMENT_REFUND),
                eq("PAYMENT"),
                eq(1L),
                anyString(),
                anyMap());
    }

    @Test
    void refund_Matching_null_이면_캐스케이드_없이_Payment_만_CANCELLED() {
        Payment p = Payment.builder()
                .id(2L).userId(10L).applicationId(100L).matchingId(null)
                .orderId("ord_2").paymentKey("pk_2").amount(990_000)
                .status(PaymentStatus.CONFIRMED).build();
        when(paymentRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(p));
        when(paymentRepository.findById(2L)).thenReturn(Optional.of(p)); // getDetail 응답용
        when(tossPaymentService.cancelPayment(any(), any())).thenReturn(true);

        AdminPaymentService svc = new AdminPaymentService(
                paymentRepository, matchingRepository, userRepository,
                tossPaymentService, auditLogService, props(true));

        svc.refundPayment(2L, 99L, "신청서 오입력 환불 요청 — 접수");

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(matchingRepository, never()).findById(any());
    }

    @Test
    void refund_Matching_REJECTED_은_skip() {
        Payment p = Payment.builder()
                .id(3L).userId(10L).applicationId(100L).matchingId(70L)
                .orderId("ord_3").paymentKey("pk_3").amount(990_000)
                .status(PaymentStatus.CONFIRMED).build();
        Matching m = Matching.builder().id(70L).status(MatchingStatus.REJECTED).build();
        when(paymentRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(p));
        when(paymentRepository.findById(3L)).thenReturn(Optional.of(p)); // getDetail 응답용
        when(matchingRepository.findById(70L)).thenReturn(Optional.of(m));
        when(tossPaymentService.cancelPayment(any(), any())).thenReturn(true);

        AdminPaymentService svc = new AdminPaymentService(
                paymentRepository, matchingRepository, userRepository,
                tossPaymentService, auditLogService, props(true));

        svc.refundPayment(3L, 99L, "이중 청구로 환불 처리합니다");

        assertThat(m.getStatus()).isEqualTo(MatchingStatus.REJECTED);
    }

    @Test
    void refund_PENDING_결제_환불_시도는_PaymentFailedException() {
        Payment p = Payment.builder().id(4L).status(PaymentStatus.PENDING).build();
        when(paymentRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(p));

        AdminPaymentService svc = new AdminPaymentService(
                paymentRepository, matchingRepository, userRepository,
                tossPaymentService, auditLogService, props(true));

        assertThatThrownBy(() -> svc.refundPayment(4L, 99L, "환불 요청에 따른 처리"))
                .isInstanceOf(PaymentFailedException.class);
        verifyNoInteractions(tossPaymentService);
    }

    @Test
    void refund_CANCELLED_재환불_시도는_PaymentFailedException() {
        Payment p = Payment.builder().id(5L).status(PaymentStatus.CANCELLED).build();
        when(paymentRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(p));

        AdminPaymentService svc = new AdminPaymentService(
                paymentRepository, matchingRepository, userRepository,
                tossPaymentService, auditLogService, props(true));

        assertThatThrownBy(() -> svc.refundPayment(5L, 99L, "재환불 시도"))
                .isInstanceOf(PaymentFailedException.class);
    }

    @Test
    void refund_플래그_false_면_토스_호출_skip() {
        Payment p = Payment.builder()
                .id(6L).userId(10L).matchingId(null)
                .orderId("ord_6").paymentKey(null).amount(990_000)
                .status(PaymentStatus.CONFIRMED).build();
        when(paymentRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(p));
        when(paymentRepository.findById(6L)).thenReturn(Optional.of(p)); // getDetail 응답용

        AdminPaymentService svc = new AdminPaymentService(
                paymentRepository, matchingRepository, userRepository,
                tossPaymentService, auditLogService, props(false));

        svc.refundPayment(6L, 99L, "dev 환경 테스트 환불");

        verifyNoInteractions(tossPaymentService);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(auditLogService).record(
                eq(99L),
                eq(AdminActionType.PAYMENT_REFUND),
                eq("PAYMENT"),
                eq(6L),
                anyString(),
                anyMap());
    }

    @Test
    void refund_플래그_true_인데_paymentKey_NULL_은_PaymentFailedException() {
        Payment p = Payment.builder()
                .id(7L).paymentKey(null).status(PaymentStatus.CONFIRMED).build();
        when(paymentRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(p));

        AdminPaymentService svc = new AdminPaymentService(
                paymentRepository, matchingRepository, userRepository,
                tossPaymentService, auditLogService, props(true));

        assertThatThrownBy(() -> svc.refundPayment(7L, 99L, "prod 환경 토스키 결여 케이스"))
                .isInstanceOf(PaymentFailedException.class);
        verifyNoInteractions(tossPaymentService);
    }

    @Test
    void refund_토스_호출_실패는_PaymentFailedException_전파() {
        Payment p = Payment.builder()
                .id(8L).paymentKey("pk_x").status(PaymentStatus.CONFIRMED).build();
        when(paymentRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(p));
        when(tossPaymentService.cancelPayment(eq("pk_x"), any()))
                .thenThrow(new PaymentFailedException("토스 4xx"));

        AdminPaymentService svc = new AdminPaymentService(
                paymentRepository, matchingRepository, userRepository,
                tossPaymentService, auditLogService, props(true));

        assertThatThrownBy(() -> svc.refundPayment(8L, 99L, "토스 실패 케이스 테스트"))
                .isInstanceOf(PaymentFailedException.class);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void refund_동시요청_두번째는_상태가드에서_차단되어_Toss_미호출() {
        // Admin1 의 트랜잭션이 이미 commit 된 직후 Admin2 가 락을 획득해 재읽기한 상황을 시뮬레이션.
        // 첫 호출은 CONFIRMED, 두 번째 호출은 CANCELLED 를 반환.
        Payment first = Payment.builder()
                .id(100L).userId(10L).applicationId(100L).matchingId(null)
                .orderId("ord_concurrent").paymentKey("pk_concurrent").amount(150_000)
                .status(PaymentStatus.CONFIRMED).build();
        Payment second = Payment.builder()
                .id(100L).userId(10L).applicationId(100L).matchingId(null)
                .orderId("ord_concurrent").paymentKey("pk_concurrent").amount(150_000)
                .status(PaymentStatus.CANCELLED).build();

        when(paymentRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(first))
                .thenReturn(Optional.of(second));
        // Admin1 의 환불 후 getDetail 응답용 (Admin2 는 status 가드에서 throw 하므로 도달 안 함)
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(first));
        when(tossPaymentService.cancelPayment(eq("pk_concurrent"), anyString())).thenReturn(true);

        AdminPaymentService svc = new AdminPaymentService(
                paymentRepository, matchingRepository, userRepository,
                tossPaymentService, auditLogService, props(true));

        // Admin1: 정상 환불
        svc.refundPayment(100L, 99L, "Admin1 환불 처리");
        assertThat(first.getStatus()).isEqualTo(PaymentStatus.CANCELLED);

        // Admin2: 락이 풀린 뒤 재읽기 결과가 CANCELLED → status 가드에서 차단
        assertThatThrownBy(() -> svc.refundPayment(100L, 88L, "Admin2 동시 환불 시도"))
                .isInstanceOf(PaymentFailedException.class);

        // Toss 환불 API 는 정확히 1회만 호출되어야 한다 (중복 차단의 핵심 검증)
        verify(tossPaymentService, times(1)).cancelPayment(eq("pk_concurrent"), anyString());
        // 감사 로그도 1회만 기록
        verify(auditLogService, times(1)).record(
                eq(99L),
                eq(AdminActionType.PAYMENT_REFUND),
                eq("PAYMENT"),
                eq(100L),
                anyString(),
                anyMap());
    }

    @Test
    void refund_낙관적잠금_충돌시_OptimisticLockingFailureException_전파() {
        // 다른 코드 경로(예: 자동 배치, 결제 confirm 흐름)가 같은 Payment 를 동시 수정한 경우를 시뮬레이션.
        // findByIdForUpdate 는 정상 반환하지만, 감사 로그 기록 시점에 @Version 충돌이 발생.
        // 주의: 이 시점엔 이미 Toss 호출이 일어났을 수 있음 — 그래서 1차 방어가 PESSIMISTIC_WRITE 인 이유.
        Payment p = Payment.builder()
                .id(200L).userId(10L).applicationId(100L).matchingId(null)
                .orderId("ord_optlock").paymentKey("pk_optlock").amount(150_000)
                .status(PaymentStatus.CONFIRMED).build();
        when(paymentRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(p));
        when(tossPaymentService.cancelPayment(eq("pk_optlock"), anyString())).thenReturn(true);
        org.mockito.Mockito.doThrow(new OptimisticLockingFailureException("version mismatch"))
                .when(auditLogService).record(
                        eq(99L), eq(AdminActionType.PAYMENT_REFUND),
                        eq("PAYMENT"), eq(200L), anyString(), anyMap());

        AdminPaymentService svc = new AdminPaymentService(
                paymentRepository, matchingRepository, userRepository,
                tossPaymentService, auditLogService, props(true));

        assertThatThrownBy(() -> svc.refundPayment(200L, 99L, "낙관적 잠금 충돌 시나리오"))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
}
