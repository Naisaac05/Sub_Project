package com.devmatch.service;

import com.devmatch.dto.mentor.MentorApplyRequest;
import com.devmatch.dto.mentor.MentorProfileResponse;
import com.devmatch.entity.*;
import com.devmatch.exception.AlreadyAppliedException;
import com.devmatch.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MentorServiceTest {

    @Mock private MentorProfileRepository mentorProfileRepository;
    @Mock private UserRepository userRepository;
    @Mock private CourseService courseService;
    @Mock private MentorProfileHistoryRepository historyRepository;
    @InjectMocks private MentorService mentorService;

    private MentorApplyRequest validRequest() {
        MentorApplyRequest req = new MentorApplyRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(req, "courseKeys", List.of("java-backend"));
        org.springframework.test.util.ReflectionTestUtils.setField(req, "careerYears", 5);
        return req;
    }

    @Test
    void apply_신규_신청시_프로필과_이력이_생성된다() {
        User user = User.builder().id(1L).email("m@test").name("멘토").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(mentorProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(courseService.findActiveByKeys(List.of("java-backend")))
                .thenReturn(List.of(MentoringCourse.builder().courseKey("java-backend").title("Java").build()));
        when(mentorProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mentorService.apply(1L, validRequest());

        verify(mentorProfileRepository).save(any(MentorProfile.class));
        verify(historyRepository).save(any(MentorProfileHistory.class));
    }

    @Test
    void apply_REJECTED_상태에서는_프로필_업데이트_후_새_이력_insert() {
        User user = User.builder().id(1L).email("m@test").name("멘토").build();
        MentorProfile existing = MentorProfile.builder()
                .user(user).careerYears(3).status(MentorStatus.REJECTED).courses(new HashSet<>()).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(mentorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(courseService.findActiveByKeys(any()))
                .thenReturn(List.of(MentoringCourse.builder().courseKey("java-backend").build()));

        mentorService.apply(1L, validRequest());

        assertThat(existing.getStatus()).isEqualTo(MentorStatus.PENDING);
        verify(historyRepository).save(any(MentorProfileHistory.class));
        verify(mentorProfileRepository, never()).save(existing);
    }

    @Test
    void apply_PENDING_중복_신청시_AlreadyAppliedException() {
        User user = User.builder().id(1L).build();
        MentorProfile existing = MentorProfile.builder().user(user).status(MentorStatus.PENDING).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(mentorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> mentorService.apply(1L, validRequest()))
                .isInstanceOf(AlreadyAppliedException.class);
    }

    @Test
    void apply_APPROVED_상태에서_재신청_불가() {
        User user = User.builder().id(1L).build();
        MentorProfile existing = MentorProfile.builder().user(user).status(MentorStatus.APPROVED).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(mentorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> mentorService.apply(1L, validRequest()))
                .isInstanceOf(AlreadyAppliedException.class);
    }
}
