package com.aiinpocket.n3n.flow.service;

import com.aiinpocket.n3n.auth.event.UserAuthenticatedEvent;
import com.aiinpocket.n3n.base.BaseServiceTest;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class PendingInvitationListenerTest extends BaseServiceTest {

    @Mock
    private FlowShareService flowShareService;

    @InjectMocks
    private PendingInvitationListener listener;

    @Test
    void onUserAuthenticated_acceptsPendingInvitations() {
        UUID userId = UUID.randomUUID();
        String email = "invited@example.com";

        listener.onUserAuthenticated(new UserAuthenticatedEvent(userId, email));

        verify(flowShareService).acceptPendingInvitations(userId, email);
    }

    @Test
    void onUserAuthenticated_swallowsExceptions() {
        UUID userId = UUID.randomUUID();
        doThrow(new RuntimeException("db down"))
            .when(flowShareService).acceptPendingInvitations(any(), any());

        assertThatCode(() ->
            listener.onUserAuthenticated(new UserAuthenticatedEvent(userId, "x@example.com")))
            .doesNotThrowAnyException();
    }
}
