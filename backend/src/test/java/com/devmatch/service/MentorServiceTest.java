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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class MentorServiceTest {

    @Mock private MentorProfileRepository mentorProfileRepository;
    @Mock private UserRepository userRepository;
    @Mock private CourseService courseService;
    @Mock private MentorProfileHistoryRepository historyRepository;
    @Mock private com.devmatch.service.AdminAuditLogService adminAuditLogService;
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

    @Test
    void approve_성공시_멘토프로필_승인되고_감사로그가_기록된다() {
        User user = User.builder().id(5L).email("m@test").name("멘토").build();
        MentorProfile profile = MentorProfile.builder()
                .user(user).status(MentorStatus.PENDING).courses(new HashSet<>()).build();
        org.springframework.test.util.ReflectionTestUtils.setField(profile, "id", 42L);

        when(mentorProfileRepository.findById(42L)).thenReturn(Optional.of(profile));
        when(historyRepository.findTopByUserIdOrderBySubmittedAtDesc(5L))
                .thenReturn(Optional.empty());

        mentorService.approve(42L, 1L);

        assertThat(profile.getStatus()).isEqualTo(MentorStatus.APPROVED);
        verify(adminAuditLogService).record(
                eq(1L),
                eq(com.devmatch.entity.AdminActionType.MENTOR_APPROVE),
                eq("MENTOR_PROFILE"),
                eq(42L),
                isNull(),
                isNull());
    }

    @Test
    void reject_성공시_멘토프로필_반려되고_사유와함께_감사로그가_기록된다() {
        User user = User.builder().id(5L).email("m@test").name("멘토").build();
        MentorProfile profile = MentorProfile.builder()
                .user(user).status(MentorStatus.PENDING).courses(new HashSet<>()).build();
        org.springframework.test.util.ReflectionTestUtils.setField(profile, "id", 42L);

        when(mentorProfileRepository.findById(42L)).thenReturn(Optional.of(profile));
        when(historyRepository.findTopByUserIdOrderBySubmittedAtDesc(5L))
                .thenReturn(Optional.empty());

        mentorService.reject(42L, 1L, "경력 증빙 부족");

        assertThat(profile.getStatus()).isEqualTo(MentorStatus.REJECTED);
        verify(adminAuditLogService).record(
                eq(1L),
                eq(com.devmatch.entity.AdminActionType.MENTOR_REJECT),
                eq("MENTOR_PROFILE"),
                eq(42L),
                eq("경력 증빙 부족"),
                isNull());
    }

    @Test
    void approve_이미_승인된_프로필은_예외를_던지고_감사로그는_기록되지_않는다() {
        MentorProfile profile = MentorProfile.builder()
                .status(MentorStatus.APPROVED).build();
        when(mentorProfileRepository.findById(42L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> mentorService.approve(42L, 1L))
                .isInstanceOf(com.devmatch.exception.InvalidMentorReviewStateException.class);

        verify(adminAuditLogService, never()).record(any(), any(), any(), any(), any(), any());
    }
}
