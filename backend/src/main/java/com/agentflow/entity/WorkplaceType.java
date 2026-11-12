package com.agentflow.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum WorkplaceType {
    REMOTE,
    HYBRID,
    ON_SITE;

    @JsonCreator
    public static WorkplaceType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (WorkplaceType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown workplace type: '" + value +
                "'. Allowed values: REMOTE, HYBRID, ON_SITE");
    }
}
