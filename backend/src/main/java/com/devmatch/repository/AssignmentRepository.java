package com.devmatch.repository;
import com.devmatch.entity.Assignment;
import com.devmatch.entity.AssignmentStatus;
import com.devmatch.entity.AssignmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    List<Assignment> findByMatchingIdOrderByCreatedAtDesc(Long matchingId);
    List<Assignment> findByMatchingIdAndTypeOrderByCreatedAtDesc(Long matchingId, AssignmentType type);
    long countByMatchingId(Long matchingId);
    long countByMatchingIdAndStatusIn(Long matchingId, List<AssignmentStatus> statuses);
}
