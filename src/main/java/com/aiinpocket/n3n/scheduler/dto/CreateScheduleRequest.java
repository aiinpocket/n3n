package com.aiinpocket.n3n.scheduler.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class CreateScheduleRequest {

    @NotNull(message = "Flow ID is required")
    private UUID flowId;

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must be at most 255 characters")
    private String name;

    @NotBlank(message = "Cron expression is required")
    @Size(max = 100, message = "Cron expression must be at most 100 characters")
    @Pattern(regexp = "^[0-9*,/\\-?LW# a-zA-Z]+$", message = "Invalid cron expression format")
    private String cronExpression;

    @Size(max = 50, message = "Timezone must be at most 50 characters")
    private String timezone = "UTC";

    private Map<String, Object> input;
}
