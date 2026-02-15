package com.aiinpocket.n3n.execution.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NodeTypesControllerTest {

    @Mock
    private NodeHandlerRegistry registry;

    @InjectMocks
    private NodeTypesController controller;

    // ===== Helpers =====

    private NodeHandlerInfo sampleHandlerInfo(String type, String category, boolean isTrigger) {
        return NodeHandlerInfo.builder()
                .type(type)
                .displayName(type.substring(0, 1).toUpperCase() + type.substring(1))
                .description("Description for " + type)
                .category(category)
                .icon("icon-" + type)
                .isTrigger(isTrigger)
                .supportsAsync(false)
                .configSchema(Map.of("type", "object"))
                .interfaceDefinition(Map.of())
                .build();
    }

    private NodeHandler mockHandler(String type, String category, boolean isTrigger) {
        NodeHandler handler = mock(NodeHandler.class);
        lenient().when(handler.getType()).thenReturn(type);
        lenient().when(handler.getDisplayName()).thenReturn(type.substring(0, 1).toUpperCase() + type.substring(1));
        lenient().when(handler.getDescription()).thenReturn("Description for " + type);
        lenient().when(handler.getCategory()).thenReturn(category);
        lenient().when(handler.getIcon()).thenReturn("icon-" + type);
        lenient().when(handler.isTrigger()).thenReturn(isTrigger);
        lenient().when(handler.supportsAsync()).thenReturn(false);
        lenient().when(handler.getConfigSchema()).thenReturn(Map.of("type", "object"));
        lenient().when(handler.getInterfaceDefinition()).thenReturn(Map.of());
        return handler;
    }

    // ===== GET / =====

    @Test
    void listNodeTypes_shouldReturnAllTypes() {
        var info1 = sampleHandlerInfo("httpRequest", "action", false);
        var info2 = sampleHandlerInfo("webhookTrigger", "trigger", true);

        when(registry.listHandlerInfo()).thenReturn(List.of(info1, info2));

        var response = controller.listNodeTypes();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody().get(0).getType()).isEqualTo("httpRequest");
        assertThat(response.getBody().get(1).getType()).isEqualTo("webhookTrigger");
    }

    @Test
    void listNodeTypes_shouldReturnEmptyWhenNoHandlers() {
        when(registry.listHandlerInfo()).thenReturn(List.of());

        var response = controller.listNodeTypes();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    // ===== GET /{type} =====

    @Test
    void getNodeType_shouldReturnHandlerInfo() {
        NodeHandler handler = mockHandler("httpRequest", "action", false);
        when(registry.findHandler("httpRequest")).thenReturn(Optional.of(handler));

        var response = controller.getNodeType("httpRequest");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getType()).isEqualTo("httpRequest");
        assertThat(response.getBody().getCategory()).isEqualTo("action");
        assertThat(response.getBody().isTrigger()).isFalse();
    }

    @Test
    void getNodeType_shouldReturn404WhenNotFound() {
        when(registry.findHandler("nonExistent")).thenReturn(Optional.empty());

        var response = controller.getNodeType("nonExistent");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ===== GET /{type}/schema =====

    @Test
    void getNodeSchema_shouldReturnConfigSchema() {
        Map<String, Object> schema = Map.of("type", "object", "properties", Map.of("url", Map.of("type", "string")));
        NodeHandler handler = mockHandler("httpRequest", "action", false);
        when(handler.getConfigSchema()).thenReturn(schema);
        when(registry.findHandler("httpRequest")).thenReturn(Optional.of(handler));

        var response = controller.getNodeSchema("httpRequest");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("type");
        assertThat(response.getBody()).containsKey("properties");
    }

    @Test
    void getNodeSchema_shouldReturn404WhenNotFound() {
        when(registry.findHandler("nonExistent")).thenReturn(Optional.empty());

        var response = controller.getNodeSchema("nonExistent");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ===== GET /types =====

    @Test
    void listTypes_shouldReturnTypeNames() {
        when(registry.getRegisteredTypes()).thenReturn(List.of("httpRequest", "webhookTrigger", "ifElse", "code"));

        var response = controller.listTypes();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly("httpRequest", "webhookTrigger", "ifElse", "code");
    }

    @Test
    void listTypes_shouldReturnEmptyList() {
        when(registry.getRegisteredTypes()).thenReturn(List.of());

        var response = controller.listTypes();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    // ===== GET /category/{category} =====

    @Test
    void listByCategory_shouldReturnHandlersInCategory() {
        NodeHandler handler1 = mockHandler("httpRequest", "action", false);
        NodeHandler handler2 = mockHandler("emailSender", "action", false);

        when(registry.getHandlersByCategory("action")).thenReturn(List.of(handler1, handler2));

        var response = controller.listByCategory("action");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()).allSatisfy(info ->
                assertThat(info.getCategory()).isEqualTo("action"));
    }

    @Test
    void listByCategory_shouldReturnEmptyForUnknownCategory() {
        when(registry.getHandlersByCategory("unknown")).thenReturn(List.of());

        var response = controller.listByCategory("unknown");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    // ===== GET /triggers =====

    @Test
    void listTriggers_shouldReturnOnlyTriggerHandlers() {
        NodeHandler trigger1 = mockHandler("webhookTrigger", "trigger", true);
        NodeHandler trigger2 = mockHandler("scheduleTrigger", "trigger", true);

        when(registry.getTriggerHandlers()).thenReturn(List.of(trigger1, trigger2));

        var response = controller.listTriggers();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()).allSatisfy(info ->
                assertThat(info.isTrigger()).isTrue());
    }

    @Test
    void listTriggers_shouldReturnEmptyWhenNoTriggers() {
        when(registry.getTriggerHandlers()).thenReturn(List.of());

        var response = controller.listTriggers();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }
}
