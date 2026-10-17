package com.agentflow.workflow.engine;

public enum NodeType {
    MANUAL_TRIGGER,
    TEXT_INPUT,
    TEXT_TRANSFORM,
    AI_TEXT_PROCESSOR,
    OUTPUT;

    public static NodeType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        for (NodeType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        return null;
    }
}
