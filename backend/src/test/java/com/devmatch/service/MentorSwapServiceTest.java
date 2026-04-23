package com.devmatch.service;

import com.devmatch.entity.*;
import com.devmatch.exception.ForbiddenOperationException;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.MentorProfileRepository;
import com.devmatch.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MentorSwapServiceTest {

    @Mock UserRepository userRepository;
    @Mock MentorProfileRepository mentorProfileRepository;
    @Mock MatchingRepository matchingRepository;
    @Mock AdminAuditLogService auditLogService;

    @InjectMocks MentorSwapService service;

    private User userOf(Long id, Role role, UserStatus status) {
        User u = User.builder().email("u").name("u").password("p").role(role).status(status).build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private MentorProfile mentorOf(Long profileId, User mentorUser, MentorStatus status) {
        MentorProfile p = MentorProfile.builder().user(mentorUser).status(status).build();
        ReflectionTestUtils.setField(p, "id", profileId);
        return p;
    }

    private Matching matchingOf(Long id, User mentee, User mentor, MatchingStatus status) {
        Matching m = Matching.builder()
                .mentee(mentee).mentor(mentor)
                .category("Java BE")
                .status(status).build();
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    @Test
    void swap_정상_old_SWAPPED_new_생성_감사로그() {
        User mentee = userOf(7L, Role.MENTEE, UserStatus.ACTIVE);
        User oldMentor = userOf(11L, Role.MENTOR, UserStatus.ACTIVE);
        User newMentor = userOf(22L, Role.MENTOR, UserStatus.ACTIVE);
        MentorProfile newMentorProfile = mentorOf(101L, newMentor, MentorStatus.APPROVED);
        Matching old = matchingOf(50L, mentee, oldMentor, MatchingStatus.ACCEPTED);

        when(userRepository.findById(7L)).thenReturn(Optional.of(mentee));
        when(mentorProfileRepository.findByUserId(22L)).thenReturn(Optional.of(newMentorProfile));
        when(matchingRepository.findFirstByMenteeIdAndStatusInOrderByCreatedAtDesc(
                eq(7L), anyCollection())).thenReturn(Optional.of(old));
        when(matchingRepository.save(any(Matching.class))).thenAnswer(inv -> {
            Matching m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 60L);
            return m;
        });

        service.swap(1L, 7L, 22L, "멘티 요청");

        assertThat(old.getStatus()).isEqualTo(MatchingStatus.SWAPPED);

        ArgumentCaptor<Matching> captor = ArgumentCaptor.forClass(Matching.class);
        verify(matchingRepository).save(captor.capture());
        Matching newMatching = captor.getValue();
        assertThat(newMatching.getMentee().getId()).isEqualTo(7L);
        assertThat(newMatching.getMentor().getId()).isEqualTo(22L);
        assertThat(newMatching.getCategory()).isEqualTo("Java BE");

        verify(auditLogService).record(
                eq(1L),
                eq(AdminActionType.USER_MENTOR_SWAP),
                eq("USER"),
                eq(7L),
                eq("멘티 요청"),
                eq(Map.of("oldMatchingId", 50L, "oldMentorUserId", 11L, "newMentorUserId", 22L)));
    }

    @Test
    void swap_새_멘토가_APPROVED가_아니면_차단() {
        User mentee = userOf(7L, Role.MENTEE, UserStatus.ACTIVE);
        User newMentor = userOf(22L, Role.MENTOR, UserStatus.ACTIVE);
        MentorProfile pending = mentorOf(101L, newMentor, MentorStatus.PENDING);
        when(userRepository.findById(7L)).thenReturn(Optional.of(mentee));
        when(mentorProfileRepository.findByUserId(22L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.swap(1L, 7L, 22L, "사유"))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("승인된 멘토");
    }

    @Test
    void swap_활성_매칭_없으면_차단() {
        User mentee = userOf(7L, Role.MENTEE, UserStatus.ACTIVE);
        User newMentor = userOf(22L, Role.MENTOR, UserStatus.ACTIVE);
        MentorProfile newMentorProfile = mentorOf(101L, newMentor, MentorStatus.APPROVED);
        when(userRepository.findById(7L)).thenReturn(Optional.of(mentee));
        when(mentorProfileRepository.findByUserId(22L)).thenReturn(Optional.of(newMentorProfile));
        when(matchingRepository.findFirstByMenteeIdAndStatusInOrderByCreatedAtDesc(
                eq(7L), anyCollection())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.swap(1L, 7L, 22L, "사유"))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("활성 매칭");
    }
}
