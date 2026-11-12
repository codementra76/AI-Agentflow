package com.agentflow.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ScheduleType {
    ONE_TIME,
    DAILY,
    WEEKLY;

    @JsonCreator
    public static ScheduleType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (ScheduleType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown schedule type: '" + value + "'. Allowed values: ONE_TIME, DAILY, WEEKLY");
    }
}
