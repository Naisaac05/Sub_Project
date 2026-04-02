package com.devmatch.repository;

import com.devmatch.entity.MentorAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MentorAvailabilityRepository extends JpaRepository<MentorAvailability, Long> {

    List<MentorAvailability> findByMentorIdAndIsActiveTrue(Long mentorId);

    List<MentorAvailability> findByMentorId(Long mentorId);
}
