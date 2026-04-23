package com.devmatch.service;

import com.devmatch.dto.admin.AdminCreateRequest;
import com.devmatch.dto.admin.AdminCreateResponse;
import com.devmatch.dto.user.UserResponse;
import com.devmatch.entity.AdminActionType;
import com.devmatch.entity.Role;
import com.devmatch.entity.User;
import com.devmatch.entity.UserStatus;
import com.devmatch.exception.DuplicateEmailException;
import com.devmatch.repository.UserRepository;
import com.devmatch.util.PasswordGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordGenerator passwordGenerator;
    private final AdminAuditLogService adminAuditLogService;

    @Transactional(readOnly = true)
    public List<UserResponse> listAdmins() {
        return userRepository.findByRoleIn(List.of(Role.ADMIN, Role.SUPER_ADMIN))
                .stream().map(UserResponse::from).toList();
    }

    @Transactional
    public AdminCreateResponse createAdmin(Long superAdminId, AdminCreateRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("이미 존재하는 이메일입니다: " + request.getEmail());
        }

        String temp = passwordGenerator.generate();
        String encoded = passwordEncoder.encode(temp);

        User user = User.builder()
                .email(request.getEmail())
                .password(encoded)
                .name(request.getName())
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .jobTitle(request.getJobTitle())
                .mustChangePassword(true)
                .build();
        userRepository.save(user);

        adminAuditLogService.record(superAdminId, AdminActionType.ADMIN_CREATE,
                "USER", user.getId(), null,
                Map.of("email", request.getEmail(), "jobTitle", request.getJobTitle()));

        return new AdminCreateResponse(UserResponse.from(user), temp);
    }
}
