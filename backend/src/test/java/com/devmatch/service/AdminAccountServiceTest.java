package com.devmatch.service;

import com.devmatch.dto.admin.AdminCreateRequest;
import com.devmatch.entity.Role;
import com.devmatch.entity.User;
import com.devmatch.entity.UserStatus;
import com.devmatch.repository.UserRepository;
import com.devmatch.util.PasswordGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccountServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PasswordGenerator passwordGenerator;
    @Mock AdminAuditLogService auditLogService;

    @InjectMocks AdminAccountService service;

    private AdminCreateRequest req() {
        AdminCreateRequest r = new AdminCreateRequest();
        ReflectionTestUtils.setField(r, "email", "newadmin@x.com");
        ReflectionTestUtils.setField(r, "name", "새관리자");
        ReflectionTestUtils.setField(r, "jobTitle", "운영팀장");
        return r;
    }

    @Test
    void createAdmin_정상_평문_비번_응답() {
        when(userRepository.existsByEmail("newadmin@x.com")).thenReturn(false);
        when(passwordGenerator.generate()).thenReturn("Tmp1!XyzAbc2");
        when(passwordEncoder.encode("Tmp1!XyzAbc2")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 99L);
            return u;
        });

        var resp = service.createAdmin(1L, req());

        assertThat(resp.getTemporaryPassword()).isEqualTo("Tmp1!XyzAbc2");
        assertThat(resp.getUser().getEmail()).isEqualTo("newadmin@x.com");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("newadmin@x.com");
        assertThat(saved.getName()).isEqualTo("새관리자");
        assertThat(saved.getJobTitle()).isEqualTo("운영팀장");
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getMustChangePassword()).isTrue();
        assertThat(saved.getPassword()).isEqualTo("encoded");

        verify(auditLogService).record(
                eq(1L),
                eq(com.devmatch.entity.AdminActionType.ADMIN_CREATE),
                eq("USER"),
                eq(99L),
                org.mockito.ArgumentMatchers.isNull(),
                eq(java.util.Map.of("email", "newadmin@x.com", "jobTitle", "운영팀장")));
    }

    @Test
    void createAdmin_중복_이메일_예외() {
        when(userRepository.existsByEmail("newadmin@x.com")).thenReturn(true);
        assertThatThrownBy(() -> service.createAdmin(1L, req()))
                .isInstanceOf(com.devmatch.exception.DuplicateEmailException.class);
    }
}
