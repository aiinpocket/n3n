package com.aiinpocket.n3n.scheduler.dto;

import com.aiinpocket.n3n.scheduler.entity.Schedule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleResponse {

    private UUID id;
    private UUID flowId;
    private String flowName;
    private String name;
    private String cronExpression;
    private String timezone;
    private boolean isActive;
    private Map<String, Object> input;
    private Instant lastRunAt;
    private Instant nextRunAt;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public static ScheduleResponse from(Schedule schedule) {
        return from(schedule, null);
    }

    public static ScheduleResponse from(Schedule schedule, String flowName) {
        return ScheduleResponse.builder()
                .id(schedule.getId())
                .flowId(schedule.getFlowId())
                .flowName(flowName)
                .name(schedule.getName())
                .cronExpression(schedule.getCronExpression())
                .timezone(schedule.getTimezone())
                .isActive(Boolean.TRUE.equals(schedule.getIsActive()))
                .input(schedule.getInput())
                .lastRunAt(schedule.getLastRunAt())
                .nextRunAt(schedule.getNextRunAt())
                .createdBy(schedule.getCreatedBy())
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt())
                .build();
    }
}
