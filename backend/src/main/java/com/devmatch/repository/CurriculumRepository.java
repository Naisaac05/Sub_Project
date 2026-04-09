package com.devmatch.repository;
import com.devmatch.entity.Curriculum;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CurriculumRepository extends JpaRepository<Curriculum, Long> {
    Optional<Curriculum> findByMatchingId(Long matchingId);
    boolean existsByMatchingId(Long matchingId);
}
