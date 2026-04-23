package com.devmatch.service;

import com.devmatch.dto.auth.LoginRequest;
import com.devmatch.entity.Role;
import com.devmatch.entity.User;
import com.devmatch.entity.UserStatus;
import com.devmatch.exception.AccountInactiveException;
import com.devmatch.repository.UserRepository;
import com.devmatch.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginGuardTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshSessionService refreshSessionService;
    @InjectMocks private AuthService authService;

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest req = new LoginRequest();
        ReflectionTestUtils.setField(req, "email", email);
        ReflectionTestUtils.setField(req, "password", password);
        return req;
    }

    private User userWithStatus(UserStatus status) {
        User user = User.builder()
                .email("test@devmatch.com")
                .password("encoded-password")
                .name("테스트")
                .role(Role.MENTEE)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        // Use the appropriate method to set status via builder default override
        ReflectionTestUtils.setField(user, "status", status);
        return user;
    }

    @Test
    void login_DEACTIVATED_사용자_AccountInactiveException() {
        User deactivatedUser = userWithStatus(UserStatus.DEACTIVATED);
        LoginRequest req = loginRequest("test@devmatch.com", "password123");

        when(userRepository.findByEmail("test@devmatch.com")).thenReturn(Optional.of(deactivatedUser));
        lenient().when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(req, "device", "127.0.0.1"))
                .isInstanceOf(AccountInactiveException.class)
                .hasMessageContaining("비활성");
    }

    @Test
    void login_DELETED_사용자_AccountInactiveException() {
        User deletedUser = userWithStatus(UserStatus.DELETED);
        LoginRequest req = loginRequest("test@devmatch.com", "password123");

        when(userRepository.findByEmail("test@devmatch.com")).thenReturn(Optional.of(deletedUser));
        lenient().when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(req, "device", "127.0.0.1"))
                .isInstanceOf(AccountInactiveException.class)
                .hasMessageContaining("탈퇴");
    }
}
