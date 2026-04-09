package com.devmatch.repository;

import com.devmatch.entity.MockInterview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MockInterviewRepository extends JpaRepository<MockInterview, Long> {
    List<MockInterview> findByMatchingIdOrderByInterviewDateDesc(Long matchingId);
}
