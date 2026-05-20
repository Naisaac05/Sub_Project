package com.devmatch.controller;

import com.devmatch.dto.faq.FaqResponse;
import com.devmatch.entity.FaqCategory;
import com.devmatch.entity.Role;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.FaqService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminFaqControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @MockitoBean FaqService service;

    private CustomUserDetails admin() {
        return new CustomUserDetails(99L, "admin@test", Role.ADMIN);
    }

    private CustomUserDetails superAdmin() {
        return new CustomUserDetails(1L, "super@test", Role.SUPER_ADMIN);
    }

    private CustomUserDetails mentee() {
        return new CustomUserDetails(2L, "mentee@test", Role.MENTEE);
    }

    private FaqResponse sample() {
        return new FaqResponse(1L, FaqCategory.SERVICE_INTRO, "Q", "A",
                0, true, LocalDateTime.now(), LocalDateTime.now());
    }

    // ─── 공개 GET ───

    @Test
    void public_get_faqs_works_for_anonymous() throws Exception {
        when(service.listPublic()).thenReturn(List.of());
        mvc.perform(get("/api/faqs")).andExpect(status().isOk());
    }

    // ─── 어드민 GET ───

    @Test
    void admin_can_list() throws Exception {
        when(service.listAll()).thenReturn(List.of());
        mvc.perform(get("/api/admin/faqs").with(user(admin())))
                .andExpect(status().isOk());
    }

    @Test
    void super_admin_can_list() throws Exception {
        when(service.listAll()).thenReturn(List.of());
        mvc.perform(get("/api/admin/faqs").with(user(superAdmin())))
                .andExpect(status().isOk());
    }

    @Test
    void mentee_cannot_list() throws Exception {
        mvc.perform(get("/api/admin/faqs").with(user(mentee())))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_cannot_list_admin() throws Exception {
        mvc.perform(get("/api/admin/faqs")).andExpect(status().isForbidden());
    }

    // ─── 어드민 POST ───

    @Test
    void admin_can_create() throws Exception {
        when(service.create(any())).thenReturn(sample());
        String body = """
                {"category":"SERVICE_INTRO","question":"Q","answer":"A"}
                """;
        mvc.perform(post("/api/admin/faqs").with(user(admin()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void mentee_cannot_create() throws Exception {
        String body = """
                {"category":"SERVICE_INTRO","question":"Q","answer":"A"}
                """;
        mvc.perform(post("/api/admin/faqs").with(user(mentee()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    // ─── 어드민 PUT ───

    @Test
    void admin_can_update() throws Exception {
        when(service.update(eq(1L), any())).thenReturn(sample());
        String body = "{\"published\":false}";
        mvc.perform(put("/api/admin/faqs/1").with(user(admin()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    // ─── 어드민 DELETE ───

    @Test
    void admin_can_delete() throws Exception {
        mvc.perform(delete("/api/admin/faqs/1").with(user(admin())))
                .andExpect(status().isOk());
    }

    @Test
    void mentee_cannot_delete() throws Exception {
        mvc.perform(delete("/api/admin/faqs/1").with(user(mentee())))
                .andExpect(status().isForbidden());
    }
}
