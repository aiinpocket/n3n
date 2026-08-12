package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool.ToolExecutionContext;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool.ToolResult;
import com.aiinpocket.n3n.scheduler.entity.Schedule;
import com.aiinpocket.n3n.scheduler.repository.ScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListSchedulesToolTest extends BaseServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    private ListSchedulesTool tool;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tool = new ListSchedulesTool(scheduleRepository);
    }

    private ToolExecutionContext contextFor(String userId) {
        return new ToolExecutionContext(userId, null, null, Map.of());
    }

    private Schedule schedule(String name) {
        return Schedule.builder()
                .id(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .name(name)
                .cronExpression("0 8 * * *")
                .timezone("Asia/Taipei")
                .isActive(true)
                .createdBy(userId)
                .build();
    }

    @Test
    @DisplayName("Basic properties")
    void basicProperties() {
        assertThat(tool.getId()).isEqualTo("list_schedules");
        assertThat(tool.getCategory()).isEqualTo("platform");
        assertThat(tool.getParametersSchema()).containsKey("properties");
    }

    @Test
    @DisplayName("Fails closed without an authenticated user")
    void failsWithoutUser() throws Exception {
        ToolResult result = tool.execute(Map.of(), contextFor(null)).get();

        assertThat(result.success()).isFalse();
        verify(scheduleRepository, never()).findByCreatedByOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("Queries only the current user's schedules")
    void scopesToOwner() throws Exception {
        when(scheduleRepository.findByCreatedByOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(schedule("Morning report")));

        ToolResult result = tool.execute(Map.of(), contextFor(userId.toString())).get();

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("Morning report");
        assertThat(result.output()).contains("0 8 * * *");
        verify(scheduleRepository).findByCreatedByOrderByCreatedAtDesc(userId);
    }

    @Test
    @DisplayName("Caps output at 20 schedules")
    void capsAtTwenty() throws Exception {
        List<Schedule> many = IntStream.range(0, 30)
                .mapToObj(i -> schedule("job-" + i))
                .toList();
        when(scheduleRepository.findByCreatedByOrderByCreatedAtDesc(userId)).thenReturn(many);

        ToolResult result = tool.execute(Map.of(), contextFor(userId.toString())).get();

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("showing newest 20");
        assertThat(result.output()).contains("job-19");
        assertThat(result.output()).doesNotContain("job-20 ");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.data().get("schedules");
        assertThat(items).hasSize(20);
    }

    @Test
    @DisplayName("Empty schedule list returns a friendly message")
    void emptyList() throws Exception {
        when(scheduleRepository.findByCreatedByOrderByCreatedAtDesc(userId))
                .thenReturn(List.of());

        ToolResult result = tool.execute(Map.of(), contextFor(userId.toString())).get();

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("No schedules found");
    }
}
