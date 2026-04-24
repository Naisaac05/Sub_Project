package com.devmatch.entity;

import com.devmatch.exception.AlreadyDeletedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommentSoftDeleteTest {

    private Comment newComment() {
        return Comment.builder()
                .content("c")
                .build();
    }

    @Test
    void softDelete_최초_호출은_4필드를_세팅() {
        Comment c = newComment();

        c.softDelete("욕설이 포함된 댓글이라 삭제합니다", 99L);

        assertThat(c.isDeleted()).isTrue();
        assertThat(c.getDeletionReason()).isEqualTo("욕설이 포함된 댓글이라 삭제합니다");
        assertThat(c.getDeletedBy()).isEqualTo(99L);
        assertThat(c.getDeletedAt()).isNotNull();
    }

    @Test
    void softDelete_이미_삭제된_댓글_재호출은_AlreadyDeletedException() {
        Comment c = newComment();
        c.softDelete("사유1사유1사유1사유1", 1L);

        assertThatThrownBy(() -> c.softDelete("사유2사유2사유2사유2", 2L))
                .isInstanceOf(AlreadyDeletedException.class)
                .hasMessageContaining("이미 삭제된 댓글");
    }
}
