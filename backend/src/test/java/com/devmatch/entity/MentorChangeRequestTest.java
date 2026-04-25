package com.devmatch.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MentorChangeRequestTest {

    private MentorChangeRequest pending() {
        return MentorChangeRequest.builder()
                .menteeId(1L)
                .currentMatchingId(10L)
                .currentMentorId(20L)
                .reason("멘토 스타일이 맞지 않습니다")
                .status(MentorChangeRequestStatus.PENDING)
                .build();
    }

    @Test
    void approve_정상_상태와_새멘토ID_세팅() {
        MentorChangeRequest r = pending();
        r.approve(99L, 33L);
        assertThat(r.getStatus()).isEqualTo(MentorChangeRequestStatus.APPROVED);
        assertThat(r.getDecidedByAdminId()).isEqualTo(99L);
        assertThat(r.getNewMentorId()).isEqualTo(33L);
        assertThat(r.getRespondedAt()).isNotNull();
    }

    @Test
    void reject_정상_사유_저장() {
        MentorChangeRequest r = pending();
        r.reject(99L, "객관적 사유가 부족합니다");
        assertThat(r.getStatus()).isEqualTo(MentorChangeRequestStatus.REJECTED);
        assertThat(r.getRejectReason()).isEqualTo("객관적 사유가 부족합니다");
        assertThat(r.getRespondedAt()).isNotNull();
    }

    @Test
    void reject_사유_빈값_예외() {
        MentorChangeRequest r = pending();
        assertThatThrownBy(() -> r.reject(99L, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancel_정상() {
        MentorChangeRequest r = pending();
        r.cancel();
        assertThat(r.getStatus()).isEqualTo(MentorChangeRequestStatus.CANCELLED);
        assertThat(r.getRespondedAt()).isNotNull();
    }

    @Test
    void approve_PENDING_아니면_예외() {
        MentorChangeRequest r = pending();
        r.cancel();
        assertThatThrownBy(() -> r.approve(99L, 33L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reject_PENDING_아니면_예외() {
        MentorChangeRequest r = pending();
        r.approve(99L, 33L);
        assertThatThrownBy(() -> r.reject(99L, "사유"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancel_PENDING_아니면_예외() {
        MentorChangeRequest r = pending();
        r.reject(99L, "사유");
        assertThatThrownBy(r::cancel).isInstanceOf(IllegalStateException.class);
    }
}
