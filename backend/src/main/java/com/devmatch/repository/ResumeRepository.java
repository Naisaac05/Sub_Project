package com.devmatch.repository;

import com.devmatch.entity.Resume;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ResumeRepository extends JpaRepository<Resume, Long> {
    List<Resume> findByMatchingIdOrderByVersionDesc(Long matchingId);
    long countByMatchingId(Long matchingId);
}
