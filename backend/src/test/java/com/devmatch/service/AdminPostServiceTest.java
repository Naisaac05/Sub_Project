package com.devmatch.service;

import com.devmatch.dto.admin.post.AdminPostDetailResponse;
import com.devmatch.entity.AdminActionType;
import com.devmatch.entity.Comment;
import com.devmatch.entity.Post;
import com.devmatch.entity.Role;
import com.devmatch.entity.User;
import com.devmatch.entity.UserStatus;
import com.devmatch.exception.AlreadyDeletedException;
import com.devmatch.exception.PostNotFoundException;
import com.devmatch.repository.CommentRepository;
import com.devmatch.repository.PostRepository;
import com.devmatch.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPostServiceTest {

    @Mock PostRepository postRepository;
    @Mock CommentRepository commentRepository;
    @Mock UserRepository userRepository;
    @Mock AdminAuditLogService auditLogService;

    @InjectMocks AdminPostService service;

    private User user(Long id, String name) {
        return User.builder().id(id).email(name + "@t").name(name)
                .role(Role.MENTEE).status(UserStatus.ACTIVE).build();
    }

    private Post post(Long id, User author) {
        return Post.builder().id(id).author(author).title("t").content("c")
                .category("질문").likeCount(0).commentCount(1).viewCount(0).build();
    }

    private Comment comment(Long id, Post post, User author) {
        return Comment.builder().id(id).post(post).author(author).content("cc").build();
    }

    @Test
    void deletePost_정상_플로우_감사로그_호출() {
        User author = user(10L, "auth");
        Post p = post(1L, author);
        when(postRepository.findById(1L)).thenReturn(Optional.of(p));
        when(commentRepository.findByPostIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

        AdminPostDetailResponse res = service.deletePost(1L, 99L, "스팸 광고 이유로 삭제합니다");

        assertThat(p.isDeleted()).isTrue();
        assertThat(res.deleted()).isTrue();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metaCap = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).record(eq(99L), eq(AdminActionType.POST_DELETE),
                eq("POST"), eq(1L), eq("스팸 광고 이유로 삭제합니다"), metaCap.capture());
        assertThat(metaCap.getValue()).containsEntry("authorId", 10L)
                .containsEntry("category", "질문");
    }

    @Test
    void deletePost_존재하지_않으면_PostNotFoundException() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePost(99L, 1L, "사유사유사유사유사유"))
                .isInstanceOf(PostNotFoundException.class);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void deletePost_이미_삭제된_경우_AlreadyDeletedException() {
        User author = user(10L, "auth");
        Post p = post(1L, author);
        p.softDelete("기존사유기존사유", 1L);
        when(postRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.deletePost(1L, 99L, "새사유새사유새사유새사유"))
                .isInstanceOf(AlreadyDeletedException.class);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void deleteComment_commentCount_감소_및_감사로그() {
        User author = user(10L, "auth");
        Post p = post(1L, author);
        Comment c = comment(2L, p, author);
        when(commentRepository.findById(2L)).thenReturn(Optional.of(c));

        service.deleteComment(1L, 2L, 99L, "욕설포함사유욕설포함");

        assertThat(c.isDeleted()).isTrue();
        assertThat(p.getCommentCount()).isZero();
        verify(auditLogService).record(eq(99L), eq(AdminActionType.COMMENT_DELETE),
                eq("COMMENT"), eq(2L), eq("욕설포함사유욕설포함"), anyMap());
    }

    @Test
    void deleteComment_postId_불일치시_IllegalArgumentException() {
        User author = user(10L, "auth");
        Post p = post(1L, author);
        Comment c = comment(2L, p, author);
        when(commentRepository.findById(2L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.deleteComment(777L, 2L, 99L, "사유사유사유사유사유"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(auditLogService);
    }
}
