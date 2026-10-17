package com.agentflow.controller;

import com.agentflow.dto.ApiResponse;
import com.agentflow.dto.SystemInfoResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    @GetMapping("/info")
    public ResponseEntity<ApiResponse<SystemInfoResponse>> getSystemInfo() {
        SystemInfoResponse info = new SystemInfoResponse(
            "AgentFlow AI Workflow Automation Platform",
            "0.0.1-SNAPSHOT",
            "Java " + System.getProperty("java.version"),
            "ONLINE",
            "Enterprise AI agent workflow automation engine running on Spring Boot and Java 25"
        );
        return ResponseEntity.ok(ApiResponse.success("AgentFlow backend online", info));
    }
}
