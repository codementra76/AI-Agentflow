package com.agentflow.controller;

import com.agentflow.dto.ApiResponse;
import com.agentflow.dto.ScheduleStatusRequest;
import com.agentflow.dto.WorkflowScheduleRequest;
import com.agentflow.dto.WorkflowScheduleResponse;
import com.agentflow.service.WorkflowScheduleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workflows/{workflowId}/schedule")
public class WorkflowScheduleController {

    private final WorkflowScheduleService scheduleService;

    public WorkflowScheduleController(WorkflowScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<WorkflowScheduleResponse>> getSchedule(
            @PathVariable Long workflowId) {
        WorkflowScheduleResponse schedule = scheduleService.getScheduleByWorkflowId(workflowId);
        return ResponseEntity.ok(ApiResponse.success(schedule));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<WorkflowScheduleResponse>> saveSchedule(
            @PathVariable Long workflowId,
            @Valid @RequestBody WorkflowScheduleRequest request) {
        WorkflowScheduleResponse saved = scheduleService.saveOrUpdateSchedule(workflowId, request);
        return ResponseEntity.ok(ApiResponse.success("Workflow schedule saved successfully", saved));
    }

    @PatchMapping("/status")
    public ResponseEntity<ApiResponse<WorkflowScheduleResponse>> updateStatus(
            @PathVariable Long workflowId,
            @Valid @RequestBody ScheduleStatusRequest request) {
        WorkflowScheduleResponse updated = scheduleService.updateScheduleStatus(workflowId, request);
        return ResponseEntity.ok(ApiResponse.success("Workflow schedule status updated successfully", updated));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteSchedule(@PathVariable Long workflowId) {
        scheduleService.deleteSchedule(workflowId);
        return ResponseEntity.noContent().build();
    }
}
