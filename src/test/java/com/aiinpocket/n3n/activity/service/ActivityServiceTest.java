package com.aiinpocket.n3n.activity.service;

import com.aiinpocket.n3n.activity.entity.UserActivity;
import com.aiinpocket.n3n.activity.repository.UserActivityRepository;
import com.aiinpocket.n3n.base.BaseServiceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ActivityServiceTest extends BaseServiceTest {

    @Mock
    private UserActivityRepository activityRepository;

    @InjectMocks
    private ActivityService activityService;

    private UUID userId;
    private UUID resourceId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        resourceId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("Activity Constants")
    class ActivityConstants {

        @Test
        void authConstants_areDefinedCorrectly() {
            assertThat(ActivityService.LOGIN).isEqualTo("LOGIN");
            assertThat(ActivityService.LOGIN_FAILED).isEqualTo("LOGIN_FAILED");
            assertThat(ActivityService.LOGOUT).isEqualTo("LOGOUT");
            assertThat(ActivityService.TOKEN_REFRESH).isEqualTo("TOKEN_REFRESH");
        }

        @Test
        void flowConstants_areDefinedCorrectly() {
            assertThat(ActivityService.FLOW_CREATE).isEqualTo("FLOW_CREATE");
            assertThat(ActivityService.FLOW_UPDATE).isEqualTo("FLOW_UPDATE");
            assertThat(ActivityService.FLOW_DELETE).isEqualTo("FLOW_DELETE");
            assertThat(ActivityService.FLOW_PUBLISH).isEqualTo("FLOW_PUBLISH");
        }

        @Test
        void executionConstants_areDefinedCorrectly() {
            assertThat(ActivityService.EXECUTION_START).isEqualTo("EXECUTION_START");
            assertThat(ActivityService.EXECUTION_COMPLETE).isEqualTo("EXECUTION_COMPLETE");
            assertThat(ActivityService.EXECUTION_FAIL).isEqualTo("EXECUTION_FAIL");
            assertThat(ActivityService.EXECUTION_CANCEL).isEqualTo("EXECUTION_CANCEL");
        }

        @Test
        void credentialConstants_areDefinedCorrectly() {
            assertThat(ActivityService.CREDENTIAL_CREATE).isEqualTo("CREDENTIAL_CREATE");
            assertThat(ActivityService.CREDENTIAL_DELETE).isEqualTo("CREDENTIAL_DELETE");
            assertThat(ActivityService.CREDENTIAL_ACCESS).isEqualTo("CREDENTIAL_ACCESS");
        }

        @Test
        void webhookConstants_areDefinedCorrectly() {
            assertThat(ActivityService.WEBHOOK_CREATE).isEqualTo("WEBHOOK_CREATE");
            assertThat(ActivityService.WEBHOOK_TRIGGER).isEqualTo("WEBHOOK_TRIGGER");
            assertThat(ActivityService.WEBHOOK_TRIGGER_FAILED).isEqualTo("WEBHOOK_TRIGGER_FAILED");
        }
    }

    @Nested
    @DisplayName("Log Activity")
    class LogActivity {

        @Test
        void logActivity_savesToRepository() {
            activityService.logActivity(userId, ActivityService.LOGIN, "user", userId, "test@example.com", null);

            verify(activityRepository).save(argThat(activity ->
                activity.getUserId().equals(userId) &&
                activity.getActivityType().equals("LOGIN") &&
                activity.getResourceType().equals("user") &&
                activity.getResourceName().equals("test@example.com")
            ));
        }

        @Test
        void logActivity_withDetails_savesDetails() {
            Map<String, Object> details = Map.of("reason", "test");

            activityService.logActivity(userId, ActivityService.LOGIN_FAILED, "user", null, "test@example.com", details);

            verify(activityRepository).save(argThat(activity ->
                activity.getDetails() != null && activity.getDetails().containsKey("reason")
            ));
        }

        @Test
        void logActivity_withNullUserId_savesSuccessfully() {
            activityService.logActivity(null, ActivityService.WEBHOOK_TRIGGER, "webhook", resourceId, "/api/test", null);

            verify(activityRepository).save(argThat(activity ->
                activity.getUserId() == null
            ));
        }
    }

    @Nested
    @DisplayName("Auth Logging")
    class AuthLogging {

        @Test
        void logLogin_savesLoginActivity() {
            activityService.logLogin(userId, "test@example.com");

            verify(activityRepository).save(argThat(activity ->
                activity.getActivityType().equals("LOGIN") &&
                activity.getResourceName().equals("test@example.com")
            ));
        }

        @Test
        void logLoginFailed_savesFailedLoginActivity() {
            activityService.logLoginFailed("test@example.com", "invalid password");

            verify(activityRepository).save(argThat(activity ->
                activity.getActivityType().equals("LOGIN_FAILED") &&
                activity.getDetails().containsKey("reason")
            ));
        }

        @Test
        void logLogout_savesLogoutActivity() {
            activityService.logLogout(userId, "test@example.com");

            verify(activityRepository).save(argThat(activity ->
                activity.getActivityType().equals("LOGOUT")
            ));
        }

        @Test
        void logTokenRefresh_savesRefreshActivity() {
            activityService.logTokenRefresh(userId);

            verify(activityRepository).save(argThat(activity ->
                activity.getActivityType().equals("TOKEN_REFRESH")
            ));
        }
    }

    @Nested
    @DisplayName("Flow Logging")
    class FlowLogging {

        @Test
        void logFlowCreate_savesFlowCreateActivity() {
            UUID flowId = UUID.randomUUID();
            activityService.logFlowCreate(userId, flowId, "My Flow");

            verify(activityRepository).save(argThat(activity ->
                activity.getActivityType().equals("FLOW_CREATE") &&
                activity.getResourceId().equals(flowId) &&
                activity.getResourceName().equals("My Flow")
            ));
        }

        @Test
        void logFlowDelete_savesFlowDeleteActivity() {
            UUID flowId = UUID.randomUUID();
            activityService.logFlowDelete(userId, flowId, "My Flow");

            verify(activityRepository).save(argThat(activity ->
                activity.getActivityType().equals("FLOW_DELETE")
            ));
        }

        @Test
        void logFlowPublish_savesPublishActivity() {
            UUID flowId = UUID.randomUUID();
            activityService.logFlowPublish(userId, flowId, "My Flow", "1.0.0");

            verify(activityRepository).save(argThat(activity ->
                activity.getActivityType().equals("FLOW_PUBLISH") &&
                activity.getDetails().containsKey("version")
            ));
        }
    }

    @Nested
    @DisplayName("Execution Logging")
    class ExecutionLogging {

        @Test
        void logExecutionStart_savesStartActivity() {
            UUID execId = UUID.randomUUID();
            UUID flowId = UUID.randomUUID();
            activityService.logExecutionStart(userId, execId, flowId, "My Flow", "manual");

            verify(activityRepository).save(argThat(activity ->
                activity.getActivityType().equals("EXECUTION_START") &&
                activity.getDetails().containsKey("triggerType")
            ));
        }

        @Test
        void logExecutionComplete_savesCompleteActivity() {
            UUID execId = UUID.randomUUID();
            UUID flowId = UUID.randomUUID();
            activityService.logExecutionComplete(userId, execId, flowId, 1500);

            verify(activityRepository).save(argThat(activity ->
                activity.getActivityType().equals("EXECUTION_COMPLETE") &&
                activity.getDetails().containsKey("durationMs")
            ));
        }

        @Test
        void logExecutionFail_savesFailActivity() {
            UUID execId = UUID.randomUUID();
            UUID flowId = UUID.randomUUID();
            activityService.logExecutionFail(userId, execId, flowId, "Connection timeout");

            verify(activityRepository).save(argThat(activity ->
                activity.getActivityType().equals("EXECUTION_FAIL") &&
                activity.getDetails().containsKey("error")
            ));
        }
    }

    @Nested
    @DisplayName("Query Activities")
    class QueryActivities {

        @Test
        void getUserActivities_delegatesToRepository() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<UserActivity> page = new PageImpl<>(List.of());
            when(activityRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)).thenReturn(page);

            Page<UserActivity> result = activityService.getUserActivities(userId, pageable);

            assertThat(result).isNotNull();
            verify(activityRepository).findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        @Test
        void getResourceActivities_delegatesToRepository() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<UserActivity> page = new PageImpl<>(List.of());
            when(activityRepository.findByResourceTypeAndResourceIdOrderByCreatedAtDesc("flow", resourceId, pageable))
                .thenReturn(page);

            Page<UserActivity> result = activityService.getResourceActivities("flow", resourceId, pageable);

            assertThat(result).isNotNull();
            verify(activityRepository).findByResourceTypeAndResourceIdOrderByCreatedAtDesc("flow", resourceId, pageable);
        }

        @Test
        void getAllActivities_delegatesToRepository() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<UserActivity> page = new PageImpl<>(List.of());
            when(activityRepository.findAllByOrderByCreatedAtDesc(pageable)).thenReturn(page);

            Page<UserActivity> result = activityService.getAllActivities(pageable);

            assertThat(result).isNotNull();
            verify(activityRepository).findAllByOrderByCreatedAtDesc(pageable);
        }

        @Test
        void getActivitiesByType_delegatesToRepository() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<UserActivity> page = new PageImpl<>(List.of());
            when(activityRepository.findByActivityTypeOrderByCreatedAtDesc("LOGIN", pageable)).thenReturn(page);

            Page<UserActivity> result = activityService.getActivitiesByType("LOGIN", pageable);

            assertThat(result).isNotNull();
            verify(activityRepository).findByActivityTypeOrderByCreatedAtDesc("LOGIN", pageable);
        }
    }

    @Nested
    @DisplayName("Credential Logging")
    class CredentialLogging {

        @Test
        void logCredentialCreate_savesActivity() {
            UUID credId = UUID.randomUUID();
            activityService.logCredentialCreate(userId, credId, "My API Key", "api_key");

            verify(activityRepository).save(argThat(activity ->
                activity.getActivityType().equals("CREDENTIAL_CREATE") &&
                activity.getDetails().containsKey("type")
            ));
        }

        @Test
        void logCredentialDelete_savesActivity() {
            UUID credId = UUID.randomUUID();
            activityService.logCredentialDelete(userId, credId, "My API Key");

            verify(activityRepository).save(argThat(activity ->
                activity.getActivityType().equals("CREDENTIAL_DELETE")
            ));
        }
    }

    @Nested
    @DisplayName("Webhook Logging")
    class WebhookLogging {

        @Test
        void logWebhookCreate_savesActivity() {
            UUID webhookId = UUID.randomUUID();
            UUID flowId = UUID.randomUUID();
            activityService.logWebhookCreate(userId, webhookId, "/api/hook", flowId);

            verify(activityRepository).save(argThat(activity ->
                activity.getActivityType().equals("WEBHOOK_CREATE") &&
                activity.getResourceName().equals("/api/hook")
            ));
        }

        @Test
        void logWebhookTrigger_savesActivity() {
            UUID execId = UUID.randomUUID();
            activityService.logWebhookTrigger("/api/hook", execId, "192.168.1.1", "Mozilla", 256);

            verify(activityRepository).save(argThat(activity ->
                activity.getActivityType().equals("WEBHOOK_TRIGGER") &&
                activity.getDetails().containsKey("sourceIp")
            ));
        }
    }

    @Nested
    @DisplayName("Admin Logging")
    class AdminLogging {

        @Test
        void logAdminAction_savesWithMergedDetails() {
            Map<String, Object> details = Map.of("reason", "maintenance");
            activityService.logAdminAction(userId, "SUSPEND_USER", "user", resourceId, details);

            verify(activityRepository).save(argThat(activity ->
                activity.getActivityType().equals("ADMIN_ACTION") &&
                activity.getDetails().containsKey("action") &&
                activity.getDetails().containsKey("reason")
            ));
        }

        @Test
        void logAdminAction_withNullDetails_savesOnlyAction() {
            activityService.logAdminAction(userId, "VIEW_LOGS", "system", null, null);

            verify(activityRepository).save(argThat(activity ->
                activity.getDetails().containsKey("action")
            ));
        }
    }
}
