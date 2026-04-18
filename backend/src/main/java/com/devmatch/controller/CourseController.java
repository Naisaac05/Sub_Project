package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.course.CourseResponse;
import com.devmatch.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    public ApiResponse<List<CourseResponse>> list() {
        return ApiResponse.success("코스 목록 조회 성공", courseService.findAllActive());
    }

    @GetMapping("/{courseKey}")
    public ApiResponse<CourseResponse> detail(@PathVariable String courseKey) {
        return ApiResponse.success("코스 조회 성공", courseService.findByKey(courseKey));
    }
}
