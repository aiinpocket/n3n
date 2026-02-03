package com.aiinpocket.n3n.optimizer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "flow-optimizer")
public class FlowOptimizerConfig {

    private boolean enabled = true;
    private String url = "http://localhost:8081";
    private int timeoutMs = 30000;
    private String model = "phi-3-mini";
    private double temperature = 0.7;
    private int maxTokens = 1024;
}
