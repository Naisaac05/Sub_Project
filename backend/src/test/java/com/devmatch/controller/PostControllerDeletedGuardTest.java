package com.devmatch.controller;

import com.devmatch.entity.Post;
import com.devmatch.entity.Role;
import com.devmatch.entity.User;
import com.devmatch.entity.UserStatus;
import com.devmatch.repository.PostRepository;
import com.devmatch.repository.UserRepository;
import com.devmatch.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostControllerDeletedGuardTest {

    @Autowired MockMvc mvc;
    @Autowired PostRepository postRepository;
    @Autowired UserRepository userRepository;

    @Test
    void 삭제된_글_단건_조회는_404() throws Exception {
        String uniqueEmail = "deleted-guard-test-u1-" + System.nanoTime() + "@test";
        User author = userRepository.save(User.builder()
                .email(uniqueEmail)
                .password("x")
                .name("U1")
                .role(Role.MENTEE)
                .status(UserStatus.ACTIVE)
                .build());
        Post p = postRepository.save(Post.builder()
                .author(author)
                .title("t")
                .content("c")
                .category("질문")
                .likeCount(0)
                .commentCount(0)
                .viewCount(0)
                .build());
        p.softDelete("삭제된 글 조회 테스트용입니다", 999L);
        postRepository.saveAndFlush(p);

        CustomUserDetails principal = new CustomUserDetails(
                author.getId(), author.getEmail(), author.getRole());

        mvc.perform(get("/api/posts/" + p.getId()).with(user(principal)))
                .andExpect(status().isNotFound());
    }
}
