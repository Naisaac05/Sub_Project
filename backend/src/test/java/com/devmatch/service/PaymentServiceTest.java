package com.devmatch.service;

import com.devmatch.dto.payment.PaymentConfirmRequest;
import com.devmatch.entity.Payment;
import com.devmatch.entity.PaymentStatus;
import com.devmatch.repository.ApplicationRepository;
import com.devmatch.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private TossPaymentService tossPaymentService;
    @Mock private ApplicationService applicationService;

    @InjectMocks private PaymentService paymentService;

    @Test
    void confirmPayment_confirmsApplicationAndCreatesAutoMatching() {
        Payment payment = Payment.builder()
                .userId(10L)
                .applicationId(100L)
                .orderId("ORDER-1")
                .amount(4680000)
                .courseType("IMMEDIATE")
                .build();

        when(paymentRepository.findByOrderId("ORDER-1")).thenReturn(Optional.of(payment));
        when(tossPaymentService.confirmPayment("PAYMENT-KEY", "ORDER-1", 4680000)).thenReturn(true);

        paymentService.confirmPayment(10L, new PaymentConfirmRequest("PAYMENT-KEY", "ORDER-1", 4680000));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        verify(applicationService).confirmPayment(10L, 100L);
    }
}
