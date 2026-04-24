package com.devmatch.dto.admin.payment;

import com.devmatch.entity.PaymentStatus;

import java.time.LocalDate;

public record AdminPaymentFilter(
        PaymentStatus status, // null = ALL
        String q,             // null/blank 허용
        LocalDate from,       // null 이면 서비스 레이어에서 기본 최근 30일 적용
        LocalDate to          // null 이면 today
) {}
