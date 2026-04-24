package com.devmatch.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostSoftDeleteTest {

    private Post newPost() {
        return Post.builder()
                .title("t")
                .content("c")
                .category("질문")
                .likeCount(0).commentCount(0).viewCount(0)
                .build();
    }

    @Test
    void softDelete_최초_호출은_4필드를_세팅() {
        Post post = newPost();

        post.softDelete("스팸 광고성 게시물이므로 삭제합니다", 99L);

        assertThat(post.isDeleted()).isTrue();
        assertThat(post.getDeletionReason()).isEqualTo("스팸 광고성 게시물이므로 삭제합니다");
        assertThat(post.getDeletedBy()).isEqualTo(99L);
        assertThat(post.getDeletedAt()).isNotNull();
    }

    @Test
    void softDelete_이미_삭제된_게시물_재호출은_IllegalStateException() {
        Post post = newPost();
        post.softDelete("사유1사유1사유1사유1", 1L);

        assertThatThrownBy(() -> post.softDelete("사유2사유2사유2사유2", 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 삭제된 게시물");
    }
}
