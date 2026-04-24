package com.devmatch.dto.admin.post;

import java.time.LocalDate;

public record AdminPostFilter(
        String category,
        String q,
        LocalDate from,
        LocalDate to,
        boolean includeDeleted
) {}
