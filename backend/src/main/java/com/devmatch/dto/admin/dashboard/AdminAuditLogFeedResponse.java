package com.devmatch.dto.admin.dashboard;

import com.devmatch.entity.AdminActionType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/admin/dashboard/audit-log 응답 (SUPER_ADMIN).
 * metadata 원문은 의도적으로 포함하지 않는다 — 민감 사유 노출 방지 (스펙 §5.4).
 */
public record AdminAuditLogFeedResponse(List<Item> items) {

    public record Item(
            Long id,
            String adminName,
            AdminActionType actionType,
            String description,
            String targetHref,
            LocalDateTime createdAt
    ) {}
}
