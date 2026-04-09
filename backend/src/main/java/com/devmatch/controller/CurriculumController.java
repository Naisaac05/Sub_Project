package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.lms.CurriculumCreateRequest;
import com.devmatch.dto.lms.CurriculumResponse;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.CurriculumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "LMS - Curriculum", description = "커리큘럼 관리 API")
@RestController
@RequestMapping("/api/lms/curriculum")
@RequiredArgsConstructor
public class CurriculumController {
    private final CurriculumService curriculumService;

    @Operation(summary = "커리큘럼 생성", description = "멘토가 커리큘럼과 주차 정보를 생성합니다")
    @PostMapping
    public ResponseEntity<ApiResponse<CurriculumResponse>> create(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody CurriculumCreateRequest request) {
        CurriculumResponse response = curriculumService.create(user.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("커리큘럼이 생성되었습니다", response));
    }

    @Operation(summary = "커리큘럼 조회")
    @GetMapping("/{matchingId}")
    public ResponseEntity<ApiResponse<CurriculumResponse>> get(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId) {
        CurriculumResponse response = curriculumService.getByMatchingId(user.getUserId(), matchingId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "커리큘럼 수정")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CurriculumResponse>> update(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody CurriculumCreateRequest request) {
        CurriculumResponse response = curriculumService.update(user.getUserId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("커리큘럼이 수정되었습니다", response));
    }

    @Operation(summary = "주차 완료 토글")
    @PutMapping("/weeks/{weekId}/complete")
    public ResponseEntity<ApiResponse<Void>> toggleWeekComplete(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long weekId) {
        curriculumService.toggleWeekComplete(user.getUserId(), weekId);
        return ResponseEntity.ok(ApiResponse.success("주차 완료 상태가 변경되었습니다", null));
    }
}
