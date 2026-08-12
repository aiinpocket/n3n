package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool.ToolExecutionContext;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool.ToolResult;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowVersion;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListFlowsToolTest extends BaseServiceTest {

    @Mock
    private FlowRepository flowRepository;

    @Mock
    private FlowVersionRepository flowVersionRepository;

    private ListFlowsTool tool;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tool = new ListFlowsTool(flowRepository, flowVersionRepository);
    }

    private ToolExecutionContext contextFor(String userId) {
        return new ToolExecutionContext(userId, null, null, Map.of());
    }

    private Flow flow(UUID id, String name) {
        return Flow.builder()
                .id(id)
                .name(name)
                .description("desc of " + name)
                .createdBy(userId)
                .build();
    }

    @Test
    @DisplayName("Basic properties")
    void basicProperties() {
        assertThat(tool.getId()).isEqualTo("list_flows");
        assertThat(tool.getCategory()).isEqualTo("platform");
        assertThat(tool.getDescription()).isNotBlank();
        assertThat(tool.getParametersSchema()).containsKey("properties");
    }

    @Test
    @DisplayName("Lists only the user's own flows (queried by createdBy)")
    @SuppressWarnings("unchecked")
    void listsOwnFlowsOnly() throws Exception {
        UUID flowId1 = UUID.randomUUID();
        UUID flowId2 = UUID.randomUUID();
        when(flowRepository.findByCreatedByAndIsDeletedFalseOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(flow(flowId1, "Flow One"), flow(flowId2, "Flow Two")));
        when(flowVersionRepository.findByFlowIdInAndStatus(anyList(), eq("published")))
                .thenReturn(List.of(FlowVersion.builder()
                        .id(UUID.randomUUID()).flowId(flowId1).version("1.0.0").status("published")
                        .build()));

        ToolResult result = tool.execute(Map.of(), contextFor(userId.toString())).get();

        assertThat(result.success()).isTrue();
        // Repository was queried strictly with this user's id
        verify(flowRepository).findByCreatedByAndIsDeletedFalseOrderByCreatedAtDesc(userId);

        List<Map<String, Object>> flows = (List<Map<String, Object>>) result.data().get("flows");
        assertThat(flows).hasSize(2);
        assertThat(flows.get(0)).containsEntry("id", flowId1.toString())
                .containsEntry("name", "Flow One")
                .containsEntry("hasPublishedVersion", true);
        assertThat(flows.get(1)).containsEntry("id", flowId2.toString())
                .containsEntry("hasPublishedVersion", false);
    }

    @Test
    @DisplayName("Filters flows by query (case-insensitive)")
    @SuppressWarnings("unchecked")
    void filtersByQuery() throws Exception {
        UUID flowId1 = UUID.randomUUID();
        UUID flowId2 = UUID.randomUUID();
        when(flowRepository.findByCreatedByAndIsDeletedFalseOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(flow(flowId1, "Daily Report"), flow(flowId2, "Sync Job")));
        when(flowVersionRepository.findByFlowIdInAndStatus(anyList(), eq("published")))
                .thenReturn(List.of());

        ToolResult result = tool.execute(Map.of("query", "report"), contextFor(userId.toString())).get();

        assertThat(result.success()).isTrue();
        List<Map<String, Object>> flows = (List<Map<String, Object>>) result.data().get("flows");
        assertThat(flows).hasSize(1);
        assertThat(flows.get(0)).containsEntry("name", "Daily Report");
    }

    @Test
    @DisplayName("Returns empty list when the user has no flows")
    @SuppressWarnings("unchecked")
    void emptyList() throws Exception {
        when(flowRepository.findByCreatedByAndIsDeletedFalseOrderByCreatedAtDesc(userId))
                .thenReturn(List.of());

        ToolResult result = tool.execute(Map.of(), contextFor(userId.toString())).get();

        assertThat(result.success()).isTrue();
        List<Map<String, Object>> flows = (List<Map<String, Object>>) result.data().get("flows");
        assertThat(flows).isEmpty();
    }

    @Test
    @DisplayName("Fails when userId is missing or invalid")
    void failsWithoutUser() throws Exception {
        ToolResult noUser = tool.execute(Map.of(), contextFor(null)).get();
        assertThat(noUser.success()).isFalse();

        ToolResult badUser = tool.execute(Map.of(), contextFor("not-a-uuid")).get();
        assertThat(badUser.success()).isFalse();
    }
}
