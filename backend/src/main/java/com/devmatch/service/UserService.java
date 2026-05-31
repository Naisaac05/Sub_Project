package com.devmatch.service;

import com.devmatch.dto.user.UserResponse;
import com.devmatch.dto.user.UserUpdateRequest;
import com.devmatch.entity.User;
import com.devmatch.exception.UserNotFoundException;
import com.devmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshSessionService refreshSessionService;

    public UserResponse getMyProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다"));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateMyProfile(Long userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다"));

        if (request.getName() != null) {
            user.updateName(request.getName());
        }
        if (request.getPassword() != null) {
            user.updatePassword(passwordEncoder.encode(request.getPassword()));
            // 비밀번호 변경 시 기존 refresh 세션 전체 폐기
            refreshSessionService.revokeAllForUser(userId);
        }

        return UserResponse.from(user);
    }
}
