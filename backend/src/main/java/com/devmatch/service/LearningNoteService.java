package com.devmatch.service;
import com.devmatch.dto.lms.*;
import com.devmatch.entity.*;
import com.devmatch.exception.NoteNotFoundException;
import com.devmatch.exception.UserNotFoundException;
import com.devmatch.repository.LearningNoteRepository;
import com.devmatch.repository.NoteCommentRepository;
import com.devmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class LearningNoteService {
    private final LearningNoteRepository noteRepository;
    private final NoteCommentRepository commentRepository;
    private final UserRepository userRepository;
    private final LmsAccessService lmsAccessService;

    @Transactional
    public NoteResponse create(Long userId, NoteCreateRequest request) {
        lmsAccessService.validateAccess(userId, request.getMatchingId());
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다: " + userId));
        LearningNote note = LearningNote.builder()
                .matchingId(request.getMatchingId()).authorId(userId).type(request.getType())
                .sessionId(request.getSessionId()).weekNumber(request.getWeekNumber())
                .title(request.getTitle()).content(request.getContent()).selfRating(request.getSelfRating())
                .build();
        return NoteResponse.from(noteRepository.save(note), author.getName(), Collections.emptyList());
    }

    public List<NoteResponse> getList(Long userId, Long matchingId, String type) {
        lmsAccessService.validateAccess(userId, matchingId);
        List<LearningNote> notes;
        if (type != null && !type.isBlank()) {
            notes = noteRepository.findByMatchingIdAndTypeOrderByCreatedAtDesc(matchingId, NoteType.valueOf(type));
        } else {
            notes = noteRepository.findByMatchingIdOrderByCreatedAtDesc(matchingId);
        }
        return notes.stream().map(note -> {
            String authorName = userRepository.findById(note.getAuthorId()).map(User::getName).orElse("알 수 없음");
            List<NoteResponse.CommentInfo> commentInfos = note.getComments().stream().map(c -> {
                String cName = userRepository.findById(c.getAuthorId()).map(User::getName).orElse("알 수 없음");
                return NoteResponse.CommentInfo.from(c, cName);
            }).collect(Collectors.toList());
            return NoteResponse.from(note, authorName, commentInfos);
        }).collect(Collectors.toList());
    }

    public NoteResponse getDetail(Long userId, Long noteId) {
        LearningNote note = findNote(noteId);
        lmsAccessService.validateAccess(userId, note.getMatchingId());
        String authorName = userRepository.findById(note.getAuthorId()).map(User::getName).orElse("알 수 없음");
        List<NoteResponse.CommentInfo> commentInfos = note.getComments().stream().map(c -> {
            String cName = userRepository.findById(c.getAuthorId()).map(User::getName).orElse("알 수 없음");
            return NoteResponse.CommentInfo.from(c, cName);
        }).collect(Collectors.toList());
        return NoteResponse.from(note, authorName, commentInfos);
    }

    @Transactional
    public NoteResponse update(Long userId, Long noteId, NoteCreateRequest request) {
        LearningNote note = findNote(noteId);
        lmsAccessService.validateAccess(userId, note.getMatchingId());
        note.update(request.getTitle(), request.getContent(), request.getSelfRating());
        String authorName = userRepository.findById(note.getAuthorId()).map(User::getName).orElse("알 수 없음");
        return NoteResponse.from(note, authorName, Collections.emptyList());
    }

    @Transactional
    public NoteResponse addComment(Long userId, Long noteId, NoteCommentRequest request) {
        LearningNote note = findNote(noteId);
        lmsAccessService.validateAccess(userId, note.getMatchingId());
        NoteComment comment = NoteComment.builder().note(note).authorId(userId).content(request.getContent()).build();
        commentRepository.save(comment);
        return getDetail(userId, noteId);
    }

    private LearningNote findNote(Long id) {
        return noteRepository.findById(id)
                .orElseThrow(() -> new NoteNotFoundException("학습 노트를 찾을 수 없습니다: " + id));
    }
}
