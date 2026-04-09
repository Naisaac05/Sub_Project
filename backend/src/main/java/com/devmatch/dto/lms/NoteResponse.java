package com.devmatch.dto.lms;
import com.devmatch.entity.LearningNote;
import com.devmatch.entity.NoteComment;
import com.devmatch.entity.NoteType;
import lombok.AllArgsConstructor; import lombok.Builder; import lombok.Getter;
import java.time.LocalDateTime;
import java.util.List;

@Getter @AllArgsConstructor @Builder
public class NoteResponse {
    private Long id; private Long matchingId; private Long authorId; private String authorName;
    private NoteType type; private Long sessionId; private Integer weekNumber;
    private String title; private String content; private Integer selfRating;
    private List<CommentInfo> comments; private LocalDateTime createdAt; private LocalDateTime updatedAt;

    @Getter @AllArgsConstructor @Builder
    public static class CommentInfo {
        private Long id; private Long authorId; private String authorName;
        private String content; private LocalDateTime createdAt;
        public static CommentInfo from(NoteComment c, String authorName) {
            return CommentInfo.builder().id(c.getId()).authorId(c.getAuthorId())
                    .authorName(authorName).content(c.getContent()).createdAt(c.getCreatedAt()).build();
        }
    }

    public static NoteResponse from(LearningNote note, String authorName, List<CommentInfo> commentInfos) {
        return NoteResponse.builder().id(note.getId()).matchingId(note.getMatchingId())
                .authorId(note.getAuthorId()).authorName(authorName).type(note.getType())
                .sessionId(note.getSessionId()).weekNumber(note.getWeekNumber())
                .title(note.getTitle()).content(note.getContent()).selfRating(note.getSelfRating())
                .comments(commentInfos).createdAt(note.getCreatedAt()).updatedAt(note.getUpdatedAt()).build();
    }
}
