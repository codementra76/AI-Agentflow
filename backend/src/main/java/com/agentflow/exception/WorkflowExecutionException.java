package com.agentflow.exception;

public class WorkflowExecutionException extends AgentFlowException {
    public WorkflowExecutionException(String message) {
        super(message);
    }

    public WorkflowExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
