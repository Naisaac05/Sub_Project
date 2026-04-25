package com.devmatch.dto.admin.dashboard;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/admin/dashboard 응답 (ADMIN+).
 */
public record AdminDashboardResponse(
        Kpi kpi,
        List<SignupTrendPoint> signupTrend,
        List<RevenueTrendPoint> revenueTrend,
        Queue queue
) {

    public record Kpi(
            MetricWithDelta totalActiveUsers,
            MetricWithDelta currentMonthRevenue,
            MatchingMetric totalAcceptedMatchings,
            MentorMetric approvedMentors
    ) {}

    /** current: 현재 값, deltaFromLastMonth: 절댓값 차이, deltaPercent: %(지난달 0 이면 null) */
    public record MetricWithDelta(long current, long deltaFromLastMonth, Double deltaPercent) {}

    public record MatchingMetric(long current, long newThisMonth) {}

    public record MentorMetric(long current, long pending) {}

    public record SignupTrendPoint(LocalDate date, long count) {}

    public record RevenueTrendPoint(String month, long grossRevenue, long refundAmount, long netRevenue) {}

    public record Queue(long pendingMentorCount, long failedPaymentCount) {}
}
