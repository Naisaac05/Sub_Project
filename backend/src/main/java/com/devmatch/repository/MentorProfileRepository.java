package com.devmatch.repository;

import com.devmatch.entity.MentorProfile;
import com.devmatch.entity.MentorStatus;
import com.devmatch.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MentorProfileRepository extends JpaRepository<MentorProfile, Long> {

    Optional<MentorProfile> findByUser(User user);

    Optional<MentorProfile> findByUserId(Long userId);

    List<MentorProfile> findByStatus(MentorStatus status);

    Page<MentorProfile> findByStatus(MentorStatus status, Pageable pageable);

    boolean existsByUserId(Long userId);

    long countByStatus(MentorStatus status);
}
