package com.devmatch.service;

import com.devmatch.dto.admin.dashboard.AdminDashboardResponse;
import com.devmatch.dto.admin.dashboard.AdminDashboardResponse.*;
import com.devmatch.entity.MatchingStatus;
import com.devmatch.entity.MentorStatus;
import com.devmatch.entity.PaymentStatus;
import com.devmatch.entity.UserStatus;
import com.devmatch.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final MatchingRepository matchingRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final AdminAuditLogRepository auditLogRepository;

    public AdminDashboardResponse getSummary() {
        LocalDateTime nowKst = LocalDateTime.now(KST);
        LocalDate today = nowKst.toLocalDate();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime lastMonthStart = monthStart.minusMonths(1);
        LocalDateTime nowSameTimeLastMonth = lastMonthStart.plus(Duration.between(monthStart, nowKst));

        // KPI ─ 회원
        long totalActive = userRepository.countByStatus(UserStatus.ACTIVE);
        long thisMonthSignups = userRepository.countByStatusAndCreatedAtBetween(
                UserStatus.ACTIVE, monthStart, nowKst);
        long lastMonthSignups = userRepository.countByStatusAndCreatedAtBetween(
                UserStatus.ACTIVE, lastMonthStart, nowSameTimeLastMonth);
        MetricWithDelta usersKpi = deltaMetric(totalActive, thisMonthSignups, lastMonthSignups);

        // KPI ─ 매출
        long thisMonthGross = paymentRepository.sumAmountByStatusAndCreatedBetween(
                PaymentStatus.CONFIRMED, monthStart, nowKst);
        long lastMonthGross = paymentRepository.sumAmountByStatusAndCreatedBetween(
                PaymentStatus.CONFIRMED, lastMonthStart, nowSameTimeLastMonth);
        long thisMonthRefund = paymentRepository.sumAmountByStatusAndCancelledBetween(
                PaymentStatus.CANCELLED, monthStart, nowKst);
        long lastMonthRefund = paymentRepository.sumAmountByStatusAndCancelledBetween(
                PaymentStatus.CANCELLED, lastMonthStart, nowSameTimeLastMonth);
        long thisMonthNet = thisMonthGross - thisMonthRefund;
        long lastMonthNet = lastMonthGross - lastMonthRefund;
        MetricWithDelta revenueKpi = deltaMetric(thisMonthNet, thisMonthNet, lastMonthNet);
        // revenue 는 current 가 곧 이번달 값 (누적 X). delta 계산을 위해 this/last 둘 다 이번달/지난달 값.

        // KPI ─ 매칭
        long totalAccepted = matchingRepository.countByStatus(MatchingStatus.ACCEPTED);
        long newThisMonthAccepted = matchingRepository.countByStatusAndCreatedAtBetween(
                MatchingStatus.ACCEPTED, monthStart, nowKst);
        MatchingMetric matchingKpi = new MatchingMetric(totalAccepted, newThisMonthAccepted);

        // KPI ─ 멘토
        long approvedMentors = mentorProfileRepository.countByStatus(MentorStatus.APPROVED);
        long pendingMentors = mentorProfileRepository.countByStatus(MentorStatus.PENDING);
        MentorMetric mentorKpi = new MentorMetric(approvedMentors, pendingMentors);

        // 차트 1 ─ 일별 신규 가입 (rolling 30일)
        LocalDate from30 = today.minusDays(29);
        List<SignupTrendPoint> signupTrend = buildSignupTrend(from30, today);

        // 차트 2 ─ 월별 순매출 (rolling 12개월)
        List<RevenueTrendPoint> revenueTrend = buildRevenueTrend(today);

        // 처리 큐
        long failedPaymentCount = paymentRepository.countByStatus(PaymentStatus.FAILED);
        AdminDashboardResponse.Queue queue =
                new AdminDashboardResponse.Queue(pendingMentors, failedPaymentCount);

        return new AdminDashboardResponse(
                new Kpi(usersKpi, revenueKpi, matchingKpi, mentorKpi),
                signupTrend,
                revenueTrend,
                queue
        );
    }

    private MetricWithDelta deltaMetric(long current, long thisWindow, long lastWindow) {
        long delta = thisWindow - lastWindow;
        Double percent = (lastWindow == 0) ? null : (delta * 100.0) / lastWindow;
        return new MetricWithDelta(current, delta, percent);
    }

    private List<SignupTrendPoint> buildSignupTrend(LocalDate from, LocalDate today) {
        LocalDateTime fromDt = from.atStartOfDay();
        List<Object[]> rows = userRepository.findDailySignupsSince(fromDt);
        Map<LocalDate, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate d = ((Date) row[0]).toLocalDate();
            long c = ((Number) row[1]).longValue();
            map.put(d, c);
        }
        return IntStream.range(0, 30)
                .mapToObj(i -> {
                    LocalDate d = from.plusDays(i);
                    return new SignupTrendPoint(d, map.getOrDefault(d, 0L));
                })
                .toList();
    }

    private List<RevenueTrendPoint> buildRevenueTrend(LocalDate today) {
        YearMonth current = YearMonth.from(today);
        List<RevenueTrendPoint> out = new ArrayList<>(12);
        for (int i = 11; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            LocalDateTime start = ym.atDay(1).atStartOfDay();
            LocalDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay();
            long gross = paymentRepository.sumAmountByStatusAndCreatedBetween(
                    PaymentStatus.CONFIRMED, start, end);
            long refund = paymentRepository.sumAmountByStatusAndCancelledBetween(
                    PaymentStatus.CANCELLED, start, end);
            out.add(new RevenueTrendPoint(
                    ym.toString(), gross, refund, gross - refund));
        }
        return out;
    }

    public com.devmatch.dto.admin.dashboard.AdminAuditLogFeedResponse getAuditLogFeed() {
        var logs = auditLogRepository.findTop10ByOrderByCreatedAtDesc();
        var items = logs.stream().map(log -> {
            String adminName = userRepository.findById(log.getAdminId())
                    .map(com.devmatch.entity.User::getName)
                    .orElse("(삭제된 관리자)");
            return new com.devmatch.dto.admin.dashboard.AdminAuditLogFeedResponse.Item(
                    log.getId(),
                    adminName,
                    log.getActionType(),
                    formatDescription(log),
                    formatTargetHref(log),
                    log.getCreatedAt()
            );
        }).toList();
        return new com.devmatch.dto.admin.dashboard.AdminAuditLogFeedResponse(items);
    }

    static String formatDescription(com.devmatch.entity.AdminAuditLog log) {
        long id = log.getTargetId();
        return switch (log.getActionType()) {
            case USER_ROLE_CHANGE    -> "회원 #" + id + " 역할 변경";
            case USER_DEACTIVATE     -> "회원 #" + id + " 비활성화";
            case USER_REACTIVATE     -> "회원 #" + id + " 재활성화";
            case USER_DELETE         -> "회원 #" + id + " 삭제";
            case USER_PASSWORD_RESET -> "회원 #" + id + " 비밀번호 초기화";
            case USER_MENTOR_SWAP    -> "회원 #" + id + " 멘토 교체";
            case ADMIN_CREATE        -> "관리자 계정 #" + id + " 생성";
            case PAYMENT_REFUND      -> "결제 #" + id + " 환불";
            case POST_DELETE         -> "게시물 #" + id + " 삭제";
            case COMMENT_DELETE      -> "댓글 #" + id + " 삭제";
            case MENTOR_APPROVE      -> "멘토 #" + id + " 승인";
            case MENTOR_REJECT       -> "멘토 #" + id + " 거절";
        };
    }

    static String formatTargetHref(com.devmatch.entity.AdminAuditLog log) {
        long id = log.getTargetId();
        return switch (log.getTargetType()) {
            case "USER"    -> "/admin/users/" + id;
            case "PAYMENT" -> "/admin/payments/" + id;
            case "POST"    -> "/admin/posts/" + id;
            case "COMMENT" -> "/admin/posts";
            case "MENTOR"  -> "/admin/mentor/" + id;
            case "ADMIN"   -> "/admin/admins";
            default        -> "/admin";
        };
    }
}
