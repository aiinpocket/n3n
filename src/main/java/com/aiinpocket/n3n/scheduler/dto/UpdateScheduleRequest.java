package com.aiinpocket.n3n.scheduler.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class UpdateScheduleRequest {

    @Size(max = 255, message = "Name must be at most 255 characters")
    private String name;

    @Size(max = 100, message = "Cron expression must be at most 100 characters")
    @Pattern(regexp = "^[0-9*,/\\-?LW# a-zA-Z]+$", message = "Invalid cron expression format")
    private String cronExpression;

    @Size(max = 50, message = "Timezone must be at most 50 characters")
    private String timezone;

    private Map<String, Object> input;
}
