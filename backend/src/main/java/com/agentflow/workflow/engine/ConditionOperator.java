package com.agentflow.workflow.engine;

/**
 * Supported deterministic operators for workflow condition nodes.
 *
 * Safe operator set:
 * - Equality: EQUALS, NOT_EQUALS
 * - Numeric ordering: GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL
 * - Substring: CONTAINS, NOT_CONTAINS
 * - Emptiness: IS_EMPTY, IS_NOT_EMPTY
 */
public enum ConditionOperator {
    EQUALS,
    NOT_EQUALS,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    CONTAINS,
    NOT_CONTAINS,
    IS_EMPTY,
    IS_NOT_EMPTY;

    public static ConditionOperator fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        return switch (normalized) {
            case "EQUALS", "EQ", "==", "=" -> EQUALS;
            case "NOT_EQUALS", "NOT_EQUALS_TO", "NEQ", "!=", "<>" -> NOT_EQUALS;
            case "GREATER_THAN", "GT", ">" -> GREATER_THAN;
            case "GREATER_THAN_OR_EQUAL", "GREATER_THAN_OR_EQUALS", "GTE", ">=" -> GREATER_THAN_OR_EQUAL;
            case "LESS_THAN", "LT", "<" -> LESS_THAN;
            case "LESS_THAN_OR_EQUAL", "LESS_THAN_OR_EQUALS", "LTE", "<=" -> LESS_THAN_OR_EQUAL;
            case "CONTAINS", "CONTAIN", "INCLUDES" -> CONTAINS;
            case "NOT_CONTAINS", "DOES_NOT_CONTAIN", "NOT_CONTAIN", "EXCLUDES" -> NOT_CONTAINS;
            case "IS_EMPTY", "EMPTY" -> IS_EMPTY;
            case "IS_NOT_EMPTY", "NOT_EMPTY" -> IS_NOT_EMPTY;
            default -> {
                for (ConditionOperator op : values()) {
                    if (op.name().equalsIgnoreCase(normalized)) {
                        yield op;
                    }
                }
                yield null;
            }
        };
    }

    public boolean isUnary() {
        return this == IS_EMPTY || this == IS_NOT_EMPTY;
    }
}
