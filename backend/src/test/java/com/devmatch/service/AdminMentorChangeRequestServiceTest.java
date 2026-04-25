package com.devmatch.service;

import com.devmatch.dto.admin.menteechange.AdminMentorChangeApproveRequest;
import com.devmatch.entity.*;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.MentorChangeRequestRepository;
import com.devmatch.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminMentorChangeRequestServiceTest {

    @Mock MentorChangeRequestRepository requestRepository;
    @Mock UserRepository userRepository;
    @Mock MatchingRepository matchingRepository;
    @Mock MentorSwapService mentorSwapService;
    @Mock AdminAuditLogService auditLogService;
    @Mock com.devmatch.repository.MentorProfileRepository mentorProfileRepository;

    @InjectMocks AdminMentorChangeRequestService service;

    private MentorChangeRequest pending(Long id, Long menteeId, Long mentorId) {
        MentorChangeRequest r = MentorChangeRequest.builder()
                .menteeId(menteeId).currentMatchingId(10L).currentMentorId(mentorId)
                .reason("스타일 안 맞음").status(MentorChangeRequestStatus.PENDING).build();
        ReflectionTestUtils.setField(r, "id", id);
        return r;
    }

    @Test
    void approve_정상_swap_호출_엔티티_APPROVED_감사로그() {
        MentorChangeRequest r = pending(100L, 7L, 20L);
        when(requestRepository.findById(100L)).thenReturn(Optional.of(r));

        service.approve(99L, 100L, new AdminMentorChangeApproveRequest(33L));

        verify(mentorSwapService).swap(99L, 7L, 33L, "스타일 안 맞음");
        assertThat(r.getStatus()).isEqualTo(MentorChangeRequestStatus.APPROVED);
        assertThat(r.getNewMentorId()).isEqualTo(33L);
        verify(auditLogService).record(
                eq(99L), eq(AdminActionType.MENTOR_CHANGE_APPROVE),
                eq("MENTOR_CHANGE_REQUEST"), eq(100L), eq("스타일 안 맞음"),
                eq(Map.of("newMentorUserId", 33L, "oldMentorUserId", 20L)));
    }

    @Test
    void approve_PENDING_아니면_예외() {
        MentorChangeRequest r = pending(100L, 7L, 20L);
        r.cancel();
        when(requestRepository.findById(100L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.approve(99L, 100L,
                new AdminMentorChangeApproveRequest(33L)))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(mentorSwapService);
    }

    @Test
    void approve_swap_실패시_엔티티_변경_없음() {
        MentorChangeRequest r = pending(100L, 7L, 20L);
        when(requestRepository.findById(100L)).thenReturn(Optional.of(r));
        doThrow(new com.devmatch.exception.ForbiddenOperationException("매칭 없음"))
                .when(mentorSwapService).swap(any(), any(), any(), any());

        assertThatThrownBy(() -> service.approve(99L, 100L,
                new AdminMentorChangeApproveRequest(33L)))
                .isInstanceOf(com.devmatch.exception.ForbiddenOperationException.class);
        assertThat(r.getStatus()).isEqualTo(MentorChangeRequestStatus.PENDING);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void reject_정상_엔티티_REJECTED_감사로그() {
        MentorChangeRequest r = pending(100L, 7L, 20L);
        when(requestRepository.findById(100L)).thenReturn(Optional.of(r));

        service.reject(99L, 100L,
                new com.devmatch.dto.admin.menteechange.AdminMentorChangeRejectRequest("객관적 사유 부족"));

        assertThat(r.getStatus()).isEqualTo(MentorChangeRequestStatus.REJECTED);
        assertThat(r.getRejectReason()).isEqualTo("객관적 사유 부족");
        verify(auditLogService).record(
                eq(99L), eq(AdminActionType.MENTOR_CHANGE_REJECT),
                eq("MENTOR_CHANGE_REQUEST"), eq(100L), eq("객관적 사유 부족"),
                eq(Map.of("menteeReason", "스타일 안 맞음")));
    }
}
