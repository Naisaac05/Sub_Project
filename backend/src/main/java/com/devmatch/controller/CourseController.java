package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.course.CourseResponse;
import com.devmatch.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Course", description = "멘토링 코스 카탈로그 API")
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @Operation(summary = "활성 코스 전체 조회", description = "displayOrder 오름차순으로 활성 코스 목록을 반환합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CourseResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success("코스 목록 조회 성공", courseService.findAllActive()));
    }

    @Operation(summary = "단일 코스 조회", description = "courseKey로 활성 코스 상세를 반환합니다.")
    @GetMapping("/{courseKey}")
    public ResponseEntity<ApiResponse<CourseResponse>> detail(@PathVariable String courseKey) {
        return ResponseEntity.ok(ApiResponse.success("코스 조회 성공", courseService.findByKey(courseKey)));
    }
}
