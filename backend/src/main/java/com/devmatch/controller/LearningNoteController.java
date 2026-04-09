package com.devmatch.controller;
import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.lms.*;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.LearningNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name = "LMS - Notes", description = "학습 노트 API")
@RestController @RequestMapping("/api/lms/notes") @RequiredArgsConstructor
public class LearningNoteController {
    private final LearningNoteService noteService;

    @Operation(summary = "노트 작성") @PostMapping
    public ResponseEntity<ApiResponse<NoteResponse>> create(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody NoteCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("학습 노트가 작성되었습니다", noteService.create(user.getUserId(), request)));
    }

    @Operation(summary = "노트 목록 조회") @GetMapping
    public ResponseEntity<ApiResponse<List<NoteResponse>>> getList(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam Long matchingId, @RequestParam(required = false) String type) {
        return ResponseEntity.ok(ApiResponse.success(noteService.getList(user.getUserId(), matchingId, type)));
    }

    @Operation(summary = "노트 상세 조회") @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NoteResponse>> getDetail(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(noteService.getDetail(user.getUserId(), id)));
    }

    @Operation(summary = "노트 수정") @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NoteResponse>> update(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id,
            @Valid @RequestBody NoteCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("학습 노트가 수정되었습니다", noteService.update(user.getUserId(), id, request)));
    }

    @Operation(summary = "코멘트 추가") @PostMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<NoteResponse>> addComment(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id,
            @Valid @RequestBody NoteCommentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("코멘트가 등록되었습니다", noteService.addComment(user.getUserId(), id, request)));
    }
}
