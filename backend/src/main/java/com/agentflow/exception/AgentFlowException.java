package com.agentflow.exception;

public class AgentFlowException extends RuntimeException {
    public AgentFlowException(String message) {
        super(message);
    }

    public AgentFlowException(String message, Throwable cause) {
        super(message, cause);
    }
}
