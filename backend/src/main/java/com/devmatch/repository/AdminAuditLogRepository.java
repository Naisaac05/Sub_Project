package com.devmatch.repository;

import com.devmatch.entity.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    // Phase II 에서는 조회 메서드 불필요. Phase III 대시보드에서 추가 예정.
}
