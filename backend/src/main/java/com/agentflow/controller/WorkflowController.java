package com.agentflow.controller;

import com.agentflow.dto.ApiResponse;
import com.agentflow.dto.WorkflowActivePatchRequest;
import com.agentflow.dto.WorkflowCreateRequest;
import com.agentflow.dto.WorkflowResponse;
import com.agentflow.dto.WorkflowUpdateRequest;
import com.agentflow.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<WorkflowResponse>>> getAllWorkflows(
            @RequestParam(required = false) Boolean active) {
        List<WorkflowResponse> workflows = workflowService.getAllWorkflows(active);
        return ResponseEntity.ok(ApiResponse.success(workflows));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WorkflowResponse>> getWorkflowById(@PathVariable Long id) {
        WorkflowResponse workflow = workflowService.getWorkflowById(id);
        return ResponseEntity.ok(ApiResponse.success(workflow));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WorkflowResponse>> createWorkflow(
            @Valid @RequestBody WorkflowCreateRequest request) {
        WorkflowResponse created = workflowService.createWorkflow(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Workflow created successfully", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WorkflowResponse>> updateWorkflow(
            @PathVariable Long id,
            @Valid @RequestBody WorkflowUpdateRequest request) {
        WorkflowResponse updated = workflowService.updateWorkflow(id, request);
        return ResponseEntity.ok(ApiResponse.success("Workflow updated successfully", updated));
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<ApiResponse<WorkflowResponse>> updateWorkflowActiveStatus(
            @PathVariable Long id,
            @Valid @RequestBody WorkflowActivePatchRequest request) {
        WorkflowResponse updated = workflowService.updateWorkflowActiveStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Workflow status updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkflow(@PathVariable Long id) {
        workflowService.deleteWorkflow(id);
        return ResponseEntity.noContent().build();
    }
}
