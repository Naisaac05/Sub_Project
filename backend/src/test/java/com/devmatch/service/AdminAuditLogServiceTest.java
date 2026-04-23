package com.devmatch.service;

import com.devmatch.entity.AdminActionType;
import com.devmatch.entity.AdminAuditLog;
import com.devmatch.repository.AdminAuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditLogServiceTest {

    @Mock private AdminAuditLogRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private AdminAuditLogService service;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        service = new AdminAuditLogService(repository, objectMapper);
    }

    @Test
    void record_정상_입력시_엔티티가_저장된다() {
        when(repository.save(any(AdminAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.record(1L, AdminActionType.USER_ROLE_CHANGE,
                "USER", 42L, null, Map.of("from", "MENTEE", "to", "ADMIN"));

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(repository).save(captor.capture());
        AdminAuditLog saved = captor.getValue();

        assertThat(saved.getAdminId()).isEqualTo(1L);
        assertThat(saved.getActionType()).isEqualTo(AdminActionType.USER_ROLE_CHANGE);
        assertThat(saved.getTargetType()).isEqualTo("USER");
        assertThat(saved.getTargetId()).isEqualTo(42L);
        assertThat(saved.getReason()).isNull();
        assertThat(saved.getMetadata()).contains("\"from\":\"MENTEE\"");
        assertThat(saved.getMetadata()).contains("\"to\":\"ADMIN\"");
    }

    @Test
    void record_metadata가_null이면_metadata_컬럼도_null로_저장된다() {
        when(repository.save(any(AdminAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.record(1L, AdminActionType.MENTOR_APPROVE,
                "MENTOR_PROFILE", 10L, null, null);

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMetadata()).isNull();
    }

    @Test
    void record_metadata가_빈_맵이면_null로_저장된다() {
        when(repository.save(any(AdminAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.record(1L, AdminActionType.POST_DELETE,
                "POST", 5L, "부적절한 내용", Map.of());

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMetadata()).isNull();
    }

    @Test
    void record_reason이_있으면_그대로_저장된다() {
        when(repository.save(any(AdminAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.record(2L, AdminActionType.PAYMENT_REFUND,
                "PAYMENT", 100L, "결제 중복 환불 요청", null);

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo("결제 중복 환불 요청");
    }
}
