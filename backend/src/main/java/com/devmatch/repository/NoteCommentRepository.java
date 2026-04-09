package com.devmatch.repository;
import com.devmatch.entity.NoteComment;
import org.springframework.data.jpa.repository.JpaRepository;
public interface NoteCommentRepository extends JpaRepository<NoteComment, Long> {}
