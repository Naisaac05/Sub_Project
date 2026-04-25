package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.faq.FaqResponse;
import com.devmatch.service.FaqService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "FAQ (Public)", description = "FAQ 공개 조회 API")
@RestController
@RequestMapping("/api/faqs")
@RequiredArgsConstructor
public class FaqController {

    private final FaqService service;

    @Operation(summary = "공개 FAQ 목록 (published=true 만)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<FaqResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(service.listPublic()));
    }
}
