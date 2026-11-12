package com.agentflow.dto;

import com.agentflow.entity.ScheduleType;
import com.agentflow.exception.WorkflowValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public record WorkflowScheduleRequest(
    @NotNull(message = "Schedule type is required")
    ScheduleType scheduleType,

    LocalTime scheduledTime,

    List<String> daysOfWeek,

    String dayOfWeek,

    Instant oneTimeExecutionAt,

    @NotBlank(message = "Time zone is required")
    String timeZone,

    JsonNode inputData,

    Boolean enabled
) {
    public Set<DayOfWeek> resolveDaysOfWeek() {
        Set<DayOfWeek> set = new TreeSet<>();
        if (daysOfWeek != null) {
            for (String day : daysOfWeek) {
                if (day != null && !day.isBlank()) {
                    set.add(parseDayOfWeek(day));
                }
            }
        }
        if (dayOfWeek != null && !dayOfWeek.isBlank()) {
            for (String part : dayOfWeek.split(",")) {
                if (!part.isBlank()) {
                    set.add(parseDayOfWeek(part));
                }
            }
        }
        return set;
    }

    private static DayOfWeek parseDayOfWeek(String s) {
        try {
            return DayOfWeek.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new WorkflowValidationException(
                "Invalid day of week: '" + s + "'. Allowed values: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY"
            );
        }
    }
}
