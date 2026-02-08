package com.aiinpocket.n3n.skill.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class ExecuteSkillRequest {
    @Size(max = 100, message = "Input must have at most 100 fields")
    private Map<String, Object> input;
}
