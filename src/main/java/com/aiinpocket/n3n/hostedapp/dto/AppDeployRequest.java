package com.aiinpocket.n3n.hostedapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 部署請求：使用者填寫的參數值（名稱 → 值）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppDeployRequest {

    private Map<String, String> params;
}
