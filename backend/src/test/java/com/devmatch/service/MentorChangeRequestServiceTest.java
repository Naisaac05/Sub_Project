package com.devmatch.service;

import com.devmatch.entity.*;
import com.devmatch.exception.DuplicatePendingMentorChangeRequestException;
import com.devmatch.exception.ForbiddenOperationException;
import com.devmatch.exception.MentorChangeRequestNotFoundException;
import com.devmatch.exception.NoActiveMatchingException;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.MentorChangeRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MentorChangeRequestServiceTest {

    @Mock MatchingRepository matchingRepository;
    @Mock MentorChangeRequestRepository requestRepository;

    @InjectMocks MentorChangeRequestService service;

    private User userOf(Long id) {
        User u = User.builder().email("u").name("u").password("p")
                .role(Role.MENTEE).status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private Matching activeMatching(Long id, Long menteeId, Long mentorId) {
        Matching m = Matching.builder()
                .mentee(userOf(menteeId)).mentor(userOf(mentorId))
                .category("Java BE").status(MatchingStatus.ACCEPTED).build();
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    @Test
    void submit_정상_PENDING_으로_저장() {
        when(matchingRepository.findFirstByMenteeIdAndStatusInOrderByCreatedAtDesc(
                eq(7L), eq(List.of(MatchingStatus.ACCEPTED, MatchingStatus.TRIAL))))
                .thenReturn(Optional.of(activeMatching(10L, 7L, 20L)));
        when(requestRepository.existsByMenteeIdAndStatus(7L, MentorChangeRequestStatus.PENDING))
                .thenReturn(false);
        when(requestRepository.save(any(MentorChangeRequest.class)))
                .thenAnswer(inv -> {
                    MentorChangeRequest r = inv.getArgument(0);
                    ReflectionTestUtils.setField(r, "id", 100L);
                    return r;
                });

        var res = service.submit(7L, "스타일이 맞지 않습니다");

        assertThat(res.id()).isEqualTo(100L);
        assertThat(res.status()).isEqualTo(MentorChangeRequestStatus.PENDING);

        ArgumentCaptor<MentorChangeRequest> cap = ArgumentCaptor.forClass(MentorChangeRequest.class);
        verify(requestRepository).save(cap.capture());
        assertThat(cap.getValue().getCurrentMatchingId()).isEqualTo(10L);
        assertThat(cap.getValue().getCurrentMentorId()).isEqualTo(20L);
        assertThat(cap.getValue().getReason()).isEqualTo("스타일이 맞지 않습니다");
    }

    @Test
    void submit_활성매칭_없음_예외() {
        when(matchingRepository.findFirstByMenteeIdAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(7L, "사유"))
                .isInstanceOf(NoActiveMatchingException.class);
    }

    @Test
    void submit_PENDING_중복_예외() {
        when(matchingRepository.findFirstByMenteeIdAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.of(activeMatching(10L, 7L, 20L)));
        when(requestRepository.existsByMenteeIdAndStatus(7L, MentorChangeRequestStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> service.submit(7L, "사유"))
                .isInstanceOf(DuplicatePendingMentorChangeRequestException.class);
    }

    @Test
    void cancel_정상_엔티티_상태_변경() {
        MentorChangeRequest r = MentorChangeRequest.builder()
                .menteeId(7L).currentMatchingId(10L).currentMentorId(20L)
                .reason("사유").status(MentorChangeRequestStatus.PENDING).build();
        ReflectionTestUtils.setField(r, "id", 100L);
        when(requestRepository.findById(100L)).thenReturn(Optional.of(r));

        service.cancel(7L, 100L);

        assertThat(r.getStatus()).isEqualTo(MentorChangeRequestStatus.CANCELLED);
    }

    @Test
    void cancel_타인_신청_403() {
        MentorChangeRequest r = MentorChangeRequest.builder()
                .menteeId(99L).currentMatchingId(10L).currentMentorId(20L)
                .reason("사유").status(MentorChangeRequestStatus.PENDING).build();
        ReflectionTestUtils.setField(r, "id", 100L);
        when(requestRepository.findById(100L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.cancel(7L, 100L))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void cancel_존재하지_않으면_404() {
        when(requestRepository.findById(100L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.cancel(7L, 100L))
                .isInstanceOf(MentorChangeRequestNotFoundException.class);
    }

    @Test
    void getLatest_없으면_null_반환() {
        when(requestRepository.findFirstByMenteeIdOrderByCreatedAtDesc(7L))
                .thenReturn(Optional.empty());
        assertThat(service.getLatest(7L)).isNull();
    }
}
