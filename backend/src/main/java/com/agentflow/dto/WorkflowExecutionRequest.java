package com.agentflow.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record WorkflowExecutionRequest(
    JsonNode inputData
) {}
