package com.devmatch.controller;

import com.devmatch.entity.Role;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.AdminPostService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminPostControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    AdminPostService adminPostService;

    private CustomUserDetails menteePrincipal() {
        return new CustomUserDetails(1L, "mentee@test", Role.MENTEE);
    }

    private CustomUserDetails adminPrincipal() {
        return new CustomUserDetails(99L, "admin@test", Role.ADMIN);
    }

    @Test
    void MENTEE_권한은_관리자_목록_조회_시_403() throws Exception {
        mvc.perform(get("/api/admin/posts").with(user(menteePrincipal())))
                .andExpect(status().isForbidden());
    }

    @Test
    void ADMIN_권한은_관리자_목록_조회_시_200() throws Exception {
        Page<com.devmatch.dto.admin.post.AdminPostListItemResponse> empty =
                new PageImpl<>(Collections.emptyList());
        Mockito.when(adminPostService.listPosts(any(), any(Pageable.class)))
                .thenReturn(empty);

        mvc.perform(get("/api/admin/posts").with(user(adminPrincipal())))
                .andExpect(status().isOk());
    }

    @Test
    void ADMIN_권한이지만_사유가_10자_미만이면_400() throws Exception {
        String body = "{\"reason\":\"짧은9자\"}";

        mvc.perform(delete("/api/admin/posts/1")
                        .with(user(adminPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
