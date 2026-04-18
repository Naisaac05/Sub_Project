package com.devmatch.repository;

import com.devmatch.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import com.devmatch.entity.ApplicationStatus;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findByMenteeIdOrderByCreatedAtDesc(Long menteeId);

    List<Application> findByAssignedMentorIdAndStatusOrderByCreatedAtAsc(Long assignedMentorId, ApplicationStatus status);
}
