package com.devmatch.service;

import com.devmatch.dto.application.ApplicationRequest;
import com.devmatch.dto.application.ApplicationResponse;
import com.devmatch.entity.Application;
import com.devmatch.entity.User;
import com.devmatch.repository.ApplicationRepository;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.MentorProfileRepository;
import com.devmatch.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private UserRepository userRepository;
    @Mock private MatchingRepository matchingRepository;
    @Mock private MentorProfileRepository mentorProfileRepository;

    @InjectMocks private ApplicationService applicationService;

    @Test
    void submitApplication_savesPhoneAndReturnsItForMentorApplicationDetail() {
        User mentee = User.builder().email("m@test.com").name("Mentee").build();
        ReflectionTestUtils.setField(mentee, "id", 10L);

        ApplicationRequest request = ApplicationRequest.builder()
                .currentLevel("BEGINNER")
                .targetTechStack("Java, Spring")
                .careerGoal("Backend")
                .category("java-backend")
                .courseType("IMMEDIATE")
                .desiredMonths(4)
                .languages(List.of("Java"))
                .platforms(List.of("Web"))
                .isCsMajor(false)
                .learningPaths(List.of("Online"))
                .careerYears("0")
                .githubUrl("https://github.com/example")
                .projectCount("1")
                .projectDescription("Project")
                .weekdayStudyHours("Mon 20:00-22:00")
                .weekendStudyHours("Sat 10:00-12:00")
                .goal("Get hired")
                .personality("Detailed mentor")
                .phone("010-1234-5678")
                .selfIntroduction("Hello")
                .referralSources(List.of("Search"))
                .referralCode("")
                .termsAgreed(true)
                .build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(mentee));
        when(applicationRepository.save(any(Application.class))).thenAnswer(invocation -> {
            Application saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });

        ApplicationResponse response = applicationService.submitApplication(10L, request);

        assertThat(response.getPhone()).isEqualTo("010-1234-5678");
    }
}
