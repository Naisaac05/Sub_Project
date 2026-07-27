package com.devmatch.service;

import com.devmatch.dto.payment.PaymentCreateRequest;
import com.devmatch.dto.payment.PaymentResponse;
import com.devmatch.entity.Application;
import com.devmatch.entity.PaymentStatus;
import com.devmatch.entity.Role;
import com.devmatch.entity.User;
import com.devmatch.exception.DuplicatePaymentException;
import com.devmatch.repository.ApplicationRepository;
import com.devmatch.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 결제 생성의 실제 DB 왕복 검증.
 *
 * <p>단위 테스트(Mockito)는 리포지토리를 모킹하므로 <b>스키마 드리프트를 잡지 못한다.</b>
 * 실제로 {@code payments.matching_id} 가 DB 에서 NOT NULL 로 굳어 있어
 * {@code createPayment} 가 항상 {@code Column 'matching_id' cannot be null} 로 실패하는
 * 버그가 있었고(V3 마이그레이션으로 교정), 이 테스트가 그 회귀를 막는다.
 *
 * <p>{@code @Transactional} 로 각 테스트 후 롤백되므로 개발 DB 에 데이터가 남지 않는다.
 */
@SpringBootTest
@Transactional
class PaymentCreationIntegrationTest {

    @Autowired private PaymentService paymentService;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private com.devmatch.config.TossPaymentProperties tossPaymentProperties;

    /**
     * 실결제 차단이 <b>기본값</b>으로 걸려 있는지 고정한다.
     *
     * <p>학생 포트폴리오 정책상 토스 실호출은 금지다. 누군가 설정에서 기본값을 true 로 바꾸면
     * 이 테스트가 깨져서 알려준다 — 안전의 근거를 "아무도 안 건드리겠지"가 아니라 테스트에 둔다.
     */
    @Test
    void 토스_실호출은_승인_취소_모두_기본_차단이어야_한다() {
        assertThat(tossPaymentProperties.tossConfirmEnabled())
                .as("app.payment.toss-confirm-enabled 기본값은 false 여야 한다 (실결제 차단)")
                .isFalse();
        assertThat(tossPaymentProperties.tossCancelEnabled())
                .as("app.payment.toss-cancel-enabled 기본값은 false 여야 한다 (실환불 차단)")
                .isFalse();
    }

    private Application persistApplication() {
        User mentee = userRepository.save(User.builder()
                .email("payment-it-" + System.nanoTime() + "@devmatch.test")
                .password("{noop}test")
                .name("결제통합테스트")
                .role(Role.MENTEE)
                .build());

        return applicationRepository.save(Application.builder()
                .mentee(mentee)
                .currentLevel("BEGINNER")
                .targetTechStack("Spring Boot")
                .careerGoal("백엔드 개발자")
                .category("BACKEND")
                .courseType("IMMEDIATE")
                .desiredMonths(1)
                .build());
    }

    @Test
    void createPayment_matchingId_없이_저장되어야_한다() {
        Application application = persistApplication();

        PaymentResponse res = paymentService.createPayment(
                application.getMentee().getId(),
                new PaymentCreateRequest(application.getId(), "IMMEDIATE", 1, 0));

        // 핵심: matchingId 는 결제 시점에 비어 있어야 하고, 그 상태로 DB 에 저장되어야 한다.
        // (matching_id 가 NOT NULL 이면 여기서 DataIntegrityViolationException 이 난다)
        assertThat(res.getMatchingId()).isNull();
        assertThat(res.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(res.getOrderId()).startsWith("DEVMATCH-");
        // 1개월 IMMEDIATE = 기본 단가(묶음 할인 없음). 가격 정책은 PaymentServiceTest 가 상세 검증한다.
        assertThat(res.getAmount()).isEqualTo(1_300_000);
    }

    @Test
    void createPayment_같은_신청서로_두번_생성하면_거부된다() {
        Application application = persistApplication();
        Long userId = application.getMentee().getId();
        PaymentCreateRequest request = new PaymentCreateRequest(application.getId(), "IMMEDIATE", 1, 0);

        paymentService.createPayment(userId, request);

        // 신청서 1건당 결제 1건 불변식 — 선검사(existsByApplicationId) 또는
        // DB 유니크 제약(uk_payments_application_id) 중 어느 쪽에 걸리든 같은 예외로 수렴한다.
        assertThatThrownBy(() -> paymentService.createPayment(userId, request))
                .isInstanceOf(DuplicatePaymentException.class);
    }
}
