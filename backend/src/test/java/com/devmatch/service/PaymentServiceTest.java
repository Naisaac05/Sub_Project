package com.devmatch.service;

import com.devmatch.dto.payment.PaymentConfirmRequest;
import com.devmatch.dto.payment.PaymentCreateRequest;
import com.devmatch.dto.payment.PaymentResponse;
import com.devmatch.entity.Payment;
import com.devmatch.entity.PaymentStatus;
import com.devmatch.exception.DuplicatePaymentException;
import com.devmatch.exception.PaymentFailedException;
import com.devmatch.exception.PaymentInProgressException;
import com.devmatch.exception.QueueNotAdmittedException;
import com.devmatch.repository.ApplicationRepository;
import com.devmatch.config.TossPaymentProperties;
import com.devmatch.repository.PaymentRepository;
import com.devmatch.support.DistributedLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private TossPaymentService tossPaymentService;
    @Mock private ApplicationService applicationService;
    @Mock private DistributedLock distributedLock;
    @Mock private WaitingQueueService waitingQueueService;

    /** 대기열 통과 + 토스 실호출 허용 상태로 서비스를 만든다(승인 흐름 검증용). */
    private PaymentService service() {
        when(waitingQueueService.isActive(any())).thenReturn(true);
        return service(true);
    }

    /** 토스 실호출 플래그를 지정해 서비스를 만든다. */
    private PaymentService service(boolean tossConfirmEnabled) {
        return new PaymentService(paymentRepository, applicationRepository, tossPaymentService,
                applicationService, distributedLock, waitingQueueService,
                new TossPaymentProperties(tossConfirmEnabled, false));
    }

    /** 대기열 검사를 스텁하지 않는(=createPayment 등 미사용 경로) 서비스. */
    private PaymentService serviceWithoutQueueStub() {
        return service(true);
    }

    private Payment pending(Long userId, String orderId, int amount) {
        return Payment.builder()
                .id(1L).userId(userId).applicationId(100L)
                .orderId(orderId).amount(amount)
                .status(PaymentStatus.PENDING).build();
    }

    // ===== confirmPayment =====

    @Test
    void confirm_해피패스_락획득_토스승인_후_CONFIRMED() {
        Payment p = pending(10L, "ord_1", 990_000);
        when(distributedLock.tryLock(anyString(), any(Duration.class))).thenReturn("owner-1");
        when(paymentRepository.findByOrderId("ord_1")).thenReturn(Optional.of(p));
        when(tossPaymentService.confirmPayment("pk_1", "ord_1", 990_000)).thenReturn(true);

        PaymentResponse res = service().confirmPayment(10L,
                new PaymentConfirmRequest("pk_1", "ord_1", 990_000));

        assertThat(res.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        verify(tossPaymentService).confirmPayment("pk_1", "ord_1", 990_000);
        // 락은 트랜잭션 동기화가 없는 단위테스트에서 즉시 해제된다
        verify(distributedLock).unlock(eq("pay:confirm:lock:ord_1"), eq("owner-1"));
        // 승인 성공 시 신청서 확정 + 자동 매칭까지 이어진다
        verify(applicationService).confirmPayment(10L, 100L);
    }

    @Test
    void confirm_락획득_실패시_PaymentInProgressException_토스_미호출() {
        when(distributedLock.tryLock(anyString(), any(Duration.class))).thenReturn(null);

        assertThatThrownBy(() -> service().confirmPayment(10L,
                new PaymentConfirmRequest("pk_1", "ord_1", 990_000)))
                .isInstanceOf(PaymentInProgressException.class);

        verify(paymentRepository, never()).findByOrderId(anyString());
        verifyNoInteractions(tossPaymentService);
    }

    @Test
    void confirm_이미_CONFIRMED_면_멱등반환_토스_미호출() {
        Payment confirmed = Payment.builder()
                .id(1L).userId(10L).applicationId(100L)
                .orderId("ord_1").paymentKey("pk_old").amount(990_000)
                .status(PaymentStatus.CONFIRMED).build();
        when(distributedLock.tryLock(anyString(), any(Duration.class))).thenReturn("owner-1");
        when(paymentRepository.findByOrderId("ord_1")).thenReturn(Optional.of(confirmed));

        PaymentResponse res = service().confirmPayment(10L,
                new PaymentConfirmRequest("pk_new", "ord_1", 990_000));

        assertThat(res.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(res.getPaymentKey()).isEqualTo("pk_old"); // 기존 결과 그대로
        verify(tossPaymentService, never()).confirmPayment(anyString(), anyString(), anyInt());
    }

    @Test
    void confirm_금액불일치_는_PaymentFailedException_그리고_토스_미호출() {
        Payment p = pending(10L, "ord_1", 990_000);
        when(distributedLock.tryLock(anyString(), any(Duration.class))).thenReturn("owner-1");
        when(paymentRepository.findByOrderId("ord_1")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service().confirmPayment(10L,
                new PaymentConfirmRequest("pk_1", "ord_1", 111_111)))
                .isInstanceOf(PaymentFailedException.class);

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(tossPaymentService, never()).confirmPayment(anyString(), anyString(), anyInt());
    }

    @Test
    void confirm_타인의_결제_승인은_PaymentFailedException() {
        Payment p = pending(10L, "ord_1", 990_000);
        when(distributedLock.tryLock(anyString(), any(Duration.class))).thenReturn("owner-1");
        when(paymentRepository.findByOrderId("ord_1")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service().confirmPayment(999L,
                new PaymentConfirmRequest("pk_1", "ord_1", 990_000)))
                .isInstanceOf(PaymentFailedException.class);
        verifyNoInteractions(tossPaymentService);
    }

    // ===== createPayment =====

    @Test
    void confirm_플래그_false_면_토스_미호출하고_내부상태만_CONFIRMED() {
        // 학생 포트폴리오 정책: 실결제 차단. 키를 잘못 넣어도 코드가 막아야 한다.
        Payment p = pending(10L, "ord_1", 990_000);
        when(waitingQueueService.isActive(any())).thenReturn(true);
        when(distributedLock.tryLock(anyString(), any(Duration.class))).thenReturn("owner-1");
        when(paymentRepository.findByOrderId("ord_1")).thenReturn(Optional.of(p));

        PaymentResponse res = service(false).confirmPayment(10L,
                new PaymentConfirmRequest("pk_1", "ord_1", 990_000));

        assertThat(res.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        verifyNoInteractions(tossPaymentService);   // 외부 호출이 일어나지 않아야 한다
    }

    @Test
    void confirm_대기열_입장권_없으면_QueueNotAdmittedException_락도_시도안함() {
        when(waitingQueueService.isActive(10L)).thenReturn(false);

        assertThatThrownBy(() -> serviceWithoutQueueStub().confirmPayment(10L,
                new PaymentConfirmRequest("pk_1", "ord_1", 990_000)))
                .isInstanceOf(QueueNotAdmittedException.class);

        verifyNoInteractions(distributedLock);
        verifyNoInteractions(tossPaymentService);
    }

    // ===== createPayment =====

    @Test
    void create_이미_결제존재_신청서면_DuplicatePaymentException() {
        when(paymentRepository.existsByApplicationId(100L)).thenReturn(true);

        assertThatThrownBy(() -> serviceWithoutQueueStub().createPayment(10L,
                new PaymentCreateRequest(100L, "IMMEDIATE", 1, 0)))
                .isInstanceOf(DuplicatePaymentException.class);

        verifyNoInteractions(applicationRepository);
    }

    @Test
    void create_동시경쟁으로_유니크제약_위반시_DuplicatePaymentException_로_변환() {
        when(paymentRepository.existsByApplicationId(100L)).thenReturn(false);
        when(applicationRepository.findById(100L))
                .thenReturn(Optional.of(com.devmatch.entity.Application.builder().id(100L).build()));
        when(paymentRepository.countByUserIdAndStatus(10L, PaymentStatus.CONFIRMED)).thenReturn(0L);
        when(paymentRepository.save(any(Payment.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate application_id"));

        assertThatThrownBy(() -> serviceWithoutQueueStub().createPayment(10L,
                new PaymentCreateRequest(100L, "IMMEDIATE", 1, 0)))
                .isInstanceOf(DuplicatePaymentException.class);
    }
}
