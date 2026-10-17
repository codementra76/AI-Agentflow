package com.agentflow.controller;

import com.agentflow.dto.ApiResponse;
import com.agentflow.dto.HealthStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;

@RestController
@RequestMapping("/api/v1")
public class HealthCheckController {

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<HealthStatusResponse>> checkHealth() {
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        HealthStatusResponse health = new HealthStatusResponse("UP", "AgentFlow Core Backend", uptime);
        return ResponseEntity.ok(ApiResponse.success("System healthy", health));
    }
}
