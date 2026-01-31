package com.aiinpocket.n3n.skill.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ExecuteSkillRequest {
    private Map<String, Object> input;
}
