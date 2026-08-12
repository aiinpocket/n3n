package com.aiinpocket.n3n.auth.service;

import com.aiinpocket.n3n.auth.entity.User;
import com.aiinpocket.n3n.auth.entity.UserRole;
import com.aiinpocket.n3n.auth.repository.UserRoleRepository;
import com.aiinpocket.n3n.base.BaseServiceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminEmailBinderTest extends BaseServiceTest {

    @Mock
    private UserRoleRepository userRoleRepository;

    @InjectMocks
    private AdminEmailBinder adminEmailBinder;

    private User boundUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adminEmailBinder, "adminEmails", "mopacke2422@gmail.com, other@admin.io");
        boundUser = User.builder()
                .id(UUID.randomUUID())
                .email("mopacke2422@gmail.com")
                .name("Bound Admin")
                .status("active")
                .build();
    }

    @Test
    @DisplayName("名單內 Email 且尚無 ADMIN 角色時，應授予 ADMIN")
    void ensureAdminRole_boundEmailWithoutAdmin_grantsAdmin() {
        when(userRoleRepository.findByUserId(boundUser.getId()))
                .thenReturn(List.of(UserRole.builder().userId(boundUser.getId()).role("USER").build()));

        boolean granted = adminEmailBinder.ensureAdminRole(boundUser);

        assertThat(granted).isTrue();
        ArgumentCaptor<UserRole> captor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo("ADMIN");
        assertThat(captor.getValue().getUserId()).isEqualTo(boundUser.getId());
    }

    @Test
    @DisplayName("Email 比對不分大小寫")
    void ensureAdminRole_caseInsensitiveMatch_grantsAdmin() {
        User upperCaseUser = User.builder()
                .id(UUID.randomUUID())
                .email("MoPacke2422@Gmail.com")
                .build();
        when(userRoleRepository.findByUserId(upperCaseUser.getId())).thenReturn(List.of());

        boolean granted = adminEmailBinder.ensureAdminRole(upperCaseUser);

        assertThat(granted).isTrue();
        verify(userRoleRepository).save(any(UserRole.class));
    }

    @Test
    @DisplayName("已具 ADMIN 角色時應冪等，不重複授予")
    void ensureAdminRole_alreadyAdmin_isIdempotent() {
        when(userRoleRepository.findByUserId(boundUser.getId()))
                .thenReturn(List.of(UserRole.builder().userId(boundUser.getId()).role("ADMIN").build()));

        boolean granted = adminEmailBinder.ensureAdminRole(boundUser);

        assertThat(granted).isFalse();
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    @DisplayName("不在名單內的 Email 不做任何事")
    void ensureAdminRole_unboundEmail_doesNothing() {
        User stranger = User.builder()
                .id(UUID.randomUUID())
                .email("stranger@example.com")
                .build();

        boolean granted = adminEmailBinder.ensureAdminRole(stranger);

        assertThat(granted).isFalse();
        verify(userRoleRepository, never()).findByUserId(any());
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    @DisplayName("名單為空或使用者資訊不完整時安全跳過")
    void ensureAdminRole_blankConfigOrNullUser_doesNothing() {
        ReflectionTestUtils.setField(adminEmailBinder, "adminEmails", "");
        assertThat(adminEmailBinder.ensureAdminRole(boundUser)).isFalse();

        ReflectionTestUtils.setField(adminEmailBinder, "adminEmails", "mopacke2422@gmail.com");
        assertThat(adminEmailBinder.ensureAdminRole(null)).isFalse();
        assertThat(adminEmailBinder.ensureAdminRole(User.builder().id(UUID.randomUUID()).build())).isFalse();

        verify(userRoleRepository, never()).save(any());
    }
}
