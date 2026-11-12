package com.agentflow.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum EmploymentType {
    FULL_TIME,
    CONTRACT,
    PART_TIME,
    INTERNSHIP,
    OTHER;

    @JsonCreator
    public static EmploymentType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (EmploymentType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown employment type: '" + value +
                "'. Allowed values: FULL_TIME, CONTRACT, PART_TIME, INTERNSHIP, OTHER");
    }
}
