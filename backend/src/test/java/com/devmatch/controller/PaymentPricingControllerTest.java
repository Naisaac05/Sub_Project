package com.devmatch.controller;

import com.devmatch.entity.EnrollmentPlan;
import com.devmatch.entity.Role;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.PaymentService;
import com.devmatch.service.PaymentService.PricingResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 결제 플랜 가격 미리보기 엔드포인트.
 * 멘토 상세/결제 페이지가 로그인 전에도 카드 가격을 그려야 하므로 공개 접근이어야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentPricingControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean PaymentService paymentService;

    private PricingResult sample(EnrollmentPlan plan, int finalAmount) {
        return new PricingResult(plan, 1_300_000, 4, 0, 5_200_000, 0.10, 520_000,
                plan.discountFor(4), 520_000 + plan.discountFor(4), finalAmount);
    }

    @Test
    void 비로그인도_플랜_가격을_조회할_수_있다() throws Exception {
        when(paymentService.previewAllPlans(isNull(), eq(4))).thenReturn(List.of(
                sample(EnrollmentPlan.IMMEDIATE, 4_680_000),
                sample(EnrollmentPlan.EARLY_BIRD_1, 4_580_000),
                sample(EnrollmentPlan.EARLY_BIRD_2, 4_480_000)
        ));

        mvc.perform(get("/api/payments/pricing").param("months", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].plan").value("IMMEDIATE"))
                .andExpect(jsonPath("$.data[0].finalAmount").value(4_680_000))
                .andExpect(jsonPath("$.data[2].finalAmount").value(4_480_000));
    }

    @Test
    void 로그인_사용자는_본인_연장회차_기준가를_받는다() throws Exception {
        when(paymentService.previewAllPlans(eq(42L), eq(4)))
                .thenReturn(List.of(sample(EnrollmentPlan.IMMEDIATE, 4_680_000)));

        mvc.perform(get("/api/payments/pricing").param("months", "4")
                        .with(user(new CustomUserDetails(42L, "mentee@test", Role.MENTEE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].finalAmount").value(4_680_000));
    }

    @Test
    void months_기본값은_4개월() throws Exception {
        when(paymentService.previewAllPlans(isNull(), eq(4)))
                .thenReturn(List.of(sample(EnrollmentPlan.IMMEDIATE, 4_680_000)));

        mvc.perform(get("/api/payments/pricing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].plan").value("IMMEDIATE"));
    }
}
