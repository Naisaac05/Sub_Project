package com.devmatch.service;

import com.devmatch.dto.auth.LoginRequest;
import com.devmatch.dto.auth.SignupRequest;
import com.devmatch.dto.user.UserResponse;
import com.devmatch.entity.Role;
import com.devmatch.entity.User;
import com.devmatch.entity.UserStatus;
import com.devmatch.exception.AccountInactiveException;
import com.devmatch.exception.DuplicateEmailException;
import com.devmatch.exception.InvalidCredentialsException;
import com.devmatch.exception.InvalidTokenException;
import com.devmatch.repository.UserRepository;
import com.devmatch.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshSessionService refreshSessionService;

    public record AuthTokens(String accessToken, String refreshToken) {
    }

    @Transactional
    public UserResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("이미 사용 중인 이메일입니다: " + request.getEmail());
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .role(Role.valueOf(request.getRole()))
                .build();

        User savedUser = userRepository.save(user);
        return UserResponse.from(savedUser);
    }

    public AuthTokens login(LoginRequest request, String deviceInfo, String ip) {
        log.info("Login attempt for email: {}", request.getEmail());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("Login failed: User not found for email: {}", request.getEmail());
                    return new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다");
                });

        if (user.getStatus() == UserStatus.DEACTIVATED) {
            throw new AccountInactiveException("비활성화된 계정입니다. 관리자에게 문의해 주세요.");
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw new AccountInactiveException("탈퇴한 계정입니다.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Login failed: Password mismatch for email: {}", request.getEmail());
            throw new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다");
        }

        log.info("Login successful for email: {}, userId: {}, role: {}", user.getEmail(), user.getId(), user.getRole());

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        RefreshSessionService.IssuedSession session = refreshSessionService.createSession(user.getId(), deviceInfo, ip);

        return new AuthTokens(accessToken, session.refreshToken());
    }

    public AuthTokens refresh(String presentedRefreshToken) {
        if (presentedRefreshToken == null || presentedRefreshToken.isBlank()) {
            throw new InvalidTokenException("Refresh Token이 없습니다");
        }

        RefreshSessionService.IssuedSession rotated = refreshSessionService.rotate(presentedRefreshToken);

        Long userId = jwtTokenProvider.getUserIdFromToken(rotated.refreshToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("사용자를 찾을 수 없습니다"));

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        return new AuthTokens(newAccessToken, rotated.refreshToken());
    }

    public void logout(String presentedRefreshToken) {
        refreshSessionService.revokeByToken(presentedRefreshToken);
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.devmatch.exception.UserNotFoundException("사용자를 찾을 수 없습니다"));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new com.devmatch.exception.InvalidPasswordChangeException("현재 비밀번호가 일치하지 않습니다.");
        }
        if (currentPassword.equals(newPassword)) {
            throw new com.devmatch.exception.InvalidPasswordChangeException("새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }
        user.updatePassword(passwordEncoder.encode(newPassword));
        user.clearMustChangePassword();
        // 비밀번호 변경 시 기존 refresh 세션 전체 폐기 (탈취된 세션이 계속 살아있지 않도록)
        refreshSessionService.revokeAllForUser(userId);
    }
}
