package com.devmatch.service;

import com.devmatch.dto.payment.PaymentConfirmRequest;
import com.devmatch.dto.payment.PaymentCreateRequest;
import com.devmatch.dto.payment.PaymentResponse;
import com.devmatch.entity.Application;
import com.devmatch.entity.Payment;
import com.devmatch.entity.PaymentStatus;
import com.devmatch.exception.DuplicatePaymentException;
import com.devmatch.exception.PaymentFailedException;
import com.devmatch.exception.PaymentInProgressException;
import com.devmatch.exception.PaymentNotFoundException;
import com.devmatch.exception.QueueNotAdmittedException;
import com.devmatch.repository.ApplicationRepository;
import com.devmatch.repository.PaymentRepository;
import com.devmatch.support.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ApplicationRepository applicationRepository;
    private final TossPaymentService tossPaymentService;
    private final ApplicationService applicationService;
    private final DistributedLock distributedLock;
    private final WaitingQueueService waitingQueueService;

    // 결제 승인 분산 락 TTL — 토스 응답 지연을 넉넉히 덮되, 홀더 크래시 시 자동 해제되도록 짧게.
    private static final Duration CONFIRM_LOCK_TTL = Duration.ofSeconds(10);
    private static final String CONFIRM_LOCK_PREFIX = "pay:confirm:lock:";

    // ===== 가격 정책 상수 =====
    private static final int BASE_PRICE = 990_000;           // 기본 1개월 가격: 99만원
    private static final int FIRST_RENEWAL_PRICE = 990_000;  // 1회 연장: 99만원
    private static final int SECOND_RENEWAL_PRICE = 890_000; // 2회 연장: 89만원
    private static final int MAX_RENEWAL_PRICE = 790_000;    // 3회+ 연장: 79만원

    // 묶음 할인율 (3개월 이상)
    private static final double BUNDLE_3_DISCOUNT = 0.05;   // 3개월: 5%
    private static final double BUNDLE_4_DISCOUNT = 0.10;   // 4개월: 10%
    private static final double BUNDLE_5_DISCOUNT = 0.15;   // 5개월: 15%
    private static final double BUNDLE_6_PLUS_DISCOUNT = 0.20; // 6개월+: 20%

    /**
     * 결제 가격 미리보기 (결제 생성 전 가격 확인용)
     * 프론트엔드에서 개월 수 슬라이더 조절 시 호출하여 실시간 가격을 보여줍니다.
     */
    public PricingResult calculatePricing(Long userId, int monthsBundled) {
        long confirmedCount = paymentRepository.countByUserIdAndStatus(userId, PaymentStatus.CONFIRMED);
        int renewalCount = (int) confirmedCount;

        int unitPrice = getUnitPrice(renewalCount);
        int rawTotal = unitPrice * monthsBundled;
        double discountRate = getBundleDiscountRate(monthsBundled);
        int discountAmount = (int) Math.round(rawTotal * discountRate);
        int finalAmount = rawTotal - discountAmount;

        return new PricingResult(unitPrice, monthsBundled, renewalCount, discountRate, discountAmount, finalAmount);
    }

    /**
     * 결제 생성 (PENDING 상태)
     * 프로세스: 신청서 작성 → 결제 생성 → 토스 SDK 호출 → 결제 승인
     */
    @Transactional
    public PaymentResponse createPayment(Long userId, PaymentCreateRequest request) {
        // 중복 결제 확인
        if (paymentRepository.existsByApplicationId(request.getApplicationId())) {
            throw new DuplicatePaymentException("이미 해당 신청서에 대한 결제가 존재합니다");
        }

        // 신청서 존재 확인
        Application application = applicationRepository.findById(request.getApplicationId())
                .orElseThrow(() -> new PaymentFailedException("신청서를 찾을 수 없습니다: " + request.getApplicationId()));

        // 연장 회차 자동 계산
        long confirmedCount = paymentRepository.countByUserIdAndStatus(userId, PaymentStatus.CONFIRMED);
        int renewalCount = (int) confirmedCount;

        // 동적 금액 계산
        int months = request.getMonthsBundled() != null ? request.getMonthsBundled() : 1;
        int unitPrice = getUnitPrice(renewalCount);
        int rawTotal = unitPrice * months;
        double discountRate = getBundleDiscountRate(months);
        int discountAmount = (int) Math.round(rawTotal * discountRate);
        int finalAmount = rawTotal - discountAmount;

        // orderId 자동 생성
        String orderId = "DEVMATCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Payment payment = Payment.builder()
                .userId(userId)
                .applicationId(request.getApplicationId())
                .orderId(orderId)
                .amount(finalAmount)
                .courseType(request.getCourseType())
                .monthsBundled(months)
                .renewalCount(renewalCount)
                .discountApplied(discountAmount)
                .installmentMonths(request.getInstallmentMonths() != null ? request.getInstallmentMonths() : 0)
                .build();

        Payment saved;
        try {
            saved = paymentRepository.save(payment);
            paymentRepository.flush(); // 유니크 제약 위반을 이 지점에서 즉시 검출
        } catch (DataIntegrityViolationException e) {
            // existsByApplicationId 선검사를 통과한 동시 요청이 여기서 유니크 제약(application_id)에 막힌 경우.
            // check-then-act 경쟁의 최후 방어 — 두 번째 INSERT 는 DB 가 물리적으로 거부한다.
            throw new DuplicatePaymentException("이미 해당 신청서에 대한 결제가 존재합니다");
        }

        // 신청서 상태 업데이트
        application.markPaid();

        log.info("[Payment] 결제 생성 — orderId: {}, amount: {} (할인: {}원, 연장 {}회차, {}개월)",
                orderId, finalAmount, discountAmount, renewalCount, months);
        return PaymentResponse.from(saved);
    }

    /**
     * 결제 승인 (토스페이먼츠 API 호출).
     *
     * <p><b>3중 중복 방어:</b>
     * <ol>
     *   <li><b>분산 락</b>({@code pay:confirm:lock:{orderId}}) — 같은 주문의 동시 요청(버튼 더블클릭 등)을
     *       직렬화한다. 두 번째 요청은 즉시 {@link PaymentInProgressException}(409) 로 컷.</li>
     *   <li><b>멱등성(status) 체크</b> — 이미 {@code CONFIRMED} 인 결제면 토스 재호출 없이 기존 결과를 그대로 반환.
     *       시간차 재시도(타임아웃 후 재요청)를 흡수한다.</li>
     *   <li><b>DB 유니크 제약</b>(order_id 등) — 위 둘이 뚫려도 물리적으로 중복을 거부.</li>
     * </ol>
     *
     * <p>락은 반드시 <b>트랜잭션 커밋 이후</b>에 해제한다. 커밋 전에 풀면, 대기하던 다른 요청이
     * 아직 반영되지 않은 {@code PENDING} 상태를 읽고 토스를 재호출할 수 있다.
     * (자세한 배경은 {@link DistributedLock} 참고.)
     */
    @Transactional
    public PaymentResponse confirmPayment(Long userId, PaymentConfirmRequest request) {
        // 대기열이 켜져 있으면 입장권 보유자만 통과 (비활성 시 항상 true).
        if (!waitingQueueService.isActive(userId)) {
            throw new QueueNotAdmittedException("대기열 입장 후 결제를 진행해주세요");
        }

        String lockKey = CONFIRM_LOCK_PREFIX + request.getOrderId();
        String lockOwner = distributedLock.tryLock(lockKey, CONFIRM_LOCK_TTL);
        if (lockOwner == null) {
            throw new PaymentInProgressException("이미 처리 중인 결제입니다. 잠시 후 다시 시도해주세요");
        }
        releaseLockAfterCommit(lockKey, lockOwner);

        Payment payment = paymentRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new PaymentNotFoundException("결제 정보를 찾을 수 없습니다: " + request.getOrderId()));

        if (!payment.getUserId().equals(userId)) {
            throw new PaymentFailedException("본인의 결제만 승인할 수 있습니다");
        }

        // 멱등성: 이미 승인된 결제면 토스를 다시 부르지 않고 저장된 결과를 그대로 반환한다.
        if (payment.getStatus() == PaymentStatus.CONFIRMED) {
            log.info("[Payment] 멱등 반환 — 이미 승인된 결제 orderId: {}", request.getOrderId());
            return PaymentResponse.from(payment);
        }

        if (!payment.getAmount().equals(request.getAmount())) {
            payment.fail();
            throw new PaymentFailedException("결제 금액이 일치하지 않습니다. 요청: "
                    + request.getAmount() + ", 실제: " + payment.getAmount());
        }

        boolean confirmed = tossPaymentService.confirmPayment(
                request.getPaymentKey(),
                request.getOrderId(),
                request.getAmount()
        );

        if (confirmed) {
            payment.confirm(request.getPaymentKey());
            applicationService.confirmPayment(userId, payment.getApplicationId());
            log.info("[Payment] 결제 승인 완료 — orderId: {}, paymentKey: {}",
                    request.getOrderId(), request.getPaymentKey());
        } else {
            payment.fail();
            throw new PaymentFailedException("토스페이먼츠 결제 승인에 실패했습니다");
        }

        return PaymentResponse.from(payment);
    }

    /**
     * 분산 락 해제를 트랜잭션 완료(커밋 또는 롤백) 이후로 지연시킨다.
     * 트랜잭션 동기화가 활성인 일반 실행에서는 {@code afterCompletion} 콜백으로,
     * (단위 테스트처럼) 동기화가 없으면 즉시 해제한다. 어느 경우든 TTL 이 최후의 안전망이다.
     */
    private void releaseLockAfterCommit(String lockKey, String lockOwner) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    distributedLock.unlock(lockKey, lockOwner);
                }
            });
        } else {
            distributedLock.unlock(lockKey, lockOwner);
        }
    }

    // [보안] 사용자向 결제 취소(cancelPayment)는 제거되었습니다.
    // 기존 구현은 toss-cancel-enabled 플래그·관리자 권한·감사로그를 모두 우회하고
    // 실제 Toss 환불 API 를 직접 호출했습니다. 모든 환불은 AdminPaymentService.refundPayment 로만 수행합니다.

    /**
     * 내 결제 목록 조회
     */
    public List<PaymentResponse> getMyPayments(Long userId) {
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(PaymentResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 결제 상세 조회
     */
    public PaymentResponse getPayment(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("결제 정보를 찾을 수 없습니다: " + paymentId));

        if (!payment.getUserId().equals(userId)) {
            throw new PaymentFailedException("본인의 결제 정보만 조회할 수 있습니다");
        }

        return PaymentResponse.from(payment);
    }

    // ===== 가격 정책 엔진 (내부 로직) =====

    /**
     * 연장 회차에 따른 월 단가 산정
     * 0회(최초): 99만원 → 1회 연장: 99만원 → 2회 연장: 89만원 → 3회+: 79만원
     */
    private int getUnitPrice(int renewalCount) {
        if (renewalCount == 0) return BASE_PRICE;
        if (renewalCount == 1) return FIRST_RENEWAL_PRICE;
        if (renewalCount == 2) return SECOND_RENEWAL_PRICE;
        return MAX_RENEWAL_PRICE;
    }

    /**
     * 묶음 결제 할인율
     * 1~2개월: 0% → 3개월: 5% → 4개월: 10% → 5개월: 15% → 6개월+: 20%
     */
    private double getBundleDiscountRate(int months) {
        if (months < 3) return 0.0;
        if (months == 3) return BUNDLE_3_DISCOUNT;
        if (months == 4) return BUNDLE_4_DISCOUNT;
        if (months == 5) return BUNDLE_5_DISCOUNT;
        return BUNDLE_6_PLUS_DISCOUNT;
    }

    /**
     * 가격 계산 결과 DTO (내부용)
     */
    public record PricingResult(
            int unitPrice,
            int monthsBundled,
            int renewalCount,
            double discountRate,
            int discountAmount,
            int finalAmount
    ) {}
}
