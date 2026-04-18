package com.devmatch.repository;

import com.devmatch.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import com.devmatch.entity.ApplicationStatus;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findByMenteeIdOrderByCreatedAtDesc(Long menteeId);
    
    // 멘토가 자신에게 할당된 신청서 목록을 조회할 때 사용
    List<Application> findByAssignedMentorIdAndStatusOrderByCreatedAtAsc(Long assignedMentorId, ApplicationStatus status);
}
