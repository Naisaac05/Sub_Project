package com.devmatch.service;

import com.devmatch.entity.Role;
import com.devmatch.entity.User;
import com.devmatch.entity.UserStatus;
import com.devmatch.exception.UserNotFoundException;
import com.devmatch.repository.MentorProfileRepository;
import com.devmatch.repository.PaymentRepository;
import com.devmatch.repository.PostRepository;
import com.devmatch.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock UserRepository userRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock PostRepository postRepository;
    @Mock MentorProfileRepository mentorProfileRepository;

    @InjectMocks AdminUserService service;

    private User userOf(Long id, Role role, UserStatus status) {
        User u = User.builder().email("u@test").password("enc").name("U")
                .role(role).status(status).build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @Test
    void getDetail_연관_활동_카운트_포함() {
        User u = userOf(7L, Role.MENTEE, UserStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));
        when(paymentRepository.countByUserId(7L)).thenReturn(3L);
        when(postRepository.countByAuthor_Id(7L)).thenReturn(12L);
        when(mentorProfileRepository.findByUserId(7L)).thenReturn(Optional.empty());

        var detail = service.getDetail(7L);

        assertThat(detail.getId()).isEqualTo(7L);
        assertThat(detail.getPaymentCount()).isEqualTo(3L);
        assertThat(detail.getPostCount()).isEqualTo(12L);
        assertThat(detail.getMentorProfileId()).isNull();
    }

    @Test
    void getDetail_없는_사용자_예외() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getDetail(99L))
                .isInstanceOf(UserNotFoundException.class);
    }
}
