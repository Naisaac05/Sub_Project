package com.devmatch.repository;

import com.devmatch.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import com.devmatch.entity.Role;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByProviderAndProviderId(String provider, String providerId);
    
    List<User> findByRole(Role role);
}
