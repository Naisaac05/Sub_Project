package com.devmatch.service;

import com.devmatch.dto.lms.CurriculumCreateRequest;
import com.devmatch.dto.lms.CurriculumLimitResponse;
import com.devmatch.entity.Payment;
import com.devmatch.entity.PaymentStatus;
import com.devmatch.exception.CurriculumWeekLimitException;
import com.devmatch.repository.CurriculumRepository;
import com.devmatch.repository.CurriculumWeekRepository;
import com.devmatch.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 결제 개월 수 → 커리큘럼 주차 한도 산정 로직 검증.
 *
 * <p>이 서비스는 오랫동안 테스트가 없었다. 특히 {@code getLimit}/{@code resolveMaxWeeks} 는
 * {@code payment.getMonthsBundled()} 를 언박싱하는데, 과거 스키마에서 이 컬럼이 nullable 이라
 * {@code != null ? ... : 1} 방어 코드로 우회하고 있었다. V4 마이그레이션으로 NOT NULL 이 된 뒤
 * 그 방어 코드를 제거했으므로, 이 테스트가 해당 경로의 회귀를 막는다.
 */
@ExtendWith(MockitoExtension.class)
class CurriculumServiceTest {

    @Mock private CurriculumRepository curriculumRepository;
    @Mock private CurriculumWeekRepository weekRepository;
    @Mock private LmsAccessService lmsAccessService;
    @Mock private PaymentRepository paymentRepository;

    private CurriculumService service() {
        return new CurriculumService(curriculumRepository, weekRepository, lmsAccessService, paymentRepository);
    }

    private Payment payment(PaymentStatus status, int monthsBundled, LocalDateTime createdAt) {
        return Payment.builder()
                .id(1L).userId(10L).applicationId(100L).matchingId(50L)
                .orderId("ord_1").amount(990_000)
                .status(status)
                .monthsBundled(monthsBundled)
                .createdAt(createdAt)
                .build();
    }

    private CurriculumCreateRequest request(int totalWeeks, int weekCount) {
        List<CurriculumCreateRequest.WeekRequest> weeks = java.util.stream.IntStream
                .rangeClosed(1, weekCount)
                .mapToObj(i -> new CurriculumCreateRequest.WeekRequest(
                        i, i + "주차", "설명", List.of(), List.of()))
                .toList();
        return new CurriculumCreateRequest(
                50L, "커리큘럼", "설명", totalWeeks,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 31), null, weeks);
    }

    // ===== getLimit =====

    @Test
    void getLimit_확정결제_3개월이면_최대_12주차() {
        LocalDateTime paidAt = LocalDateTime.of(2026, 7, 20, 10, 0);
        when(paymentRepository.findByMatchingId(50L))
                .thenReturn(Optional.of(payment(PaymentStatus.CONFIRMED, 3, paidAt)));

        CurriculumLimitResponse res = service().getLimit(10L, 50L);

        assertThat(res.getMaxWeeks()).isEqualTo(12);          // 3개월 × 4주
        assertThat(res.getMonthsBundled()).isEqualTo(3);
        assertThat(res.getPaymentDate()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(res.isHasConfirmedPayment()).isTrue();
    }

    @Test
    void getLimit_결제없으면_기본_4주차_폴백() {
        when(paymentRepository.findByMatchingId(50L)).thenReturn(Optional.empty());

        CurriculumLimitResponse res = service().getLimit(10L, 50L);

        assertThat(res.getMaxWeeks()).isEqualTo(4);
        assertThat(res.getMonthsBundled()).isZero();
        assertThat(res.getPaymentDate()).isNull();
        assertThat(res.isHasConfirmedPayment()).isFalse();
    }

    @Test
    void getLimit_미승인_결제는_결제없음으로_취급() {
        when(paymentRepository.findByMatchingId(50L))
                .thenReturn(Optional.of(payment(PaymentStatus.PENDING, 6, LocalDateTime.now())));

        CurriculumLimitResponse res = service().getLimit(10L, 50L);

        assertThat(res.isHasConfirmedPayment()).isFalse();
        assertThat(res.getMaxWeeks()).isEqualTo(4);   // PENDING 은 한도에 반영되지 않는다
    }

    // ===== 주차 한도 강제 (create) =====

    @Test
    void create_결제한도_초과_totalWeeks_는_거부() {
        // 1개월 결제 → 최대 4주차
        when(paymentRepository.findByMatchingId(50L))
                .thenReturn(Optional.of(payment(PaymentStatus.CONFIRMED, 1, LocalDateTime.now())));

        assertThatThrownBy(() -> service().create(10L, request(8, 0)))
                .isInstanceOf(CurriculumWeekLimitException.class)
                .hasMessageContaining("최대 4주차");
    }

    @Test
    void create_결제한도_초과_주차개수_는_거부() {
        when(paymentRepository.findByMatchingId(50L))
                .thenReturn(Optional.of(payment(PaymentStatus.CONFIRMED, 1, LocalDateTime.now())));

        // totalWeeks 는 한도 내지만 실제 주차 목록이 한도를 넘는 경우
        assertThatThrownBy(() -> service().create(10L, request(4, 6)))
                .isInstanceOf(CurriculumWeekLimitException.class)
                .hasMessageContaining("요청한 주차 개수: 6");
    }

    @Test
    void create_한도_이내면_통과() {
        when(paymentRepository.findByMatchingId(50L))
                .thenReturn(Optional.of(payment(PaymentStatus.CONFIRMED, 2, LocalDateTime.now())));
        when(curriculumRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 2개월 → 최대 8주차. 8주차 요청은 경계값으로 허용되어야 한다.
        assertThatCode(() -> service().create(10L, request(8, 8))).doesNotThrowAnyException();
    }

    @Test
    void create_결제없으면_폴백_4주차_한도가_적용된다() {
        when(paymentRepository.findByMatchingId(50L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(10L, request(5, 0)))
                .isInstanceOf(CurriculumWeekLimitException.class)
                .hasMessageContaining("최대 4주차");
    }
}
