package com.devmatch.service;

import com.devmatch.config.TossPaymentProperties;
import com.devmatch.dto.payment.PaymentConfirmRequest;
import com.devmatch.dto.payment.PaymentCreateRequest;
import com.devmatch.dto.payment.PaymentResponse;
import com.devmatch.entity.Application;
import com.devmatch.entity.EnrollmentPlan;
import com.devmatch.entity.Payment;
import com.devmatch.entity.PaymentStatus;
import com.devmatch.exception.DuplicatePaymentException;
import com.devmatch.exception.PaymentFailedException;
import com.devmatch.exception.PaymentInProgressException;
import com.devmatch.exception.QueueNotAdmittedException;
import com.devmatch.repository.ApplicationRepository;
import com.devmatch.repository.PaymentRepository;
import com.devmatch.support.DistributedLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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

/**
 * 가격 정책 회귀 테스트.
 *
 * <p>이 테스트의 기대 금액은 프론트엔드 결제 플랜 카드가 보여주는 금액과 같아야 한다.
 * 두 값이 갈라지면 사용자가 본 가격과 실제 청구 금액이 달라지므로, 정책을 바꿀 때는
 * 이 테스트와 프론트 표시를 함께 갱신해야 한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    private static final Long USER_ID = 7L;

    @Mock private PaymentRepository paymentRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private TossPaymentService tossPaymentService;
    @Mock private ApplicationService applicationService;
    @Mock private DistributedLock distributedLock;
    @Mock private WaitingQueueService waitingQueueService;

    /**
     * 기본 서비스 — 대기열 통과 + 락 획득 + 토스 실호출 허용.
     * 가격 계산 경로는 이 스텁들과 무관하므로 그대로 써도 된다.
     */
    private PaymentService service() {
        return service(true);
    }

    /** 토스 실호출 플래그를 지정해 서비스를 만든다. 대기열·락은 통과 상태로 스텁된다. */
    private PaymentService service(boolean tossConfirmEnabled) {
        when(waitingQueueService.isActive(any())).thenReturn(true);
        when(distributedLock.tryLock(anyString(), any(Duration.class))).thenReturn("owner-1");
        return build(tossConfirmEnabled);
    }

    /** 대기열·락을 스텁하지 않는 서비스 (그 경로 자체를 검증하는 테스트용). */
    private PaymentService serviceWithoutQueueStub() {
        return build(true);
    }

    private PaymentService build(boolean tossConfirmEnabled) {
        return new PaymentService(paymentRepository, applicationRepository, tossPaymentService,
                applicationService, distributedLock, waitingQueueService,
                new TossPaymentProperties(tossConfirmEnabled, false));
    }

    private Payment pending(Long userId, String orderId, int amount) {
        return Payment.builder()
                .id(1L).userId(userId).applicationId(100L)
                .orderId(orderId).amount(amount)
                .status(PaymentStatus.PENDING).build();
    }

    private void 최초결제_사용자() {
        when(paymentRepository.countByUserIdAndStatus(USER_ID, PaymentStatus.CONFIRMED)).thenReturn(0L);
    }

    // ===== 플랜별 4개월 기준 금액 (결제 플랜 카드 표시가와 동일해야 함) =====

    @Test
    void 즉시시작_4개월_최초결제는_468만원() {
        최초결제_사용자();

        var result = service().calculatePricing(USER_ID, EnrollmentPlan.IMMEDIATE, 4);

        assertThat(result.finalAmount()).isEqualTo(4_680_000);
    }

    @Test
    void 얼리버드1차_4개월_최초결제는_458만원() {
        최초결제_사용자();

        var result = service().calculatePricing(USER_ID, EnrollmentPlan.EARLY_BIRD_1, 4);

        assertThat(result.finalAmount()).isEqualTo(4_580_000);
    }

    @Test
    void 얼리버드2차_4개월_최초결제는_448만원() {
        최초결제_사용자();

        var result = service().calculatePricing(USER_ID, EnrollmentPlan.EARLY_BIRD_2, 4);

        assertThat(result.finalAmount()).isEqualTo(4_480_000);
    }

    // ===== 계산 내역 (정가/할인 분해가 표시용으로 맞아떨어지는지) =====

    @Test
    void 즉시시작_4개월_할인내역은_정가520만원에_묶음할인52만원() {
        최초결제_사용자();

        var result = service().calculatePricing(USER_ID, EnrollmentPlan.IMMEDIATE, 4);

        assertThat(result.unitPrice()).isEqualTo(1_300_000);
        assertThat(result.rawTotal()).isEqualTo(5_200_000);
        assertThat(result.bundleDiscount()).isEqualTo(520_000);
        assertThat(result.planDiscount()).isZero();
        assertThat(result.discountAmount()).isEqualTo(520_000);
    }

    @Test
    void 얼리버드2차_4개월은_묶음할인52만원_플랜할인20만원() {
        최초결제_사용자();

        var result = service().calculatePricing(USER_ID, EnrollmentPlan.EARLY_BIRD_2, 4);

        assertThat(result.bundleDiscount()).isEqualTo(520_000);
        assertThat(result.planDiscount()).isEqualTo(200_000);
        assertThat(result.discountAmount()).isEqualTo(720_000);
    }

    // ===== 개월 수에 따른 변화 =====

    @Test
    void 즉시시작_1개월은_묶음할인이_없다() {
        최초결제_사용자();

        var result = service().calculatePricing(USER_ID, EnrollmentPlan.IMMEDIATE, 1);

        assertThat(result.bundleDiscount()).isZero();
        assertThat(result.finalAmount()).isEqualTo(1_300_000);
    }

    @Test
    void 얼리버드2차_플랜할인은_개월수에_비례한다() {
        최초결제_사용자();

        var result = service().calculatePricing(USER_ID, EnrollmentPlan.EARLY_BIRD_2, 1);

        assertThat(result.planDiscount()).isEqualTo(50_000);
        assertThat(result.finalAmount()).isEqualTo(1_250_000);
    }

    @Test
    void 즉시시작_3개월은_묶음할인_5퍼센트() {
        최초결제_사용자();

        var result = service().calculatePricing(USER_ID, EnrollmentPlan.IMMEDIATE, 3);

        assertThat(result.rawTotal()).isEqualTo(3_900_000);
        assertThat(result.bundleDiscount()).isEqualTo(195_000);
        assertThat(result.finalAmount()).isEqualTo(3_705_000);
    }

    // ===== 연장 회차에 따른 단가 사다리 =====

    @Test
    void 연장1회차는_최초와_같은_단가() {
        when(paymentRepository.countByUserIdAndStatus(USER_ID, PaymentStatus.CONFIRMED)).thenReturn(1L);

        var result = service().calculatePricing(USER_ID, EnrollmentPlan.IMMEDIATE, 1);

        assertThat(result.renewalCount()).isEqualTo(1);
        assertThat(result.unitPrice()).isEqualTo(1_300_000);
    }

    @Test
    void 연장2회차는_단가가_10퍼센트_낮다() {
        when(paymentRepository.countByUserIdAndStatus(USER_ID, PaymentStatus.CONFIRMED)).thenReturn(2L);

        var result = service().calculatePricing(USER_ID, EnrollmentPlan.IMMEDIATE, 1);

        assertThat(result.unitPrice()).isEqualTo(1_170_000);
    }

    @Test
    void 연장3회차부터는_단가가_20퍼센트_낮다() {
        when(paymentRepository.countByUserIdAndStatus(USER_ID, PaymentStatus.CONFIRMED)).thenReturn(3L);

        var result = service().calculatePricing(USER_ID, EnrollmentPlan.IMMEDIATE, 1);

        assertThat(result.unitPrice()).isEqualTo(1_040_000);
    }

    // ===== createPayment 가 같은 정책으로 금액을 확정하는지 =====

    @Test
    void 결제생성은_미리보기와_같은_금액을_저장한다() {
        최초결제_사용자();
        when(applicationRepository.findById(11L)).thenReturn(Optional.of(Application.builder().build()));
        when(paymentRepository.existsByApplicationId(11L)).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new PaymentCreateRequest(11L, "EARLY_BIRD_2", 4, 0);
        service().createPayment(USER_ID, request);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(4_480_000);
        assertThat(captor.getValue().getDiscountApplied()).isEqualTo(720_000);
        assertThat(captor.getValue().getCourseType()).isEqualTo("EARLY_BIRD_2");
    }

    // ===== 미리보기: 프론트 플랜 카드가 그대로 받아 쓰는 목록 =====

    @Test
    void 미리보기는_모든_플랜을_카드_노출_순서대로_돌려준다() {
        최초결제_사용자();

        var plans = service().previewAllPlans(USER_ID, 4);

        assertThat(plans).extracting(PaymentService.PricingResult::plan)
                .containsExactly(EnrollmentPlan.IMMEDIATE, EnrollmentPlan.EARLY_BIRD_1, EnrollmentPlan.EARLY_BIRD_2);
        assertThat(plans).extracting(PaymentService.PricingResult::finalAmount)
                .containsExactly(4_680_000, 4_580_000, 4_480_000);
    }

    @Test
    void 비로그인_미리보기는_최초결제_기준가를_돌려준다() {
        var plans = service().previewAllPlans(null, 4);

        assertThat(plans).extracting(PaymentService.PricingResult::renewalCount)
                .containsOnly(0);
        assertThat(plans.get(0).finalAmount()).isEqualTo(4_680_000);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void 알_수_없는_플랜으로는_결제를_생성할_수_없다() {
        var request = new PaymentCreateRequest(11L, "MEGA_SALE", 4, 0);

        assertThatThrownBy(() -> service().createPayment(USER_ID, request))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessageContaining("수강 방식");
    }

    // ===== 승인: toss-confirm-enabled 플래그 =====

    private Payment 승인대기_결제(int amount) {
        return Payment.builder()
                .userId(USER_ID)
                .applicationId(11L)
                .orderId("DEVMATCH-TEST1234")
                .amount(amount)
                .build();
    }

    @Test
    void 플래그가_꺼져_있으면_토스를_호출하지_않고_MOCK_접두사로_승인한다() {
        Payment payment = 승인대기_결제(4_680_000);
        when(paymentRepository.findByOrderId("DEVMATCH-TEST1234")).thenReturn(Optional.of(payment));

        var request = new PaymentConfirmRequest("tviva20250101", "DEVMATCH-TEST1234", 4_680_000);
        service(false).confirmPayment(USER_ID, request);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(payment.getPaymentKey()).isEqualTo("MOCK-tviva20250101");
        verifyNoInteractions(tossPaymentService);
    }

    @Test
    void 플래그가_켜져_있으면_토스_승인을_호출하고_원본_키를_저장한다() {
        Payment payment = 승인대기_결제(4_680_000);
        when(paymentRepository.findByOrderId("DEVMATCH-TEST1234")).thenReturn(Optional.of(payment));
        when(tossPaymentService.confirmPayment("tviva20250101", "DEVMATCH-TEST1234", 4_680_000))
                .thenReturn(true);

        var request = new PaymentConfirmRequest("tviva20250101", "DEVMATCH-TEST1234", 4_680_000);
        service(true).confirmPayment(USER_ID, request);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(payment.getPaymentKey()).isEqualTo("tviva20250101");
        verify(tossPaymentService).confirmPayment("tviva20250101", "DEVMATCH-TEST1234", 4_680_000);
    }

    @Test
    void 금액이_다르면_플래그가_꺼져_있어도_승인되지_않는다() {
        Payment payment = 승인대기_결제(4_680_000);
        when(paymentRepository.findByOrderId("DEVMATCH-TEST1234")).thenReturn(Optional.of(payment));

        // 프론트가 조작된(혹은 낡은) 금액을 보낸 상황
        var request = new PaymentConfirmRequest("tviva20250101", "DEVMATCH-TEST1234", 3_564_000);

        assertThatThrownBy(() -> service(false).confirmPayment(USER_ID, request))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessageContaining("금액이 일치하지 않습니다");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verifyNoInteractions(tossPaymentService);
    }

    // ===== 동시성 · 멱등성 · 대기열 (PR #78) =====

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
        // 서비스를 먼저 만든 뒤 락 실패로 덮어쓴다 (헬퍼가 락을 성공으로 스텁하므로 순서가 중요).
        PaymentService svc = service();
        when(distributedLock.tryLock(anyString(), any(Duration.class))).thenReturn(null);

        assertThatThrownBy(() -> svc.confirmPayment(10L,
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
