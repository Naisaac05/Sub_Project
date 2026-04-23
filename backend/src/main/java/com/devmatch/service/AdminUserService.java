package com.devmatch.service;

import com.devmatch.dto.admin.AdminUserDetailResponse;
import com.devmatch.dto.admin.AdminUserListResponse;
import com.devmatch.entity.AdminActionType;
import com.devmatch.entity.MentorProfile;
import com.devmatch.entity.Role;
import com.devmatch.entity.User;
import com.devmatch.entity.UserStatus;
import com.devmatch.exception.ForbiddenOperationException;
import com.devmatch.exception.UserNotFoundException;
import com.devmatch.repository.MentorProfileRepository;
import com.devmatch.repository.PaymentRepository;
import com.devmatch.repository.PostRepository;
import com.devmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final PostRepository postRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final com.devmatch.util.PasswordGenerator passwordGenerator;

    public Page<AdminUserListResponse> list(Role role, UserStatus status, String q, Pageable pageable) {
        // role/status/q 모두 조합 가능 — JPQL 단일 쿼리로 위임 (이전 if-else 사다리는 q 가 있으면
        // role/status 를 무시하던 버그 — 2026-04-23 코드리뷰 피드백 반영)
        String normalizedQ = (q == null || q.isBlank()) ? null : q.trim();
        return userRepository.searchAdminUsers(role, status, normalizedQ, pageable)
                .map(AdminUserListResponse::from);
    }

    public AdminUserDetailResponse getDetail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("회원을 찾을 수 없습니다: " + userId));
        long paymentCount = paymentRepository.countByUserId(userId);
        long postCount = postRepository.countByAuthor_Id(userId);
        Long mentorProfileId = mentorProfileRepository.findByUserId(userId)
                .map(MentorProfile::getId).orElse(null);
        return AdminUserDetailResponse.from(user, paymentCount, postCount, mentorProfileId);
    }

    @Transactional
    public void deactivate(Long adminId, Long targetId, String reason) {
        User target = loadTarget(targetId);
        guardAgainstSelf(adminId, targetId);
        guardAgainstAdminTarget(target);
        guardAgainstDeleted(target);

        target.deactivate();
        adminAuditLogService.record(adminId, AdminActionType.USER_DEACTIVATE,
                "USER", targetId, reason, null);
    }

    @Transactional
    public void reactivate(Long adminId, Long targetId) {
        User target = loadTarget(targetId);
        guardAgainstAdminTarget(target);
        guardAgainstDeleted(target);

        target.reactivate();
        adminAuditLogService.record(adminId, AdminActionType.USER_REACTIVATE,
                "USER", targetId, null, null);
    }

    @Transactional
    public void delete(Long adminId, Long targetId, String reason) {
        User target = loadTarget(targetId);
        guardAgainstSelf(adminId, targetId);
        guardAgainstAdminTarget(target);
        guardAgainstDeleted(target);

        target.markDeleted();
        adminAuditLogService.record(adminId, AdminActionType.USER_DELETE,
                "USER", targetId, reason, null);
    }

    @Transactional
    public com.devmatch.dto.admin.PasswordResetResponse resetPassword(Long adminId, Long targetId) {
        User target = loadTarget(targetId);
        guardAgainstSuperAdminTarget(target);
        guardAgainstDeleted(target);

        String temp = passwordGenerator.generate();
        String encoded = passwordEncoder.encode(temp);
        target.forcePasswordChange(encoded);

        adminAuditLogService.record(adminId, AdminActionType.USER_PASSWORD_RESET,
                "USER", targetId, null, null);

        return new com.devmatch.dto.admin.PasswordResetResponse(temp, true);
    }

    private void guardAgainstSuperAdminTarget(User target) {
        if (target.getRole() == Role.SUPER_ADMIN) {
            throw new ForbiddenOperationException("SUPER_ADMIN 의 비밀번호는 이 메뉴에서 리셋할 수 없습니다.");
        }
    }

    private User loadTarget(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("회원을 찾을 수 없습니다: " + id));
    }

    private void guardAgainstSelf(Long adminId, Long targetId) {
        if (adminId.equals(targetId)) {
            throw new ForbiddenOperationException("관리자 본인 계정에는 이 작업을 수행할 수 없습니다.");
        }
    }

    private void guardAgainstAdminTarget(User target) {
        if (target.getRole() == Role.ADMIN || target.getRole() == Role.SUPER_ADMIN) {
            throw new ForbiddenOperationException("관리자 계정은 회원 관리 메뉴에서 변경할 수 없습니다.");
        }
    }

    private void guardAgainstDeleted(User target) {
        if (target.getStatus() == UserStatus.DELETED) {
            throw new ForbiddenOperationException("이미 삭제된 계정입니다.");
        }
    }
}
