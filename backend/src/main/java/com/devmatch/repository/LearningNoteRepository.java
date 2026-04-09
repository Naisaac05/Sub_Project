package com.devmatch.repository;
import com.devmatch.entity.LearningNote;
import com.devmatch.entity.NoteType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LearningNoteRepository extends JpaRepository<LearningNote, Long> {
    List<LearningNote> findByMatchingIdOrderByCreatedAtDesc(Long matchingId);
    List<LearningNote> findByMatchingIdAndTypeOrderByCreatedAtDesc(Long matchingId, NoteType type);
}
