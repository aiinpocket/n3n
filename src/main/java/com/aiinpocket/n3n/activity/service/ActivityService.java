package com.aiinpocket.n3n.activity.service;

import com.aiinpocket.n3n.activity.entity.UserActivity;
import com.aiinpocket.n3n.activity.repository.UserActivityRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityService {

    private final UserActivityRepository activityRepository;

    // Activity types
    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";
    public static final String FLOW_CREATE = "FLOW_CREATE";
    public static final String FLOW_UPDATE = "FLOW_UPDATE";
    public static final String FLOW_DELETE = "FLOW_DELETE";
    public static final String FLOW_PUBLISH = "FLOW_PUBLISH";
    public static final String EXECUTION_START = "EXECUTION_START";
    public static final String EXECUTION_COMPLETE = "EXECUTION_COMPLETE";
    public static final String EXECUTION_FAIL = "EXECUTION_FAIL";
    public static final String EXECUTION_CANCEL = "EXECUTION_CANCEL";
    public static final String CREDENTIAL_CREATE = "CREDENTIAL_CREATE";
    public static final String CREDENTIAL_DELETE = "CREDENTIAL_DELETE";
    public static final String USER_CREATE = "USER_CREATE";
    public static final String USER_UPDATE = "USER_UPDATE";

    @Async
    public void logActivity(UUID userId, String activityType, String resourceType,
                           UUID resourceId, String resourceName, Map<String, Object> details) {
        try {
            String ipAddress = null;
            String userAgent = null;

            // Try to get request info
            try {
                ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attrs != null) {
                    HttpServletRequest request = attrs.getRequest();
                    ipAddress = getClientIpAddress(request);
                    userAgent = request.getHeader("User-Agent");
                }
            } catch (Exception e) {
                // Request context not available (async context)
            }

            UserActivity activity = UserActivity.builder()
                .userId(userId)
                .activityType(activityType)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .resourceName(resourceName)
                .details(details)
                .ipAddress(ipAddress)
                .userAgent(userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent)
                .build();

            activityRepository.save(activity);
            log.debug("Activity logged: user={}, type={}, resource={}/{}", userId, activityType, resourceType, resourceId);
        } catch (Exception e) {
            log.error("Failed to log activity: user={}, type={}", userId, activityType, e);
        }
    }

    public void logLogin(UUID userId) {
        logActivity(userId, LOGIN, "user", userId, null, null);
    }

    public void logLogout(UUID userId) {
        logActivity(userId, LOGOUT, "user", userId, null, null);
    }

    public void logFlowCreate(UUID userId, UUID flowId, String flowName) {
        logActivity(userId, FLOW_CREATE, "flow", flowId, flowName, null);
    }

    public void logFlowUpdate(UUID userId, UUID flowId, String flowName) {
        logActivity(userId, FLOW_UPDATE, "flow", flowId, flowName, null);
    }

    public void logFlowDelete(UUID userId, UUID flowId, String flowName) {
        logActivity(userId, FLOW_DELETE, "flow", flowId, flowName, null);
    }

    public void logFlowPublish(UUID userId, UUID flowId, String flowName, String version) {
        logActivity(userId, FLOW_PUBLISH, "flow", flowId, flowName, Map.of("version", version));
    }

    public void logExecutionStart(UUID userId, UUID executionId, UUID flowId) {
        logActivity(userId, EXECUTION_START, "execution", executionId, null, Map.of("flowId", flowId.toString()));
    }

    public void logExecutionComplete(UUID userId, UUID executionId, int durationMs) {
        logActivity(userId, EXECUTION_COMPLETE, "execution", executionId, null, Map.of("durationMs", durationMs));
    }

    public void logExecutionFail(UUID userId, UUID executionId, String error) {
        logActivity(userId, EXECUTION_FAIL, "execution", executionId, null, Map.of("error", error));
    }

    public Page<UserActivity> getUserActivities(UUID userId, Pageable pageable) {
        return activityRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public Page<UserActivity> getResourceActivities(String resourceType, UUID resourceId, Pageable pageable) {
        return activityRepository.findByResourceTypeAndResourceIdOrderByCreatedAtDesc(resourceType, resourceId, pageable);
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
