package com.agentflow.workflow.engine;

public enum TransformOperation {
    UPPERCASE,
    LOWERCASE,
    TRIM,
    REPLACE;

    public static TransformOperation fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        for (TransformOperation op : values()) {
            if (op.name().equalsIgnoreCase(value.trim())) {
                return op;
            }
        }
        return null;
    }
}
