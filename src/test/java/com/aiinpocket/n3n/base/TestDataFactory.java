package com.aiinpocket.n3n.base;

import com.aiinpocket.n3n.auth.dto.request.LoginRequest;
import com.aiinpocket.n3n.auth.dto.request.RegisterRequest;
import com.aiinpocket.n3n.auth.entity.User;
import com.aiinpocket.n3n.auth.entity.UserRole;
import com.aiinpocket.n3n.flow.dto.CreateFlowRequest;
import com.aiinpocket.n3n.flow.dto.SaveVersionRequest;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowVersion;

import java.time.Instant;
import java.util.*;

/**
 * Factory for creating test data objects.
 * Provides consistent test data across all test classes.
 */
public class TestDataFactory {

    // ========== Auth ==========

    public static User createUser() {
        return createUser("test@example.com", "Test User");
    }

    public static User createUser(String email, String name) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash("$2a$10$hashedPassword")
                .name(name)
                .status("active")
                .emailVerified(true)
                .loginAttempts(0)
                .createdAt(Instant.now())
                .build();
    }

    public static User createPendingUser(String email) {
        User user = createUser(email, "Pending User");
        user.setStatus("pending");
        user.setEmailVerified(false);
        return user;
    }

    public static User createLockedUser(String email) {
        User user = createUser(email, "Locked User");
        user.setLoginAttempts(5);
        user.setLockedUntil(Instant.now().plusSeconds(3600));
        return user;
    }

    public static UserRole createUserRole(UUID userId, String role) {
        return UserRole.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .role(role)
                .createdAt(Instant.now())
                .build();
    }

    public static LoginRequest createLoginRequest() {
        return createLoginRequest("test@example.com", "password123");
    }

    public static LoginRequest createLoginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    public static RegisterRequest createRegisterRequest() {
        return createRegisterRequest("new@example.com", "password123", "New User");
    }

    public static RegisterRequest createRegisterRequest(String email, String password, String name) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword(password);
        request.setName(name);
        return request;
    }

    // ========== Flow ==========

    public static Flow createFlow() {
        return createFlow("Test Flow", UUID.randomUUID());
    }

    public static Flow createFlow(String name, UUID createdBy) {
        return Flow.builder()
                .id(UUID.randomUUID())
                .name(name)
                .description("Test flow description")
                .createdBy(createdBy)
                .visibility("private")
                .isDeleted(false)
                .createdAt(Instant.now())
                .build();
    }

    public static CreateFlowRequest createFlowRequest() {
        return createFlowRequest("New Flow");
    }

    public static CreateFlowRequest createFlowRequest(String name) {
        CreateFlowRequest request = new CreateFlowRequest();
        request.setName(name);
        request.setDescription("Flow description");
        return request;
    }

    public static FlowVersion createFlowVersion(UUID flowId, String version) {
        return FlowVersion.builder()
                .id(UUID.randomUUID())
                .flowId(flowId)
                .version(version)
                .definition(createSimpleFlowDefinition())
                .settings(Map.of())
                .status("draft")
                .createdBy(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();
    }

    public static FlowVersion createPublishedVersion(UUID flowId, String version) {
        FlowVersion flowVersion = createFlowVersion(flowId, version);
        flowVersion.setStatus("published");
        return flowVersion;
    }

    public static SaveVersionRequest createSaveVersionRequest(String version) {
        SaveVersionRequest request = new SaveVersionRequest();
        request.setVersion(version);
        request.setDefinition(createSimpleFlowDefinition());
        request.setSettings(Map.of());
        return request;
    }

    // ========== Flow Definition ==========

    public static Map<String, Object> createSimpleFlowDefinition() {
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(createNode("node1", "trigger", 0, 0));
        nodes.add(createNode("node2", "action", 200, 0));

        List<Map<String, Object>> edges = new ArrayList<>();
        edges.add(createEdge("edge1", "node1", "node2"));

        Map<String, Object> definition = new HashMap<>();
        definition.put("nodes", nodes);
        definition.put("edges", edges);
        return definition;
    }

    public static Map<String, Object> createFlowDefinitionWithCycle() {
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(createNode("node1", "trigger", 0, 0));
        nodes.add(createNode("node2", "action", 200, 0));
        nodes.add(createNode("node3", "action", 400, 0));

        List<Map<String, Object>> edges = new ArrayList<>();
        edges.add(createEdge("edge1", "node1", "node2"));
        edges.add(createEdge("edge2", "node2", "node3"));
        edges.add(createEdge("edge3", "node3", "node1")); // Creates cycle

        Map<String, Object> definition = new HashMap<>();
        definition.put("nodes", nodes);
        definition.put("edges", edges);
        return definition;
    }

    public static Map<String, Object> createComplexFlowDefinition() {
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(createNode("trigger", "trigger", 0, 0));
        nodes.add(createNode("condition", "condition", 200, 0));
        nodes.add(createNode("branch1", "action", 400, -100));
        nodes.add(createNode("branch2", "action", 400, 100));
        nodes.add(createNode("output", "output", 600, 0));

        List<Map<String, Object>> edges = new ArrayList<>();
        edges.add(createEdge("e1", "trigger", "condition"));
        edges.add(createEdge("e2", "condition", "branch1"));
        edges.add(createEdge("e3", "condition", "branch2"));
        edges.add(createEdge("e4", "branch1", "output"));
        edges.add(createEdge("e5", "branch2", "output"));

        Map<String, Object> definition = new HashMap<>();
        definition.put("nodes", nodes);
        definition.put("edges", edges);
        return definition;
    }

    private static Map<String, Object> createNode(String id, String type, int x, int y) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("type", type);
        node.put("position", Map.of("x", x, "y", y));
        node.put("data", Map.of("label", id));
        return node;
    }

    private static Map<String, Object> createEdge(String id, String source, String target) {
        Map<String, Object> edge = new HashMap<>();
        edge.put("id", id);
        edge.put("source", source);
        edge.put("target", target);
        return edge;
    }

    // ========== Utilities ==========

    public static String randomEmail() {
        return "test-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    public static UUID randomUUID() {
        return UUID.randomUUID();
    }
}
