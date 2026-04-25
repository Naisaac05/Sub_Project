package com.devmatch.dto.admin.menteechange;

import java.util.List;

public record CandidateMentorResponse(
        Long userId,
        String name,
        String email,
        Integer activeMenteeCount,
        List<String> courseTitles
) {}
