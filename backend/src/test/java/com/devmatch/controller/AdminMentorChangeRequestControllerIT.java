package com.devmatch.controller;

import com.devmatch.entity.*;
import com.devmatch.repository.*;
import com.devmatch.security.CustomUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminMentorChangeRequestControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired MatchingRepository matchingRepository;
    @Autowired MentorChangeRequestRepository requestRepository;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;

    private CustomUserDetails adminPrincipal() {
        return new CustomUserDetails(99L, "admin@test", Role.ADMIN);
    }

    @Test
    void reject_정상_REJECTED_로_변경() throws Exception {
        User mentee = userRepository.save(User.builder()
                .email("m-it-" + System.nanoTime() + "@x.com")
                .name("멘티")
                .password(passwordEncoder.encode("p"))
                .role(Role.MENTEE)
                .status(UserStatus.ACTIVE)
                .build());
        User mentor = userRepository.save(User.builder()
                .email("t-it-" + System.nanoTime() + "@x.com")
                .name("멘토")
                .password(passwordEncoder.encode("p"))
                .role(Role.MENTOR)
                .status(UserStatus.ACTIVE)
                .build());
        Matching matching = matchingRepository.save(Matching.builder()
                .mentee(mentee)
                .mentor(mentor)
                .category("Java BE")
                .status(MatchingStatus.ACCEPTED)
                .build());
        MentorChangeRequest req = requestRepository.save(MentorChangeRequest.builder()
                .menteeId(mentee.getId())
                .currentMatchingId(matching.getId())
                .currentMentorId(mentor.getId())
                .reason("스타일 안 맞음")
                .status(MentorChangeRequestStatus.PENDING)
                .build());

        mockMvc.perform(post("/api/admin/mentor-change-requests/" + req.getId() + "/reject")
                        .with(user(adminPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("rejectReason", "객관적 사유 부족"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("객관적 사유 부족"));

        MentorChangeRequest updated = requestRepository.findById(req.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(MentorChangeRequestStatus.REJECTED);
    }
}
