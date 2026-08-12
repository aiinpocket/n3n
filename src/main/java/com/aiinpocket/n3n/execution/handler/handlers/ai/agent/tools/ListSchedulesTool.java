package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import com.aiinpocket.n3n.scheduler.entity.Schedule;
import com.aiinpocket.n3n.scheduler.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Agent tool that lists the current user's flow schedules.
 * Strictly scoped to schedules created by the requesting user.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ListSchedulesTool implements AgentNodeTool {

    private static final int MAX_RESULTS = 20;

    private final ScheduleRepository scheduleRepository;

    @Override
    public String getId() {
        return "list_schedules";
    }

    @Override
    public String getName() {
        return "List Schedules";
    }

    @Override
    public String getDescription() {
        return """
                Lists the current user's flow schedules (cron-based automatic runs)
                with name, cron expression, timezone, enabled state, next run time,
                and the flowId they trigger. Use this to answer questions about
                what is scheduled to run and when. Returns at most 20 newest items.

                Parameters: none
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(),
                "required", List.of()
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> doExecute(context));
    }

    private ToolResult doExecute(ToolExecutionContext context) {
        UUID userId = ToolSecurity.parseUserId(context);
        if (userId == null) {
            return ToolResult.failure("No authenticated user in execution context");
        }

        try {
            List<Schedule> schedules = scheduleRepository.findByCreatedByOrderByCreatedAtDesc(userId);

            if (schedules.isEmpty()) {
                return ToolResult.success("No schedules found for the current user");
            }

            List<Schedule> shown = schedules.size() > MAX_RESULTS
                    ? schedules.subList(0, MAX_RESULTS)
                    : schedules;

            List<Map<String, Object>> items = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(schedules.size()).append(" schedule(s)");
            if (schedules.size() > MAX_RESULTS) {
                sb.append(" (showing newest ").append(MAX_RESULTS).append(")");
            }
            sb.append(":\n");

            for (Schedule schedule : shown) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", schedule.getId().toString());
                item.put("name", schedule.getName());
                item.put("cron", schedule.getCronExpression());
                item.put("timezone", schedule.getTimezone());
                item.put("enabled", Boolean.TRUE.equals(schedule.getIsActive()));
                item.put("nextRunAt", schedule.getNextRunAt() != null
                        ? schedule.getNextRunAt().toString() : null);
                item.put("flowId", schedule.getFlowId() != null
                        ? schedule.getFlowId().toString() : null);
                items.add(item);

                sb.append("- ").append(schedule.getName())
                        .append(" (cron: ").append(schedule.getCronExpression())
                        .append(", tz: ").append(schedule.getTimezone())
                        .append(", enabled: ").append(Boolean.TRUE.equals(schedule.getIsActive()))
                        .append(", nextRunAt: ").append(schedule.getNextRunAt())
                        .append(", flowId: ").append(schedule.getFlowId())
                        .append(")\n");
            }

            return ToolResult.success(sb.toString(), Map.of("schedules", items));

        } catch (Exception e) {
            log.error("list_schedules tool failed for user {}", userId, e);
            return ToolResult.failure("Failed to list schedules");
        }
    }

    @Override
    public String getCategory() {
        return "platform";
    }
}
