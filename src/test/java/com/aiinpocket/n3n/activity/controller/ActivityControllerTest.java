package com.aiinpocket.n3n.activity.controller;

import com.aiinpocket.n3n.activity.dto.UserActivityResponse;
import com.aiinpocket.n3n.activity.entity.UserActivity;
import com.aiinpocket.n3n.activity.service.ActivityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityControllerTest {

    @Mock
    private ActivityService activityService;

    @InjectMocks
    private ActivityController activityController;

    private UserDetails testUser() {
        return User.withUsername(UUID.randomUUID().toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private UserDetails testUser(UUID userId) {
        return User.withUsername(userId.toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private UserDetails testAdmin() {
        return User.withUsername(UUID.randomUUID().toString())
                .password("test")
                .authorities("ROLE_ADMIN")
                .build();
    }

    private UserActivity sampleActivity(UUID userId, String activityType, String resourceType) {
        return UserActivity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .activityType(activityType)
                .resourceType(resourceType)
                .resourceId(UUID.randomUUID())
                .resourceName("test-resource")
                .details(Map.of("key", "value"))
                .ipAddress("127.0.0.1")
                .createdAt(Instant.now())
                .build();
    }

    private UserActivity sampleActivity(UUID userId) {
        return sampleActivity(userId, "FLOW_CREATE", "flow");
    }

    // ==========================================================
    // listAllActivities (GET /api/activities) - admin only
    // ==========================================================

    @Test
    void listAllActivities_noTypeFilter_returnsAllActivities() {
        var activity = sampleActivity(UUID.randomUUID());
        Page<UserActivity> page = new PageImpl<>(List.of(activity));
        when(activityService.getAllActivities(any(Pageable.class))).thenReturn(page);

        var result = activityController.listAllActivities(null, Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isEqualTo(1);
        verify(activityService).getAllActivities(any(Pageable.class));
        verify(activityService, never()).getActivitiesByType(any(), any());
    }

    @Test
    void listAllActivities_blankTypeFilter_returnsAllActivities() {
        var activity = sampleActivity(UUID.randomUUID());
        Page<UserActivity> page = new PageImpl<>(List.of(activity));
        when(activityService.getAllActivities(any(Pageable.class))).thenReturn(page);

        var result = activityController.listAllActivities("   ", Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isEqualTo(1);
        verify(activityService).getAllActivities(any(Pageable.class));
        verify(activityService, never()).getActivitiesByType(any(), any());
    }

    @Test
    void listAllActivities_emptyStringTypeFilter_returnsAllActivities() {
        Page<UserActivity> page = new PageImpl<>(List.of(sampleActivity(UUID.randomUUID())));
        when(activityService.getAllActivities(any(Pageable.class))).thenReturn(page);

        var result = activityController.listAllActivities("", Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(activityService).getAllActivities(any(Pageable.class));
        verify(activityService, never()).getActivitiesByType(any(), any());
    }

    @Test
    void listAllActivities_withTypeFilter_returnsFilteredActivities() {
        var userId = UUID.randomUUID();
        var activity = sampleActivity(userId, "LOGIN", "user");
        Page<UserActivity> page = new PageImpl<>(List.of(activity));
        when(activityService.getActivitiesByType(eq("LOGIN"), any(Pageable.class))).thenReturn(page);

        var result = activityController.listAllActivities("LOGIN", Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isEqualTo(1);
        assertThat(result.getBody().getContent().get(0).activityType()).isEqualTo("LOGIN");
        verify(activityService).getActivitiesByType(eq("LOGIN"), any(Pageable.class));
        verify(activityService, never()).getAllActivities(any());
    }

    @Test
    void listAllActivities_emptyPage_returnsOk() {
        when(activityService.getAllActivities(any(Pageable.class))).thenReturn(Page.empty());

        var result = activityController.listAllActivities(null, Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isZero();
        assertThat(result.getBody().getContent()).isEmpty();
    }

    @Test
    void listAllActivities_withPagination_passesPageableToService() {
        var pageable = PageRequest.of(2, 10);
        when(activityService.getAllActivities(eq(pageable))).thenReturn(Page.empty(pageable));

        var result = activityController.listAllActivities(null, pageable);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(activityService).getAllActivities(eq(pageable));
    }

    @Test
    void listAllActivities_multipleActivities_returnsAll() {
        var userId1 = UUID.randomUUID();
        var userId2 = UUID.randomUUID();
        var a1 = sampleActivity(userId1, "LOGIN", "user");
        var a2 = sampleActivity(userId2, "FLOW_CREATE", "flow");
        var a3 = sampleActivity(userId1, "EXECUTION_START", "execution");
        Page<UserActivity> page = new PageImpl<>(List.of(a1, a2, a3));
        when(activityService.getAllActivities(any(Pageable.class))).thenReturn(page);

        var result = activityController.listAllActivities(null, Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getTotalElements()).isEqualTo(3);
        assertThat(result.getBody().getContent())
                .extracting(UserActivityResponse::activityType)
                .containsExactly("LOGIN", "FLOW_CREATE", "EXECUTION_START");
    }

    @Test
    void listAllActivities_typeFilterReturnsEmpty_returnsEmptyPage() {
        when(activityService.getActivitiesByType(eq("NONEXISTENT"), any(Pageable.class)))
                .thenReturn(Page.empty());

        var result = activityController.listAllActivities("NONEXISTENT", Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getTotalElements()).isZero();
    }

    @Test
    void listAllActivities_mapsEntityToResponse() {
        var userId = UUID.randomUUID();
        var resourceId = UUID.randomUUID();
        var activityId = UUID.randomUUID();
        var now = Instant.now();
        var activity = UserActivity.builder()
                .id(activityId)
                .userId(userId)
                .activityType("FLOW_UPDATE")
                .resourceType("flow")
                .resourceId(resourceId)
                .resourceName("my-flow")
                .details(Map.of("field", "nodes"))
                .ipAddress("192.168.1.1")
                .createdAt(now)
                .build();
        when(activityService.getAllActivities(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(activity)));

        var result = activityController.listAllActivities(null, Pageable.unpaged());

        assertThat(result.getBody()).isNotNull();
        var response = result.getBody().getContent().get(0);
        assertThat(response.id()).isEqualTo(activityId);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.activityType()).isEqualTo("FLOW_UPDATE");
        assertThat(response.resourceType()).isEqualTo("flow");
        assertThat(response.resourceId()).isEqualTo(resourceId);
        assertThat(response.resourceName()).isEqualTo("my-flow");
        assertThat(response.details()).containsEntry("field", "nodes");
        assertThat(response.ipAddress()).isEqualTo("192.168.1.1");
        assertThat(response.createdAt()).isEqualTo(now);
    }

    // ==========================================================
    // listMyActivities (GET /api/activities/my)
    // ==========================================================

    @Test
    void listMyActivities_noTypeFilter_returnsUserActivities() {
        var userId = UUID.randomUUID();
        var user = testUser(userId);
        var activity = sampleActivity(userId);
        Page<UserActivity> page = new PageImpl<>(List.of(activity));
        when(activityService.getUserActivities(eq(userId), any(Pageable.class))).thenReturn(page);

        var result = activityController.listMyActivities(null, user, Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isEqualTo(1);
        verify(activityService).getUserActivities(eq(userId), any(Pageable.class));
        verify(activityService, never()).getUserActivitiesByType(any(), any(), any());
    }

    @Test
    void listMyActivities_blankTypeFilter_returnsUserActivities() {
        var userId = UUID.randomUUID();
        var user = testUser(userId);
        var activity = sampleActivity(userId);
        Page<UserActivity> page = new PageImpl<>(List.of(activity));
        when(activityService.getUserActivities(eq(userId), any(Pageable.class))).thenReturn(page);

        var result = activityController.listMyActivities("  ", user, Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(activityService).getUserActivities(eq(userId), any(Pageable.class));
        verify(activityService, never()).getUserActivitiesByType(any(), any(), any());
    }

    @Test
    void listMyActivities_emptyStringTypeFilter_returnsUserActivities() {
        var userId = UUID.randomUUID();
        var user = testUser(userId);
        when(activityService.getUserActivities(eq(userId), any(Pageable.class))).thenReturn(Page.empty());

        var result = activityController.listMyActivities("", user, Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(activityService).getUserActivities(eq(userId), any(Pageable.class));
        verify(activityService, never()).getUserActivitiesByType(any(), any(), any());
    }

    @Test
    void listMyActivities_withTypeFilter_returnsFilteredActivities() {
        var userId = UUID.randomUUID();
        var user = testUser(userId);
        var activity = sampleActivity(userId, "FLOW_CREATE", "flow");
        Page<UserActivity> page = new PageImpl<>(List.of(activity));
        when(activityService.getUserActivitiesByType(eq(userId), eq("FLOW_CREATE"), any(Pageable.class)))
                .thenReturn(page);

        var result = activityController.listMyActivities("FLOW_CREATE", user, Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isEqualTo(1);
        assertThat(result.getBody().getContent().get(0).activityType()).isEqualTo("FLOW_CREATE");
        verify(activityService).getUserActivitiesByType(eq(userId), eq("FLOW_CREATE"), any(Pageable.class));
        verify(activityService, never()).getUserActivities(any(), any());
    }

    @Test
    void listMyActivities_emptyResult_returnsOk() {
        var userId = UUID.randomUUID();
        var user = testUser(userId);
        when(activityService.getUserActivities(eq(userId), any(Pageable.class))).thenReturn(Page.empty());

        var result = activityController.listMyActivities(null, user, Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isZero();
    }

    @Test
    void listMyActivities_withPagination_passesPageableToService() {
        var userId = UUID.randomUUID();
        var user = testUser(userId);
        var pageable = PageRequest.of(1, 5);
        when(activityService.getUserActivities(eq(userId), eq(pageable))).thenReturn(Page.empty(pageable));

        var result = activityController.listMyActivities(null, user, pageable);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(activityService).getUserActivities(eq(userId), eq(pageable));
    }

    @Test
    void listMyActivities_multipleActivities_returnsAll() {
        var userId = UUID.randomUUID();
        var user = testUser(userId);
        var a1 = sampleActivity(userId, "FLOW_CREATE", "flow");
        var a2 = sampleActivity(userId, "CREDENTIAL_CREATE", "credential");
        Page<UserActivity> page = new PageImpl<>(List.of(a1, a2));
        when(activityService.getUserActivities(eq(userId), any(Pageable.class))).thenReturn(page);

        var result = activityController.listMyActivities(null, user, Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getTotalElements()).isEqualTo(2);
        assertThat(result.getBody().getContent())
                .extracting(UserActivityResponse::activityType)
                .containsExactly("FLOW_CREATE", "CREDENTIAL_CREATE");
    }

    @Test
    void listMyActivities_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUser(userId);
        when(activityService.getUserActivities(eq(userId), any(Pageable.class))).thenReturn(Page.empty());

        activityController.listMyActivities(null, user, Pageable.unpaged());

        verify(activityService).getUserActivities(eq(userId), any(Pageable.class));
    }

    @Test
    void listMyActivities_typeFilterReturnsEmpty_returnsEmptyPage() {
        var userId = UUID.randomUUID();
        var user = testUser(userId);
        when(activityService.getUserActivitiesByType(eq(userId), eq("WEBHOOK_CREATE"), any(Pageable.class)))
                .thenReturn(Page.empty());

        var result = activityController.listMyActivities("WEBHOOK_CREATE", user, Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getTotalElements()).isZero();
    }

    @Test
    void listMyActivities_mapsEntityToResponse() {
        var userId = UUID.randomUUID();
        var user = testUser(userId);
        var resourceId = UUID.randomUUID();
        var activity = UserActivity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .activityType("CREDENTIAL_ACCESS")
                .resourceType("credential")
                .resourceId(resourceId)
                .resourceName("my-cred")
                .details(Map.of("accessedBy", "api"))
                .ipAddress("10.0.0.1")
                .createdAt(Instant.now())
                .build();
        when(activityService.getUserActivities(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(activity)));

        var result = activityController.listMyActivities(null, user, Pageable.unpaged());

        var response = result.getBody().getContent().get(0);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.activityType()).isEqualTo("CREDENTIAL_ACCESS");
        assertThat(response.resourceType()).isEqualTo("credential");
        assertThat(response.resourceId()).isEqualTo(resourceId);
        assertThat(response.resourceName()).isEqualTo("my-cred");
        assertThat(response.details()).containsEntry("accessedBy", "api");
        assertThat(response.ipAddress()).isEqualTo("10.0.0.1");
    }

    // ==========================================================
    // getResourceActivities (GET /api/activities/resource/{resourceType}/{resourceId})
    // ==========================================================

    @Test
    void getResourceActivities_success_returnsActivities() {
        var userId = UUID.randomUUID();
        var user = testUser(userId);
        var resourceId = UUID.randomUUID();
        var activity = sampleActivity(userId, "FLOW_UPDATE", "flow");
        Page<UserActivity> page = new PageImpl<>(List.of(activity));
        when(activityService.getUserResourceActivities(eq(userId), eq("flow"), eq(resourceId), any(Pageable.class)))
                .thenReturn(page);

        var result = activityController.getResourceActivities("flow", resourceId, user, Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isEqualTo(1);
        verify(activityService).getUserResourceActivities(eq(userId), eq("flow"), eq(resourceId), any(Pageable.class));
    }

    @Test
    void getResourceActivities_emptyResult_returnsOk() {
        var userId = UUID.randomUUID();
        var user = testUser(userId);
        var resourceId = UUID.randomUUID();
        when(activityService.getUserResourceActivities(eq(userId), eq("flow"), eq(resourceId), any(Pageable.class)))
                .thenReturn(Page.empty());

        var result = activityController.getResourceActivities("flow", resourceId, user, Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isZero();
    }

    @Test
    void getResourceActivities_withPagination_passesPageableToService() {
        var userId = UUID.randomUUID();
        var user = testUser(userId);
        var resourceId = UUID.randomUUID();
        var pageable = PageRequest.of(0, 15);
        when(activityService.getUserResourceActivities(eq(userId), eq("credential"), eq(resourceId), eq(pageable)))
                .thenReturn(Page.empty(pageable));

        var result = activityController.getResourceActivities("credential", resourceId, user, pageable);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(activityService).getUserResourceActivities(eq(userId), eq("credential"), eq(resourceId), eq(pageable));
    }

    @Test
    void getResourceActivities_extractsUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = testUser(userId);
        var resourceId = UUID.randomUUID();
        when(activityService.getUserResourceActivities(eq(userId), any(), any(), any()))
                .thenReturn(Page.empty());

        activityController.getResourceActivities("execution", resourceId, user, Pageable.unpaged());

        verify(activityService).getUserResourceActivities(eq(userId), eq("execution"), eq(resourceId), any(Pageable.class));
    }

    @Test
    void getResourceActivities_multipleActivities_returnsAll() {
        var userId = UUID.randomUUID();
        var user = testUser(userId);
        var resourceId = UUID.randomUUID();
        var a1 = sampleActivity(userId, "FLOW_CREATE", "flow");
        var a2 = sampleActivity(userId, "FLOW_UPDATE", "flow");
        var a3 = sampleActivity(userId, "FLOW_PUBLISH", "flow");
        Page<UserActivity> page = new PageImpl<>(List.of(a1, a2, a3));
        when(activityService.getUserResourceActivities(eq(userId), eq("flow"), eq(resourceId), any(Pageable.class)))
                .thenReturn(page);

        var result = activityController.getResourceActivities("flow", resourceId, user, Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getTotalElements()).isEqualTo(3);
        assertThat(result.getBody().getContent())
                .extracting(UserActivityResponse::activityType)
                .containsExactly("FLOW_CREATE", "FLOW_UPDATE", "FLOW_PUBLISH");
    }

    @Test
    void getResourceActivities_differentResourceTypes_passesCorrectType() {
        var userId = UUID.randomUUID();
        var user = testUser(userId);
        var resourceId = UUID.randomUUID();

        when(activityService.getUserResourceActivities(eq(userId), eq("webhook"), eq(resourceId), any(Pageable.class)))
                .thenReturn(Page.empty());

        var result = activityController.getResourceActivities("webhook", resourceId, user, Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(activityService).getUserResourceActivities(eq(userId), eq("webhook"), eq(resourceId), any(Pageable.class));
    }

    @Test
    void getResourceActivities_mapsEntityToResponse() {
        var userId = UUID.randomUUID();
        var user = testUser(userId);
        var resourceId = UUID.randomUUID();
        var activityId = UUID.randomUUID();
        var now = Instant.now();
        var activity = UserActivity.builder()
                .id(activityId)
                .userId(userId)
                .activityType("EXECUTION_START")
                .resourceType("execution")
                .resourceId(resourceId)
                .resourceName(null)
                .details(Map.of("flowId", UUID.randomUUID().toString()))
                .ipAddress("172.16.0.1")
                .createdAt(now)
                .build();
        when(activityService.getUserResourceActivities(eq(userId), eq("execution"), eq(resourceId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(activity)));

        var result = activityController.getResourceActivities("execution", resourceId, user, Pageable.unpaged());

        var response = result.getBody().getContent().get(0);
        assertThat(response.id()).isEqualTo(activityId);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.activityType()).isEqualTo("EXECUTION_START");
        assertThat(response.resourceType()).isEqualTo("execution");
        assertThat(response.resourceId()).isEqualTo(resourceId);
        assertThat(response.resourceName()).isNull();
        assertThat(response.ipAddress()).isEqualTo("172.16.0.1");
        assertThat(response.createdAt()).isEqualTo(now);
    }

    @Test
    void getResourceActivities_activityWithNullDetails_mapsCorrectly() {
        var userId = UUID.randomUUID();
        var user = testUser(userId);
        var resourceId = UUID.randomUUID();
        var activity = UserActivity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .activityType("FLOW_DELETE")
                .resourceType("flow")
                .resourceId(resourceId)
                .resourceName("deleted-flow")
                .details(null)
                .ipAddress(null)
                .createdAt(Instant.now())
                .build();
        when(activityService.getUserResourceActivities(eq(userId), eq("flow"), eq(resourceId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(activity)));

        var result = activityController.getResourceActivities("flow", resourceId, user, Pageable.unpaged());

        var response = result.getBody().getContent().get(0);
        assertThat(response.details()).isNull();
        assertThat(response.ipAddress()).isNull();
        assertThat(response.resourceName()).isEqualTo("deleted-flow");
    }

    // ==========================================================
    // Edge cases and cross-cutting concerns
    // ==========================================================

    @Test
    void listAllActivities_serviceReturnsPageWithTotalCount_preservesTotalCount() {
        var activities = List.of(sampleActivity(UUID.randomUUID()), sampleActivity(UUID.randomUUID()));
        // Simulate a page that is a subset of a larger result set
        Page<UserActivity> page = new PageImpl<>(activities, PageRequest.of(0, 2), 50);
        when(activityService.getAllActivities(any(Pageable.class))).thenReturn(page);

        var result = activityController.listAllActivities(null, PageRequest.of(0, 2));

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isEqualTo(50);
        assertThat(result.getBody().getContent()).hasSize(2);
        assertThat(result.getBody().getTotalPages()).isEqualTo(25);
    }

    @Test
    void listMyActivities_serviceReturnsPageWithTotalCount_preservesTotalCount() {
        var userId = UUID.randomUUID();
        var user = testUser(userId);
        var activities = List.of(sampleActivity(userId));
        Page<UserActivity> page = new PageImpl<>(activities, PageRequest.of(0, 10), 100);
        when(activityService.getUserActivities(eq(userId), any(Pageable.class))).thenReturn(page);

        var result = activityController.listMyActivities(null, user, PageRequest.of(0, 10));

        assertThat(result.getBody().getTotalElements()).isEqualTo(100);
        assertThat(result.getBody().getTotalPages()).isEqualTo(10);
    }

    @Test
    void getResourceActivities_serviceReturnsPageWithTotalCount_preservesTotalCount() {
        var userId = UUID.randomUUID();
        var user = testUser(userId);
        var resourceId = UUID.randomUUID();
        var activities = List.of(sampleActivity(userId));
        Page<UserActivity> page = new PageImpl<>(activities, PageRequest.of(0, 20), 42);
        when(activityService.getUserResourceActivities(eq(userId), eq("flow"), eq(resourceId), any(Pageable.class)))
                .thenReturn(page);

        var result = activityController.getResourceActivities("flow", resourceId, user, PageRequest.of(0, 20));

        assertThat(result.getBody().getTotalElements()).isEqualTo(42);
        assertThat(result.getBody().getTotalPages()).isEqualTo(3);
    }

    @Test
    void listAllActivities_activityWithEmptyDetails_mapsCorrectly() {
        var activity = UserActivity.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .activityType("LOGOUT")
                .resourceType("user")
                .resourceId(UUID.randomUUID())
                .resourceName("user@example.com")
                .details(Collections.emptyMap())
                .ipAddress("127.0.0.1")
                .createdAt(Instant.now())
                .build();
        when(activityService.getAllActivities(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(activity)));

        var result = activityController.listAllActivities(null, Pageable.unpaged());

        var response = result.getBody().getContent().get(0);
        assertThat(response.details()).isEmpty();
    }
}
