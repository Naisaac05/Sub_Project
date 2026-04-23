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

    public Page<AdminUserListResponse> list(Role role, UserStatus status, String q, Pageable pageable) {
        Page<User> users;
        if (q != null && !q.isBlank()) {
            users = userRepository.findByNameContainingOrEmailContaining(q, q, pageable);
        } else if (role != null && status != null) {
            users = userRepository.findByRoleAndStatus(role, status, pageable);
        } else if (role != null) {
            users = userRepository.findByRole(role, pageable);
        } else if (status != null) {
            users = userRepository.findByStatus(status, pageable);
        } else {
            users = userRepository.findAll(pageable);
        }
        return users.map(AdminUserListResponse::from);
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
