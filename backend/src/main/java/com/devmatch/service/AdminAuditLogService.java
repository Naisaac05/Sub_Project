package com.devmatch.service;

import com.devmatch.entity.AdminActionType;
import com.devmatch.entity.AdminAuditLog;
import com.devmatch.repository.AdminAuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 관리자 행위 감사 로그를 기록하는 서비스.
 *
 * <p>호출 계약 (스펙 §4.4):
 * <ul>
 *   <li>호출은 도메인 서비스 메서드의 {@code @Transactional} 내부에서만</li>
 *   <li>도메인 변경 직후 호출</li>
 *   <li>{@code metadata} 값 타입은 String/Number/Boolean/Enum.name() 4종만</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AdminAuditLogService {

    private final AdminAuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public void record(Long adminId, AdminActionType actionType,
                       String targetType, Long targetId,
                       String reason, Map<String, Object> metadata) {
        repository.save(AdminAuditLog.builder()
                .adminId(adminId)
                .actionType(actionType)
                .targetType(targetType)
                .targetId(targetId)
                .reason(reason)
                .metadata(serialize(metadata))
                .build());
    }

    private String serialize(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("감사 로그 metadata 직렬화 실패", e);
        }
    }
}
