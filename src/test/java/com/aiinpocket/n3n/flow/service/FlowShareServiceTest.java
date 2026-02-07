package com.aiinpocket.n3n.flow.service;

import com.aiinpocket.n3n.auth.entity.User;
import com.aiinpocket.n3n.auth.repository.UserRepository;
import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.common.service.EmailService;
import com.aiinpocket.n3n.flow.dto.FlowShareRequest;
import com.aiinpocket.n3n.flow.dto.FlowShareResponse;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowShare;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowShareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FlowShareServiceTest extends BaseServiceTest {

    @Mock
    private FlowShareRepository flowShareRepository;

    @Mock
    private FlowRepository flowRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private FlowShareService flowShareService;

    private UUID ownerId;
    private UUID flowId;
    private UUID otherUserId;
    private Flow testFlow;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        flowId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        testFlow = Flow.builder()
            .id(flowId)
            .name("Test Flow")
            .createdBy(ownerId)
            .build();
    }

    @Nested
    @DisplayName("Share Flow")
    class ShareFlow {

        @Test
        void shareFlow_withUserId_sharesDirectly() {
            User targetUser = User.builder().id(otherUserId).email("other@test.com").name("Other").build();
            FlowShareRequest request = new FlowShareRequest();
            request.setUserId(otherUserId);
            request.setPermission("edit");

            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(targetUser));
            when(flowShareRepository.findByFlowIdAndUserId(flowId, otherUserId)).thenReturn(Optional.empty());
            when(flowShareRepository.save(any(FlowShare.class))).thenAnswer(inv -> {
                FlowShare s = inv.getArgument(0);
                s.setId(UUID.randomUUID());
                return s;
            });

            FlowShareResponse result = flowShareService.shareFlow(flowId, request, ownerId);

            assertThat(result).isNotNull();
            verify(flowShareRepository).save(argThat(share ->
                share.getFlowId().equals(flowId) &&
                share.getUserId().equals(otherUserId) &&
                "edit".equals(share.getPermission())
            ));
        }

        @Test
        void shareFlow_alreadyShared_throwsException() {
            FlowShareRequest request = new FlowShareRequest();
            request.setUserId(otherUserId);
            request.setPermission("view");

            User targetUser = User.builder().id(otherUserId).email("other@test.com").name("Other").build();
            FlowShare existingShare = FlowShare.builder().flowId(flowId).userId(otherUserId).build();

            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(userRepository.findById(otherUserId)).thenReturn(Optional.of(targetUser));
            when(flowShareRepository.findByFlowIdAndUserId(flowId, otherUserId)).thenReturn(Optional.of(existingShare));

            assertThatThrownBy(() -> flowShareService.shareFlow(flowId, request, ownerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already shared");
        }

        @Test
        void shareFlow_nonExistingFlow_throwsException() {
            FlowShareRequest request = new FlowShareRequest();
            request.setUserId(otherUserId);
            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> flowShareService.shareFlow(flowId, request, ownerId))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void shareFlow_notOwner_throwsException() {
            UUID nonOwnerId = UUID.randomUUID();
            FlowShareRequest request = new FlowShareRequest();
            request.setUserId(otherUserId);

            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(flowShareRepository.findPermissionByFlowIdAndUserId(flowId, nonOwnerId))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> flowShareService.shareFlow(flowId, request, nonOwnerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permission");
        }

        @Test
        void shareFlow_byEmail_existingUser_sharesDirectly() {
            User existingUser = User.builder().id(otherUserId).email("existing@test.com").name("Existing").build();
            FlowShareRequest request = new FlowShareRequest();
            request.setEmail("existing@test.com");
            request.setPermission("view");

            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(userRepository.findByEmail("existing@test.com")).thenReturn(Optional.of(existingUser));
            when(flowShareRepository.findByFlowIdAndUserId(flowId, otherUserId)).thenReturn(Optional.empty());
            when(flowShareRepository.save(any(FlowShare.class))).thenAnswer(inv -> {
                FlowShare s = inv.getArgument(0);
                s.setId(UUID.randomUUID());
                return s;
            });
            when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(
                User.builder().id(ownerId).name("Owner").build()
            ));

            flowShareService.shareFlow(flowId, request, ownerId);

            verify(flowShareRepository).save(argThat(s -> s.getUserId().equals(otherUserId)));
        }

        @Test
        void shareFlow_byEmail_newUser_sendsInvitation() {
            FlowShareRequest request = new FlowShareRequest();
            request.setEmail("newuser@test.com");
            request.setPermission("view");

            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(userRepository.findByEmail("newuser@test.com")).thenReturn(Optional.empty());
            when(flowShareRepository.findByFlowIdAndInvitedEmail(flowId, "newuser@test.com")).thenReturn(Optional.empty());
            when(flowShareRepository.save(any(FlowShare.class))).thenAnswer(inv -> {
                FlowShare s = inv.getArgument(0);
                s.setId(UUID.randomUUID());
                return s;
            });
            when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(
                User.builder().id(ownerId).name("Owner").build()
            ));

            flowShareService.shareFlow(flowId, request, ownerId);

            verify(flowShareRepository).save(argThat(s -> "newuser@test.com".equals(s.getInvitedEmail())));
            verify(emailService).sendFlowInvitation(eq("newuser@test.com"), eq("Test Flow"), eq("Owner"));
        }

        @Test
        void shareFlow_noUserIdOrEmail_throwsException() {
            FlowShareRequest request = new FlowShareRequest();

            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));

            assertThatThrownBy(() -> flowShareService.shareFlow(flowId, request, ownerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId or email");
        }
    }

    @Nested
    @DisplayName("Has Access")
    class HasAccess {

        @Test
        void hasAccess_owner_returnsTrue() {
            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));

            boolean result = flowShareService.hasAccess(flowId, ownerId);

            assertThat(result).isTrue();
        }

        @Test
        void hasAccess_sharedUser_returnsTrue() {
            FlowShare share = FlowShare.builder().flowId(flowId).userId(otherUserId).build();
            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(flowShareRepository.findByFlowIdAndUserId(flowId, otherUserId)).thenReturn(Optional.of(share));

            boolean result = flowShareService.hasAccess(flowId, otherUserId);

            assertThat(result).isTrue();
        }

        @Test
        void hasAccess_nonSharedUser_returnsFalse() {
            UUID randomUser = UUID.randomUUID();
            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(flowShareRepository.findByFlowIdAndUserId(flowId, randomUser)).thenReturn(Optional.empty());

            boolean result = flowShareService.hasAccess(flowId, randomUser);

            assertThat(result).isFalse();
        }

        @Test
        void hasAccess_nonExistingFlow_returnsFalse() {
            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.empty());

            boolean result = flowShareService.hasAccess(flowId, ownerId);

            assertThat(result).isFalse();
        }

        @Test
        void hasAccess_publicFlow_returnsTrue() {
            testFlow.setVisibility("public");
            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));

            boolean result = flowShareService.hasAccess(flowId, UUID.randomUUID());

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("Has Edit Access")
    class HasEditAccess {

        @Test
        void hasEditAccess_owner_returnsTrue() {
            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));

            boolean result = flowShareService.hasEditAccess(flowId, ownerId);

            assertThat(result).isTrue();
        }

        @Test
        void hasEditAccess_nonExistingFlow_returnsFalse() {
            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.empty());

            boolean result = flowShareService.hasEditAccess(flowId, ownerId);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Remove Share")
    class RemoveShare {

        @Test
        void removeShare_ownerRemoves_deletesShare() {
            UUID shareId = UUID.randomUUID();
            FlowShare share = FlowShare.builder().id(shareId).flowId(flowId).userId(otherUserId).build();

            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(flowShareRepository.findById(shareId)).thenReturn(Optional.of(share));

            flowShareService.removeShare(flowId, shareId, ownerId);

            verify(flowShareRepository).delete(share);
        }

        @Test
        void removeShare_wrongFlow_throwsException() {
            UUID shareId = UUID.randomUUID();
            UUID otherFlowId = UUID.randomUUID();
            FlowShare share = FlowShare.builder().id(shareId).flowId(otherFlowId).userId(otherUserId).build();

            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(flowShareRepository.findById(shareId)).thenReturn(Optional.of(share));

            assertThatThrownBy(() -> flowShareService.removeShare(flowId, shareId, ownerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
        }
    }

    @Nested
    @DisplayName("Accept Pending Invitations")
    class AcceptPendingInvitations {

        @Test
        void acceptPendingInvitations_updatesPendingShares() {
            FlowShare pending = FlowShare.builder()
                .id(UUID.randomUUID())
                .flowId(flowId)
                .invitedEmail("newuser@test.com")
                .permission("view")
                .sharedBy(ownerId)
                .build();

            when(flowShareRepository.findByInvitedEmailAndAcceptedAtIsNull("newuser@test.com"))
                .thenReturn(List.of(pending));

            flowShareService.acceptPendingInvitations(otherUserId, "newuser@test.com");

            verify(flowShareRepository).save(argThat(s ->
                s.getUserId().equals(otherUserId) && s.getAcceptedAt() != null
            ));
        }

        @Test
        void acceptPendingInvitations_noPending_doesNothing() {
            when(flowShareRepository.findByInvitedEmailAndAcceptedAtIsNull("nobody@test.com"))
                .thenReturn(List.of());

            flowShareService.acceptPendingInvitations(otherUserId, "nobody@test.com");

            verify(flowShareRepository, never()).save(any());
        }
    }
}
