package com.agentflow.dto;

import com.agentflow.entity.ScheduleType;
import com.agentflow.entity.WorkflowSchedule;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;

public record WorkflowScheduleResponse(
    Long id,
    Long workflowId,
    ScheduleType scheduleType,
    LocalTime scheduledTime,
    Set<DayOfWeek> daysOfWeek,
    String dayOfWeek,
    Instant oneTimeExecutionAt,
    String timeZone,
    JsonNode inputData,
    boolean enabled,
    Instant nextRunAt,
    Instant lastRunAt,
    String lastStatus,
    Instant createdAt,
    Instant updatedAt
) {
    public static WorkflowScheduleResponse fromEntity(WorkflowSchedule entity) {
        return new WorkflowScheduleResponse(
            entity.getId(),
            entity.getWorkflow().getId(),
            entity.getScheduleType(),
            entity.getScheduledTime(),
            entity.getDaysOfWeekSet(),
            entity.getDayOfWeek(),
            entity.getOneTimeExecutionAt(),
            entity.getTimeZone(),
            entity.getInputData(),
            entity.isEnabled(),
            entity.getNextRunAt(),
            entity.getLastRunAt(),
            entity.getLastStatus(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
