package com.aiinpocket.n3n.optimizer.controller;

import com.aiinpocket.n3n.optimizer.config.FlowOptimizerConfig;
import com.aiinpocket.n3n.optimizer.dto.FlowOptimizationRequest;
import com.aiinpocket.n3n.optimizer.dto.FlowOptimizationResponse;
import com.aiinpocket.n3n.optimizer.service.FlowOptimizerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/flow-optimizer")
@RequiredArgsConstructor
public class FlowOptimizerController {

    private final FlowOptimizerService flowOptimizerService;
    private final FlowOptimizerConfig config;

    @PostMapping("/analyze")
    public ResponseEntity<FlowOptimizationResponse> analyzeFlow(
            @RequestBody FlowOptimizationRequest request) {
        FlowOptimizationResponse response = flowOptimizerService.analyzeFlow(request.getDefinition());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        boolean available = flowOptimizerService.isServiceAvailable();
        return ResponseEntity.ok(Map.of(
            "enabled", config.isEnabled(),
            "available", available,
            "url", config.getUrl()
        ));
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(Map.of(
            "enabled", config.isEnabled(),
            "model", config.getModel(),
            "temperature", config.getTemperature(),
            "maxTokens", config.getMaxTokens()
        ));
    }
}
