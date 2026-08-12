package com.aiinpocket.n3n.flow.service;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.flow.dto.ClaimShareLinkResponse;
import com.aiinpocket.n3n.flow.dto.CreateShareLinkRequest;
import com.aiinpocket.n3n.flow.dto.ShareLinkResponse;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowShare;
import com.aiinpocket.n3n.flow.entity.FlowShareLink;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowShareLinkRepository;
import com.aiinpocket.n3n.flow.repository.FlowShareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FlowShareLinkServiceTest extends BaseServiceTest {

    @Mock
    private FlowShareLinkRepository shareLinkRepository;

    @Mock
    private FlowShareRepository flowShareRepository;

    @Mock
    private FlowRepository flowRepository;

    @InjectMocks
    private FlowShareLinkService service;

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

    private FlowShareLink activeLink(String permission) {
        return FlowShareLink.builder()
            .id(UUID.randomUUID())
            .flowId(flowId)
            .token("test-token")
            .permission(permission)
            .createdBy(ownerId)
            .createdAt(Instant.now())
            .build();
    }

    @Nested
    @DisplayName("Create Share Link")
    class CreateLink {

        @Test
        void createShareLink_asOwner_createsLinkWithToken() {
            CreateShareLinkRequest request = new CreateShareLinkRequest();
            request.setPermission("edit");

            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(shareLinkRepository.save(any(FlowShareLink.class))).thenAnswer(inv -> {
                FlowShareLink l = inv.getArgument(0);
                l.setId(UUID.randomUUID());
                return l;
            });

            ShareLinkResponse response = service.createShareLink(flowId, request, ownerId);

            assertThat(response.getToken()).isNotBlank();
            // 32 bytes base64url without padding = 43 chars
            assertThat(response.getToken()).hasSize(43);
            assertThat(response.getPermission()).isEqualTo("edit");
            assertThat(response.getExpiresAt()).isNull();
            assertThat(response.getUrl()).isEqualTo("/share/" + response.getToken());
        }

        @Test
        void createShareLink_withExpiry_setsExpiresAt() {
            CreateShareLinkRequest request = new CreateShareLinkRequest();
            request.setPermission("view");
            request.setExpiresInDays(7);

            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(shareLinkRepository.save(any(FlowShareLink.class))).thenAnswer(inv -> inv.getArgument(0));

            ShareLinkResponse response = service.createShareLink(flowId, request, ownerId);

            assertThat(response.getExpiresAt()).isNotNull();
            assertThat(response.getExpiresAt()).isAfter(Instant.now().plus(6, ChronoUnit.DAYS));
        }

        @Test
        void createShareLink_asAdminSharee_isAllowed() {
            CreateShareLinkRequest request = new CreateShareLinkRequest();
            request.setPermission("view");

            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(flowShareRepository.findPermissionByFlowIdAndUserId(flowId, otherUserId))
                .thenReturn(Optional.of("admin"));
            when(shareLinkRepository.save(any(FlowShareLink.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThatCode(() -> service.createShareLink(flowId, request, otherUserId))
                .doesNotThrowAnyException();
        }

        @Test
        void createShareLink_withoutPermission_throws() {
            CreateShareLinkRequest request = new CreateShareLinkRequest();

            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(flowShareRepository.findPermissionByFlowIdAndUserId(flowId, otherUserId))
                .thenReturn(Optional.of("edit"));

            assertThatThrownBy(() -> service.createShareLink(flowId, request, otherUserId))
                .isInstanceOf(IllegalArgumentException.class);
            verify(shareLinkRepository, never()).save(any());
        }

        @Test
        void createShareLink_flowNotFound_throws() {
            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createShareLink(flowId, new CreateShareLinkRequest(), ownerId))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("List Share Links")
    class ListLinks {

        @Test
        void listShareLinks_filtersExpiredLinks() {
            FlowShareLink active = activeLink("view");
            FlowShareLink expired = activeLink("view");
            expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));

            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(shareLinkRepository.findByFlowIdAndRevokedAtIsNullOrderByCreatedAtDesc(flowId))
                .thenReturn(List.of(active, expired));

            List<ShareLinkResponse> result = service.listShareLinks(flowId, ownerId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(active.getId());
        }
    }

    @Nested
    @DisplayName("Revoke Share Link")
    class RevokeLink {

        @Test
        void revokeShareLink_setsRevokedAt() {
            FlowShareLink link = activeLink("view");

            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(shareLinkRepository.findById(link.getId())).thenReturn(Optional.of(link));

            service.revokeShareLink(flowId, link.getId(), ownerId);

            verify(shareLinkRepository).save(argThat(l -> l.getRevokedAt() != null));
        }

        @Test
        void revokeShareLink_wrongFlow_throwsNotFound() {
            FlowShareLink link = activeLink("view");
            link.setFlowId(UUID.randomUUID());

            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(shareLinkRepository.findById(link.getId())).thenReturn(Optional.of(link));

            assertThatThrownBy(() -> service.revokeShareLink(flowId, link.getId(), ownerId))
                .isInstanceOf(ResourceNotFoundException.class);
            verify(shareLinkRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Claim Share Link")
    class ClaimLink {

        @Test
        void claimShareLink_newUser_createsShare() {
            FlowShareLink link = activeLink("edit");

            when(shareLinkRepository.findByToken("test-token")).thenReturn(Optional.of(link));
            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(flowShareRepository.findByFlowIdAndUserId(flowId, otherUserId)).thenReturn(Optional.empty());
            when(flowShareRepository.save(any(FlowShare.class))).thenAnswer(inv -> inv.getArgument(0));

            ClaimShareLinkResponse response = service.claimShareLink("test-token", otherUserId);

            assertThat(response.getFlowId()).isEqualTo(flowId);
            assertThat(response.getPermission()).isEqualTo("edit");
            assertThat(response.getFlowName()).isEqualTo("Test Flow");
            verify(flowShareRepository).save(argThat(share ->
                share.getUserId().equals(otherUserId) &&
                "edit".equals(share.getPermission()) &&
                share.getSharedBy().equals(ownerId) &&
                share.getAcceptedAt() != null
            ));
        }

        @Test
        void claimShareLink_invalidToken_throwsGenericNotFound() {
            when(shareLinkRepository.findByToken("bad-token")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.claimShareLink("bad-token", otherUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Share link not found");
        }

        @Test
        void claimShareLink_revokedLink_throwsGenericNotFound() {
            FlowShareLink link = activeLink("view");
            link.setRevokedAt(Instant.now());

            when(shareLinkRepository.findByToken("test-token")).thenReturn(Optional.of(link));

            assertThatThrownBy(() -> service.claimShareLink("test-token", otherUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Share link not found");
        }

        @Test
        void claimShareLink_expiredLink_throwsGenericNotFound() {
            FlowShareLink link = activeLink("view");
            link.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));

            when(shareLinkRepository.findByToken("test-token")).thenReturn(Optional.of(link));

            assertThatThrownBy(() -> service.claimShareLink("test-token", otherUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Share link not found");
        }

        @Test
        void claimShareLink_neverDowngradesExistingPermission() {
            FlowShareLink link = activeLink("view");
            FlowShare existing = FlowShare.builder()
                .flowId(flowId)
                .userId(otherUserId)
                .permission("admin")
                .sharedBy(ownerId)
                .build();

            when(shareLinkRepository.findByToken("test-token")).thenReturn(Optional.of(link));
            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(flowShareRepository.findByFlowIdAndUserId(flowId, otherUserId)).thenReturn(Optional.of(existing));

            ClaimShareLinkResponse response = service.claimShareLink("test-token", otherUserId);

            assertThat(response.getPermission()).isEqualTo("admin");
            verify(flowShareRepository, never()).save(any());
        }

        @Test
        void claimShareLink_upgradesLowerPermission() {
            FlowShareLink link = activeLink("edit");
            FlowShare existing = FlowShare.builder()
                .flowId(flowId)
                .userId(otherUserId)
                .permission("view")
                .sharedBy(ownerId)
                .build();

            when(shareLinkRepository.findByToken("test-token")).thenReturn(Optional.of(link));
            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));
            when(flowShareRepository.findByFlowIdAndUserId(flowId, otherUserId)).thenReturn(Optional.of(existing));
            when(flowShareRepository.save(any(FlowShare.class))).thenAnswer(inv -> inv.getArgument(0));

            ClaimShareLinkResponse response = service.claimShareLink("test-token", otherUserId);

            assertThat(response.getPermission()).isEqualTo("edit");
            verify(flowShareRepository).save(argThat(share -> "edit".equals(share.getPermission())));
        }

        @Test
        void claimShareLink_ownerClaimingOwnLink_isNoOp() {
            FlowShareLink link = activeLink("edit");

            when(shareLinkRepository.findByToken("test-token")).thenReturn(Optional.of(link));
            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.of(testFlow));

            ClaimShareLinkResponse response = service.claimShareLink("test-token", ownerId);

            assertThat(response.getPermission()).isEqualTo("owner");
            assertThat(response.getFlowId()).isEqualTo(flowId);
            verify(flowShareRepository, never()).save(any());
            verify(flowShareRepository, never()).findByFlowIdAndUserId(any(), any());
        }

        @Test
        void claimShareLink_deletedFlow_throwsGenericNotFound() {
            FlowShareLink link = activeLink("view");

            when(shareLinkRepository.findByToken("test-token")).thenReturn(Optional.of(link));
            when(flowRepository.findByIdAndIsDeletedFalse(flowId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.claimShareLink("test-token", otherUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Share link not found");
        }
    }
}
