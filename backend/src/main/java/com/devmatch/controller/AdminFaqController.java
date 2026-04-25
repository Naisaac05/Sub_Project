package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.faq.FaqCreateRequest;
import com.devmatch.dto.faq.FaqResponse;
import com.devmatch.dto.faq.FaqUpdateRequest;
import com.devmatch.service.FaqService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin FAQ", description = "관리자 FAQ 관리 API")
@RestController
@RequestMapping("/api/admin/faqs")
@RequiredArgsConstructor
public class AdminFaqController {

    private final FaqService service;

    @Operation(summary = "전체 FAQ 목록 (published 무관)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<FaqResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(service.listAll()));
    }

    @Operation(summary = "FAQ 생성")
    @PostMapping
    public ResponseEntity<ApiResponse<FaqResponse>> create(@Valid @RequestBody FaqCreateRequest req) {
        return ResponseEntity.ok(ApiResponse.success(service.create(req)));
    }

    @Operation(summary = "FAQ 수정 (부분)")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FaqResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody FaqUpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.success(service.update(id, req)));
    }

    @Operation(summary = "FAQ 삭제 (hard)")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
