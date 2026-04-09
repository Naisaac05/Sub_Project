package com.devmatch.entity;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "learning_notes")
@EntityListeners(AuditingEntityListener.class)
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor @Builder
public class LearningNote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "matching_id", nullable = false) private Long matchingId;
    @Column(name = "author_id", nullable = false) private Long authorId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private NoteType type;
    @Column(name = "session_id") private Long sessionId;
    @Column(name = "week_number") private Integer weekNumber;
    @Column(nullable = false, length = 200) private String title;
    @Column(columnDefinition = "TEXT", nullable = false) private String content;
    @Column(name = "self_rating") private Integer selfRating;
    @OneToMany(mappedBy = "note", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC") @Builder.Default
    private List<NoteComment> comments = new ArrayList<>();
    @CreatedDate @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @LastModifiedDate @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    public void update(String title, String content, Integer selfRating) {
        this.title = title; this.content = content; this.selfRating = selfRating;
    }
}
