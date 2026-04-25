package com.devmatch.service;

import com.devmatch.dto.admin.dashboard.AdminDashboardResponse;
import com.devmatch.entity.MatchingStatus;
import com.devmatch.entity.MentorStatus;
import com.devmatch.entity.PaymentStatus;
import com.devmatch.entity.UserStatus;
import com.devmatch.repository.AdminAuditLogRepository;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.MentorProfileRepository;
import com.devmatch.repository.PaymentRepository;
import com.devmatch.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock UserRepository userRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock MatchingRepository matchingRepository;
    @Mock MentorProfileRepository mentorProfileRepository;
    @Mock AdminAuditLogRepository auditLogRepository;

    @InjectMocks AdminDashboardService service;

    @Test
    void summary_kpi_delta_percent_is_null_when_last_month_zero() {
        when(userRepository.countByStatus(UserStatus.ACTIVE)).thenReturn(100L);
        // 이번달 신규 = 이번달 구간 count, 지난달 신규 = 지난달 구간 count = 0
        when(userRepository.countByStatusAndCreatedAtBetween(eq(UserStatus.ACTIVE), any(), any()))
                .thenReturn(10L)   // 이번달
                .thenReturn(0L);   // 지난달
        // 나머지 쿼리 기본값 (0)
        when(paymentRepository.sumAmountByStatusAndCreatedBetween(eq(PaymentStatus.CONFIRMED), any(), any()))
                .thenReturn(0L);
        when(paymentRepository.sumAmountByStatusAndCancelledBetween(eq(PaymentStatus.CANCELLED), any(), any()))
                .thenReturn(0L);
        when(matchingRepository.countByStatus(MatchingStatus.ACCEPTED)).thenReturn(0L);
        when(matchingRepository.countByStatusAndCreatedAtBetween(eq(MatchingStatus.ACCEPTED), any(), any()))
                .thenReturn(0L);
        when(mentorProfileRepository.countByStatus(MentorStatus.APPROVED)).thenReturn(0L);
        when(mentorProfileRepository.countByStatus(MentorStatus.PENDING)).thenReturn(0L);
        when(paymentRepository.countByStatus(PaymentStatus.FAILED)).thenReturn(0L);
        when(userRepository.findDailySignupsSince(any())).thenReturn(List.of());

        AdminDashboardResponse res = service.getSummary();

        assertThat(res.kpi().totalActiveUsers().deltaFromLastMonth()).isEqualTo(10L);
        assertThat(res.kpi().totalActiveUsers().deltaPercent()).isNull();
    }

    @Test
    void summary_net_revenue_is_confirmed_minus_cancelled_in_window() {
        stubZeroBase();
        when(paymentRepository.sumAmountByStatusAndCreatedBetween(eq(PaymentStatus.CONFIRMED), any(), any()))
                .thenReturn(1_000_000L)  // 이번달 gross
                .thenReturn(800_000L);   // 지난달 gross (MTD 계산용)
        when(paymentRepository.sumAmountByStatusAndCancelledBetween(eq(PaymentStatus.CANCELLED), any(), any()))
                .thenReturn(100_000L)    // 이번달 환불
                .thenReturn(50_000L);    // 지난달 환불

        AdminDashboardResponse res = service.getSummary();

        // 이번달 순매출 = 1,000,000 - 100,000 = 900,000
        assertThat(res.kpi().currentMonthRevenue().current()).isEqualTo(900_000L);
        // delta = 900,000 - (800,000 - 50,000) = 900,000 - 750,000 = 150,000
        assertThat(res.kpi().currentMonthRevenue().deltaFromLastMonth()).isEqualTo(150_000L);
    }

    @Test
    void auditLogFeed_formats_description_for_each_action_type() {
        var admin = com.devmatch.entity.User.builder()
                .id(1L).email("a@d").name("관리자A")
                .role(com.devmatch.entity.Role.ADMIN)
                .status(UserStatus.ACTIVE).build();
        var log = com.devmatch.entity.AdminAuditLog.builder()
                .id(7L).adminId(1L)
                .actionType(com.devmatch.entity.AdminActionType.PAYMENT_REFUND)
                .targetType("PAYMENT").targetId(123L).reason("중복")
                .metadata(null).createdAt(LocalDateTime.of(2026,4,24,9,0)).build();
        when(auditLogRepository.findTop10ByOrderByCreatedAtDesc()).thenReturn(List.of(log));
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(admin));

        var res = service.getAuditLogFeed();

        assertThat(res.items()).hasSize(1);
        var item = res.items().get(0);
        assertThat(item.adminName()).isEqualTo("관리자A");
        assertThat(item.description()).isEqualTo("결제 #123 환불");
        assertThat(item.targetHref()).isEqualTo("/admin/payments/123");
    }

    @Test
    void auditLogFeed_falls_back_when_admin_deleted() {
        var log = com.devmatch.entity.AdminAuditLog.builder()
                .id(8L).adminId(999L)
                .actionType(com.devmatch.entity.AdminActionType.POST_DELETE)
                .targetType("POST").targetId(45L).createdAt(LocalDateTime.now()).build();
        when(auditLogRepository.findTop10ByOrderByCreatedAtDesc()).thenReturn(List.of(log));
        when(userRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        var res = service.getAuditLogFeed();

        assertThat(res.items().get(0).adminName()).isEqualTo("(삭제된 관리자)");
        assertThat(res.items().get(0).description()).isEqualTo("게시물 #45 삭제");
        assertThat(res.items().get(0).targetHref()).isEqualTo("/admin/posts/45");
    }

    private void stubZeroBase() {
        when(userRepository.countByStatus(any())).thenReturn(0L);
        when(userRepository.countByStatusAndCreatedAtBetween(any(), any(), any())).thenReturn(0L);
        when(matchingRepository.countByStatus(any())).thenReturn(0L);
        when(matchingRepository.countByStatusAndCreatedAtBetween(any(), any(), any())).thenReturn(0L);
        when(mentorProfileRepository.countByStatus(any())).thenReturn(0L);
        when(paymentRepository.countByStatus(any())).thenReturn(0L);
        when(userRepository.findDailySignupsSince(any())).thenReturn(List.of());
    }
}
