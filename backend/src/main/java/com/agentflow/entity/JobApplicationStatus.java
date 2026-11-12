package com.agentflow.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum JobApplicationStatus {
    SAVED,
    APPLIED,
    INTERVIEWING,
    OFFERED,
    REJECTED,
    ARCHIVED;

    @JsonCreator
    public static JobApplicationStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (JobApplicationStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown job application status: '" + value +
                "'. Allowed values: SAVED, APPLIED, INTERVIEWING, OFFERED, REJECTED, ARCHIVED");
    }
}
