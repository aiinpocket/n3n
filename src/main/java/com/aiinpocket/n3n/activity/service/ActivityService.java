package com.aiinpocket.n3n.activity.service;

import com.aiinpocket.n3n.activity.entity.UserActivity;
import com.aiinpocket.n3n.activity.repository.UserActivityRepository;
import com.aiinpocket.n3n.common.logging.LogContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for recording user activities and audit logs.
 * Provides structured logging for ELK integration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityService {

    private final UserActivityRepository activityRepository;

    // ===== Authentication Activity Types =====
    public static final String LOGIN = "LOGIN";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGOUT = "LOGOUT";
    public static final String TOKEN_REFRESH = "TOKEN_REFRESH";
    public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";

    // ===== User Activity Types =====
    public static final String USER_CREATE = "USER_CREATE";
    public static final String USER_UPDATE = "USER_UPDATE";
    public static final String USER_DELETE = "USER_DELETE";
    public static final String USER_LOCK = "USER_LOCK";
    public static final String USER_UNLOCK = "USER_UNLOCK";

    // ===== Flow Activity Types =====
    public static final String FLOW_CREATE = "FLOW_CREATE";
    public static final String FLOW_UPDATE = "FLOW_UPDATE";
    public static final String FLOW_DELETE = "FLOW_DELETE";
    public static final String FLOW_PUBLISH = "FLOW_PUBLISH";
    public static final String FLOW_SHARE = "FLOW_SHARE";
    public static final String FLOW_SHARE_UPDATE = "FLOW_SHARE_UPDATE";
    public static final String FLOW_SHARE_REVOKE = "FLOW_SHARE_REVOKE";
    public static final String FLOW_EXPORT = "FLOW_EXPORT";
    public static final String FLOW_IMPORT = "FLOW_IMPORT";

    // ===== Version Activity Types =====
    public static final String VERSION_CREATE = "VERSION_CREATE";
    public static final String VERSION_UPDATE = "VERSION_UPDATE";
    public static final String VERSION_PUBLISH = "VERSION_PUBLISH";
    public static final String VERSION_DEPRECATE = "VERSION_DEPRECATE";

    // ===== Execution Activity Types =====
    public static final String EXECUTION_START = "EXECUTION_START";
    public static final String EXECUTION_COMPLETE = "EXECUTION_COMPLETE";
    public static final String EXECUTION_FAIL = "EXECUTION_FAIL";
    public static final String EXECUTION_CANCEL = "EXECUTION_CANCEL";
    public static final String EXECUTION_PAUSE = "EXECUTION_PAUSE";
    public static final String EXECUTION_RESUME = "EXECUTION_RESUME";
    public static final String EXECUTION_RETRY = "EXECUTION_RETRY";

    // ===== Credential Activity Types =====
    public static final String CREDENTIAL_CREATE = "CREDENTIAL_CREATE";
    public static final String CREDENTIAL_UPDATE = "CREDENTIAL_UPDATE";
    public static final String CREDENTIAL_DELETE = "CREDENTIAL_DELETE";
    public static final String CREDENTIAL_SHARE = "CREDENTIAL_SHARE";
    public static final String CREDENTIAL_SHARE_REVOKE = "CREDENTIAL_SHARE_REVOKE";
    public static final String CREDENTIAL_ACCESS = "CREDENTIAL_ACCESS";

    // ===== Webhook Activity Types =====
    public static final String WEBHOOK_CREATE = "WEBHOOK_CREATE";
    public static final String WEBHOOK_UPDATE = "WEBHOOK_UPDATE";
    public static final String WEBHOOK_DELETE = "WEBHOOK_DELETE";
    public static final String WEBHOOK_TRIGGER = "WEBHOOK_TRIGGER";
    public static final String WEBHOOK_TRIGGER_FAILED = "WEBHOOK_TRIGGER_FAILED";

    // ===== API Activity Types =====
    public static final String API_ACCESS = "API_ACCESS";
    public static final String API_ERROR = "API_ERROR";

    // ===== Admin Activity Types =====
    public static final String ADMIN_ACTION = "ADMIN_ACTION";
    public static final String CONFIG_CHANGE = "CONFIG_CHANGE";

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

    // ===== Authentication Logging =====

    public void logLogin(UUID userId, String email) {
        logActivity(userId, LOGIN, "user", userId, email, null);
        logStructured("AUTH_LOGIN", userId, Map.of("email", email));
    }

    public void logLoginFailed(String email, String reason) {
        logActivity(null, LOGIN_FAILED, "user", null, email, Map.of("reason", reason));
        logStructured("AUTH_LOGIN_FAILED", null, Map.of("email", email, "reason", reason));
    }

    public void logLogout(UUID userId, String email) {
        logActivity(userId, LOGOUT, "user", userId, email, null);
        logStructured("AUTH_LOGOUT", userId, Map.of("email", email));
    }

    public void logTokenRefresh(UUID userId) {
        logActivity(userId, TOKEN_REFRESH, "user", userId, null, null);
    }

    public void logPasswordChange(UUID userId, String email) {
        logActivity(userId, PASSWORD_CHANGE, "user", userId, email, null);
        logStructured("AUTH_PASSWORD_CHANGE", userId, Map.of("email", email));
    }

    public void logPasswordReset(UUID userId, String email) {
        logActivity(userId, PASSWORD_RESET, "user", userId, email, null);
        logStructured("AUTH_PASSWORD_RESET", userId, Map.of("email", email));
    }

    public void logUserCreate(UUID userId, String email, String roles) {
        Map<String, Object> details = Map.of("roles", roles);
        logActivity(userId, USER_CREATE, "user", userId, email, details);
        logStructured("USER_CREATE", userId, Map.of("email", email, "roles", roles));
    }

    // ===== Flow Logging =====

    public void logFlowCreate(UUID userId, UUID flowId, String flowName) {
        logActivity(userId, FLOW_CREATE, "flow", flowId, flowName, null);
        logStructured("FLOW_CREATE", userId, Map.of("flowId", flowId, "flowName", flowName));
    }

    public void logFlowUpdate(UUID userId, UUID flowId, String flowName, Map<String, Object> changes) {
        logActivity(userId, FLOW_UPDATE, "flow", flowId, flowName, changes);
        logStructured("FLOW_UPDATE", userId, Map.of("flowId", flowId, "flowName", flowName));
    }

    public void logFlowDelete(UUID userId, UUID flowId, String flowName) {
        logActivity(userId, FLOW_DELETE, "flow", flowId, flowName, null);
        logStructured("FLOW_DELETE", userId, Map.of("flowId", flowId, "flowName", flowName));
    }

    public void logFlowPublish(UUID userId, UUID flowId, String flowName, String version) {
        logActivity(userId, FLOW_PUBLISH, "flow", flowId, flowName, Map.of("version", version));
        logStructured("FLOW_PUBLISH", userId, Map.of("flowId", flowId, "flowName", flowName, "version", version));
    }

    public void logFlowShare(UUID userId, UUID flowId, String flowName, String sharedWithEmail, String permission) {
        Map<String, Object> details = Map.of("sharedWith", sharedWithEmail, "permission", permission);
        logActivity(userId, FLOW_SHARE, "flow", flowId, flowName, details);
        logStructured("FLOW_SHARE", userId, Map.of("flowId", flowId, "sharedWith", sharedWithEmail, "permission", permission));
    }

    public void logFlowShareUpdate(UUID userId, UUID flowId, String flowName, String sharedWithEmail, String oldPermission, String newPermission) {
        Map<String, Object> details = new HashMap<>();
        details.put("sharedWith", sharedWithEmail);
        details.put("oldPermission", oldPermission);
        details.put("newPermission", newPermission);
        logActivity(userId, FLOW_SHARE_UPDATE, "flow", flowId, flowName, details);
        logStructured("FLOW_SHARE_UPDATE", userId, Map.of("flowId", flowId, "sharedWith", sharedWithEmail, "newPermission", newPermission));
    }

    public void logFlowShareRevoke(UUID userId, UUID flowId, String flowName, String revokedEmail) {
        Map<String, Object> details = Map.of("revokedFrom", revokedEmail);
        logActivity(userId, FLOW_SHARE_REVOKE, "flow", flowId, flowName, details);
        logStructured("FLOW_SHARE_REVOKE", userId, Map.of("flowId", flowId, "revokedFrom", revokedEmail));
    }

    public void logFlowExport(UUID userId, UUID flowId, String flowName, String format) {
        Map<String, Object> details = Map.of("format", format);
        logActivity(userId, FLOW_EXPORT, "flow", flowId, flowName, details);
        logStructured("FLOW_EXPORT", userId, Map.of("flowId", flowId, "format", format));
    }

    public void logFlowImport(UUID userId, UUID flowId, String flowName) {
        logActivity(userId, FLOW_IMPORT, "flow", flowId, flowName, null);
        logStructured("FLOW_IMPORT", userId, Map.of("flowId", flowId, "flowName", flowName));
    }

    // ===== Version Logging =====

    public void logVersionCreate(UUID userId, UUID flowId, String flowName, String version) {
        Map<String, Object> details = Map.of("version", version);
        logActivity(userId, VERSION_CREATE, "flow_version", flowId, flowName, details);
        logStructured("VERSION_CREATE", userId, Map.of("flowId", flowId, "version", version));
    }

    public void logVersionPublish(UUID userId, UUID flowId, String flowName, String version, String previousVersion) {
        Map<String, Object> details = new HashMap<>();
        details.put("version", version);
        if (previousVersion != null) {
            details.put("previousVersion", previousVersion);
        }
        logActivity(userId, VERSION_PUBLISH, "flow_version", flowId, flowName, details);
        logStructured("VERSION_PUBLISH", userId, Map.of("flowId", flowId, "version", version));
    }

    // ===== Execution Logging =====

    public void logExecutionStart(UUID userId, UUID executionId, UUID flowId, String flowName, String triggerType) {
        Map<String, Object> details = new HashMap<>();
        details.put("flowId", flowId.toString());
        details.put("flowName", flowName);
        details.put("triggerType", triggerType);
        logActivity(userId, EXECUTION_START, "execution", executionId, null, details);
        logStructured("EXECUTION_START", userId, Map.of("executionId", executionId, "flowId", flowId, "triggerType", triggerType));
    }

    public void logExecutionComplete(UUID userId, UUID executionId, UUID flowId, int durationMs) {
        Map<String, Object> details = Map.of("flowId", flowId.toString(), "durationMs", durationMs);
        logActivity(userId, EXECUTION_COMPLETE, "execution", executionId, null, details);
        logStructured("EXECUTION_COMPLETE", userId, Map.of("executionId", executionId, "durationMs", durationMs));
    }

    public void logExecutionFail(UUID userId, UUID executionId, UUID flowId, String error) {
        Map<String, Object> details = Map.of("flowId", flowId.toString(), "error", truncate(error, 500));
        logActivity(userId, EXECUTION_FAIL, "execution", executionId, null, details);
        logStructured("EXECUTION_FAIL", userId, Map.of("executionId", executionId, "error", truncate(error, 200)));
    }

    public void logExecutionCancel(UUID userId, UUID executionId, String reason) {
        logActivity(userId, EXECUTION_CANCEL, "execution", executionId, null, Map.of("reason", reason));
        logStructured("EXECUTION_CANCEL", userId, Map.of("executionId", executionId, "reason", reason));
    }

    public void logExecutionPause(UUID userId, UUID executionId, String nodeId, String reason) {
        Map<String, Object> details = Map.of("nodeId", nodeId, "reason", reason);
        logActivity(userId, EXECUTION_PAUSE, "execution", executionId, null, details);
        logStructured("EXECUTION_PAUSE", userId, Map.of("executionId", executionId, "nodeId", nodeId));
    }

    public void logExecutionResume(UUID userId, UUID executionId, String nodeId) {
        logActivity(userId, EXECUTION_RESUME, "execution", executionId, null, Map.of("nodeId", nodeId));
        logStructured("EXECUTION_RESUME", userId, Map.of("executionId", executionId, "nodeId", nodeId));
    }

    public void logExecutionRetry(UUID userId, UUID newExecutionId, UUID originalExecutionId, int retryCount) {
        Map<String, Object> details = Map.of("originalExecutionId", originalExecutionId.toString(), "retryCount", retryCount);
        logActivity(userId, EXECUTION_RETRY, "execution", newExecutionId, null, details);
        logStructured("EXECUTION_RETRY", userId, Map.of("newExecutionId", newExecutionId, "originalExecutionId", originalExecutionId, "retryCount", retryCount));
    }

    // ===== Credential Logging =====

    public void logCredentialCreate(UUID userId, UUID credentialId, String credentialName, String credentialType) {
        Map<String, Object> details = Map.of("type", credentialType);
        logActivity(userId, CREDENTIAL_CREATE, "credential", credentialId, credentialName, details);
        logStructured("CREDENTIAL_CREATE", userId, Map.of("credentialId", credentialId, "type", credentialType));
    }

    public void logCredentialUpdate(UUID userId, UUID credentialId, String credentialName) {
        logActivity(userId, CREDENTIAL_UPDATE, "credential", credentialId, credentialName, null);
        logStructured("CREDENTIAL_UPDATE", userId, Map.of("credentialId", credentialId));
    }

    public void logCredentialDelete(UUID userId, UUID credentialId, String credentialName) {
        logActivity(userId, CREDENTIAL_DELETE, "credential", credentialId, credentialName, null);
        logStructured("CREDENTIAL_DELETE", userId, Map.of("credentialId", credentialId));
    }

    public void logCredentialAccess(UUID userId, UUID credentialId, String credentialName, String accessedBy) {
        Map<String, Object> details = Map.of("accessedBy", accessedBy);
        logActivity(userId, CREDENTIAL_ACCESS, "credential", credentialId, credentialName, details);
    }

    // ===== Webhook Logging =====

    public void logWebhookCreate(UUID userId, UUID webhookId, String path, UUID flowId) {
        Map<String, Object> details = Map.of("path", path, "flowId", flowId.toString());
        logActivity(userId, WEBHOOK_CREATE, "webhook", webhookId, path, details);
        logStructured("WEBHOOK_CREATE", userId, Map.of("webhookId", webhookId, "path", path));
    }

    public void logWebhookTrigger(String webhookPath, UUID executionId, String sourceIp, String userAgent, int payloadSize) {
        Map<String, Object> details = new HashMap<>();
        details.put("path", webhookPath);
        details.put("executionId", executionId != null ? executionId.toString() : null);
        details.put("sourceIp", sourceIp);
        details.put("payloadSize", payloadSize);
        logActivity(null, WEBHOOK_TRIGGER, "webhook", executionId, webhookPath, details);
        logStructured("WEBHOOK_TRIGGER", null, Map.of(
            "path", webhookPath,
            "sourceIp", sourceIp,
            "payloadSize", payloadSize,
            "executionId", executionId != null ? executionId.toString() : "null"
        ));
    }

    public void logWebhookTriggerFailed(String webhookPath, String sourceIp, String reason) {
        Map<String, Object> details = Map.of("path", webhookPath, "sourceIp", sourceIp, "reason", reason);
        logActivity(null, WEBHOOK_TRIGGER_FAILED, "webhook", null, webhookPath, details);
        logStructured("WEBHOOK_TRIGGER_FAILED", null, Map.of("path", webhookPath, "sourceIp", sourceIp, "reason", reason));
    }

    // ===== Admin Logging =====

    public void logAdminAction(UUID adminUserId, String action, String targetType, UUID targetId, Map<String, Object> details) {
        Map<String, Object> fullDetails = new HashMap<>();
        fullDetails.put("action", action);
        if (details != null) {
            fullDetails.putAll(details);
        }
        logActivity(adminUserId, ADMIN_ACTION, targetType, targetId, null, fullDetails);
        logStructured("ADMIN_ACTION", adminUserId, Map.of("action", action, "targetType", targetType));
    }

    public Page<UserActivity> getUserActivities(UUID userId, Pageable pageable) {
        return activityRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public Page<UserActivity> getResourceActivities(String resourceType, UUID resourceId, Pageable pageable) {
        return activityRepository.findByResourceTypeAndResourceIdOrderByCreatedAtDesc(resourceType, resourceId, pageable);
    }

    public Page<UserActivity> getUserResourceActivities(UUID userId, String resourceType, UUID resourceId, Pageable pageable) {
        return activityRepository.findByUserIdAndResourceTypeAndResourceIdOrderByCreatedAtDesc(userId, resourceType, resourceId, pageable);
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

    /**
     * Get client IP from current request context.
     * Use this when you need to pass IP to logging methods.
     */
    public String getCurrentClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                return getClientIpAddress(attrs.getRequest());
            }
        } catch (Exception e) {
            // Request context not available
        }
        return null;
    }

    /**
     * Get User-Agent from current request context.
     */
    public String getCurrentUserAgent() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                String ua = attrs.getRequest().getHeader("User-Agent");
                return ua != null && ua.length() > 500 ? ua.substring(0, 500) : ua;
            }
        } catch (Exception e) {
            // Request context not available
        }
        return null;
    }

    /**
     * Log a structured message for ELK integration.
     * Format: ACTIVITY_TYPE userId=uuid key1=value1 key2=value2
     */
    private void logStructured(String activityType, UUID userId, Map<String, Object> fields) {
        StringBuilder sb = new StringBuilder(activityType);
        if (userId != null) {
            sb.append(" userId=").append(userId);
        }
        String ip = getCurrentClientIp();
        if (ip != null) {
            sb.append(" ip=").append(ip);
        }
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (entry.getValue() != null) {
                sb.append(" ").append(entry.getKey()).append("=").append(entry.getValue());
            }
        }
        log.info(sb.toString());
    }

    /**
     * Truncate string to max length.
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }

    /**
     * Get all activities (for admin).
     */
    public Page<UserActivity> getAllActivities(Pageable pageable) {
        return activityRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * Get activities by type.
     */
    public Page<UserActivity> getActivitiesByType(String activityType, Pageable pageable) {
        return activityRepository.findByActivityTypeOrderByCreatedAtDesc(activityType, pageable);
    }

    /**
     * Get activities by user and type.
     */
    public Page<UserActivity> getUserActivitiesByType(UUID userId, String activityType, Pageable pageable) {
        return activityRepository.findByUserIdAndActivityTypeOrderByCreatedAtDesc(userId, activityType, pageable);
    }
}
