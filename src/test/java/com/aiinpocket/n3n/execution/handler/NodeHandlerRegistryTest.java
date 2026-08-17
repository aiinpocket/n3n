package com.aiinpocket.n3n.execution.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NodeHandlerRegistryTest {

    private NodeHandlerRegistry registry;
    private NodeHandler spreadsheetHandler;

    @BeforeEach
    void setUp() {
        spreadsheetHandler = mockHandler("spreadsheet");
        registry = new NodeHandlerRegistry(List.of(
                spreadsheetHandler,
                mockHandler("googleCalendar"),
                mockHandler("respondWebhook")
        ));
    }

    private NodeHandler mockHandler(String type) {
        NodeHandler handler = mock(NodeHandler.class);
        when(handler.getType()).thenReturn(type);
        when(handler.getDisplayName()).thenReturn(type);
        when(handler.getConfigSchema()).thenReturn(Map.of());
        return handler;
    }

    @Test
    @DisplayName("常見別名解析到既有 handler：範本與 AI 生成的流程不會被 unknown type 擋下")
    void alias_resolvesToCanonicalHandler() {
        assertThat(registry.hasHandler("csvParser")).isTrue();
        assertThat(registry.getHandler("csvParser")).isSameAs(spreadsheetHandler);
        assertThat(registry.findHandler("calendar")).isPresent();
        assertThat(registry.hasHandler("httpResponse")).isTrue();
    }

    @Test
    @DisplayName("別名不出現在節點清單，避免面板出現重複項")
    void alias_notListedAsRegisteredType() {
        assertThat(registry.getRegisteredTypes())
                .contains("spreadsheet")
                .doesNotContain("csvParser", "calendar", "httpResponse");
    }

    @Test
    @DisplayName("未知類型仍然回報不存在")
    void unknownType_stillUnknown() {
        assertThat(registry.hasHandler("definitelyNotANode")).isFalse();
        assertThat(registry.findHandler("definitelyNotANode")).isEmpty();
    }

    @Test
    @DisplayName("若日後有人實作與別名同名的 handler，實作優先於別名")
    void realHandler_winsOverAlias() {
        NodeHandler realCsvParser = mockHandler("csvParser");
        registry.register(realCsvParser);

        assertThat(registry.getHandler("csvParser")).isSameAs(realCsvParser);
    }
}
