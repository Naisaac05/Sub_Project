package com.devmatch.service;

import com.devmatch.dto.auth.LoginRequest;
import com.devmatch.dto.auth.SignupRequest;
import com.devmatch.dto.user.UserResponse;
import com.devmatch.entity.User;
import com.devmatch.exception.DuplicateEmailException;
import com.devmatch.exception.InvalidCredentialsException;
import com.devmatch.exception.InvalidTokenException;
import com.devmatch.repository.UserRepository;
import com.devmatch.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshSessionService refreshSessionService;

    public record AuthTokens(String accessToken, String refreshToken) {}

    @Transactional
    public UserResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("이미 사용 중인 이메일입니다: " + request.getEmail());
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .build();

        User savedUser = userRepository.save(user);
        return UserResponse.from(savedUser);
    }

    public AuthTokens login(LoginRequest request, String deviceInfo, String ip) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        RefreshSessionService.IssuedSession session =
                refreshSessionService.createSession(user.getId(), deviceInfo, ip);

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
}
