package com.devmatch.controller;

import com.devmatch.dto.admin.dashboard.AdminAuditLogFeedResponse;
import com.devmatch.dto.admin.dashboard.AdminDashboardResponse;
import com.devmatch.entity.Role;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.AdminDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminDashboardControllerTest {

    @Autowired MockMvc mvc;

    @MockBean AdminDashboardService service;

    private CustomUserDetails adminPrincipal() {
        return new CustomUserDetails(99L, "admin@test", Role.ADMIN);
    }

    private CustomUserDetails superAdminPrincipal() {
        return new CustomUserDetails(1L, "super@test", Role.SUPER_ADMIN);
    }

    private CustomUserDetails menteePrincipal() {
        return new CustomUserDetails(2L, "mentee@test", Role.MENTEE);
    }

    @Test
    void admin_can_get_summary() throws Exception {
        when(service.getSummary()).thenReturn(emptySummary());
        mvc.perform(get("/api/admin/dashboard").with(user(adminPrincipal())))
                .andExpect(status().isOk());
    }

    @Test
    void admin_cannot_get_audit_log() throws Exception {
        mvc.perform(get("/api/admin/dashboard/audit-log").with(user(adminPrincipal())))
                .andExpect(status().isForbidden());
    }

    @Test
    void super_admin_can_get_audit_log() throws Exception {
        when(service.getAuditLogFeed()).thenReturn(new AdminAuditLogFeedResponse(List.of()));
        mvc.perform(get("/api/admin/dashboard/audit-log").with(user(superAdminPrincipal())))
                .andExpect(status().isOk());
    }

    @Test
    void mentee_cannot_get_summary() throws Exception {
        mvc.perform(get("/api/admin/dashboard").with(user(menteePrincipal())))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_is_denied() throws Exception {
        // JwtAuthFilter 는 인증 없을 때 SecurityContext 를 비우고,
        // ExceptionTranslationFilter 는 AuthenticationEntryPoint 대신 AccessDeniedHandler 로 403 을 반환한다.
        mvc.perform(get("/api/admin/dashboard")).andExpect(status().isForbidden());
    }

    private AdminDashboardResponse emptySummary() {
        return new AdminDashboardResponse(
                new AdminDashboardResponse.Kpi(
                        new AdminDashboardResponse.MetricWithDelta(0, 0, null),
                        new AdminDashboardResponse.MetricWithDelta(0, 0, null),
                        new AdminDashboardResponse.MatchingMetric(0, 0),
                        new AdminDashboardResponse.MentorMetric(0, 0)
                ),
                List.of(), List.of(),
                new AdminDashboardResponse.Queue(0, 0)
        );
    }
}
