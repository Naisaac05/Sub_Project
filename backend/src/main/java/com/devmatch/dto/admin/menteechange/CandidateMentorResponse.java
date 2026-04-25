package com.devmatch.dto.admin.menteechange;

public record CandidateMentorResponse(
        Long userId,
        String name,
        String email,
        Integer activeMenteeCount
) {}
