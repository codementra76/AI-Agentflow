package com.agentflow.exception;

public class AiServiceUnavailableException extends AgentFlowException {

    public AiServiceUnavailableException(String message) {
        super(message);
    }

    public AiServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
