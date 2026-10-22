package com.agentflow.controller;

import com.agentflow.dto.ApiResponse;
import com.agentflow.dto.WorkflowExecutionRequest;
import com.agentflow.dto.WorkflowExecutionResponse;
import com.agentflow.service.WorkflowExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class WorkflowExecutionController {

    private final WorkflowExecutionService executionService;

    public WorkflowExecutionController(WorkflowExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/workflows/{id}/execute")
    public ResponseEntity<ApiResponse<WorkflowExecutionResponse>> executeWorkflow(
            @PathVariable Long id,
            @RequestBody(required = false) WorkflowExecutionRequest request) {
        WorkflowExecutionResponse response = executionService.executeWorkflow(id, request);
        return ResponseEntity.ok(ApiResponse.success("Workflow execution processed", response));
    }

    @GetMapping("/executions")
    public ResponseEntity<ApiResponse<List<WorkflowExecutionResponse>>> getAllExecutions(
            @RequestParam(required = false) Long workflowId,
            @RequestParam(required = false) String status) {
        List<WorkflowExecutionResponse> executions = executionService.getAllExecutions(workflowId, status);
        return ResponseEntity.ok(ApiResponse.success(executions));
    }

    @GetMapping("/executions/{id}")
    public ResponseEntity<ApiResponse<WorkflowExecutionResponse>> getExecutionById(@PathVariable Long id) {
        WorkflowExecutionResponse execution = executionService.getExecutionById(id);
        return ResponseEntity.ok(ApiResponse.success(execution));
    }

    @GetMapping("/workflows/{id}/executions")
    public ResponseEntity<ApiResponse<List<WorkflowExecutionResponse>>> getExecutionsByWorkflowId(@PathVariable Long id) {
        List<WorkflowExecutionResponse> executions = executionService.getExecutionsByWorkflowId(id);
        return ResponseEntity.ok(ApiResponse.success(executions));
    }

    @PostMapping("/executions/{id}/retry")
    public ResponseEntity<ApiResponse<WorkflowExecutionResponse>> retryExecution(@PathVariable Long id) {
        WorkflowExecutionResponse response = executionService.retryExecution(id);
        return ResponseEntity.ok(ApiResponse.success("Workflow execution retried successfully", response));
    }
}
