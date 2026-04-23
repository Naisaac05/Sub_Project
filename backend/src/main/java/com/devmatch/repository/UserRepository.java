package com.devmatch.repository;

import com.devmatch.entity.Role;
import com.devmatch.entity.User;
import com.devmatch.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByProviderAndProviderId(String provider, String providerId);

    List<User> findByRole(Role role);

    Page<User> findByRoleAndStatus(Role role, UserStatus status, Pageable pageable);

    Page<User> findByRole(Role role, Pageable pageable);

    Page<User> findByStatus(UserStatus status, Pageable pageable);

    Page<User> findByNameContainingOrEmailContaining(String name, String email, Pageable pageable);

    List<User> findByRoleIn(Collection<Role> roles);

    /**
     * 회원 관리 목록 조회 — role/status/q 모두 조합 가능. null 인 파라미터는 무시.
     * (Phase II Feature 1: q 와 role/status 동시 적용을 위해 추가)
     */
    @Query("SELECT u FROM User u WHERE " +
            "(:role IS NULL OR u.role = :role) AND " +
            "(:status IS NULL OR u.status = :status) AND " +
            "(:q IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "             OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<User> searchAdminUsers(@Param("role") Role role,
                                @Param("status") UserStatus status,
                                @Param("q") String q,
                                Pageable pageable);
}
