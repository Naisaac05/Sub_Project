package com.devmatch.repository;
import com.devmatch.entity.AssignmentSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmission, Long> {
    Optional<AssignmentSubmission> findByAssignmentId(Long assignmentId);
    boolean existsByAssignmentId(Long assignmentId);
}
