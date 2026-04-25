package com.devmatch.repository;

import com.devmatch.entity.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    /** 대시보드 피드용 — 최신 10건. admin_audit_log.created_at 인덱스는 adminId 복합 인덱스뿐이라
     *  풀 스캔이지만, 현재 row 수 규모에서 문제 없음. */
    List<AdminAuditLog> findTop10ByOrderByCreatedAtDesc();
}
