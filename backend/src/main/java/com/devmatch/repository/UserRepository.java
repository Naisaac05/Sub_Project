package com.devmatch.repository;

import com.devmatch.entity.Role;
import com.devmatch.entity.User;
import com.devmatch.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
