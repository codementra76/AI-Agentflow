package com.agentflow.workflow.schedule;

import com.agentflow.entity.ScheduleType;
import com.agentflow.entity.WorkflowSchedule;
import com.agentflow.exception.WorkflowValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * Calculates next execution instants for workflow schedules.
 *
 * <h3>Daylight Saving Time (DST) Transition Policies:</h3>
 * <ul>
 *   <li><b>Spring-Forward Gap:</b> When local clocks advance forward (e.g., from 02:00 to 03:00) and
 *       the requested {@code scheduledTime} falls within the non-existent gap (e.g., 02:30), standard
 *       Java {@link ZonedDateTime#of(LocalDate, LocalTime, ZoneId)} semantics adjust the time forward
 *       by the gap length (e.g., to 03:30 EDT). This guarantees the scheduled job executes at the first
 *       valid local instant on that date rather than being omitted.</li>
 *   <li><b>Fall-Back Overlap:</b> When local clocks fall backward (e.g., from 02:00 to 01:00) and the
 *       requested {@code scheduledTime} occurs twice (e.g., 01:30 EDT and 01:30 EST), standard Java
 *       {@link ZonedDateTime#of(LocalDate, LocalTime, ZoneId)} selects the earlier offset (Daylight Saving
 *       Time offset). Subsequent runs advance to the next scheduled day in standard time.</li>
 * </ul>
 */
@Component
public class ScheduleCalculator {

    private final Clock clock;

    public ScheduleCalculator() {
        this(Clock.systemUTC());
    }

    public ScheduleCalculator(@Autowired(required = false) Clock clock) {
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    /**
     * Calculates the next run instant for a schedule using the configured system clock.
     * Returns {@code null} if the schedule is disabled.
     */
    public Instant calculateNextRun(WorkflowSchedule schedule) {
        Objects.requireNonNull(schedule, "WorkflowSchedule must not be null");
        if (!schedule.isEnabled()) {
            return null;
        }
        return calculateNextRun(
            schedule.getScheduleType(),
            schedule.getScheduledTime(),
            schedule.getDaysOfWeekSet(),
            schedule.getOneTimeExecutionAt(),
            schedule.getTimeZone(),
            clock.instant()
        );
    }

    /**
     * Calculates the next run instant for a schedule using an explicit reference instant.
     * Returns {@code null} if the schedule is disabled.
     */
    public Instant calculateNextRun(WorkflowSchedule schedule, Instant referenceInstant) {
        Objects.requireNonNull(schedule, "WorkflowSchedule must not be null");
        if (!schedule.isEnabled()) {
            return null;
        }
        return calculateNextRun(
            schedule.getScheduleType(),
            schedule.getScheduledTime(),
            schedule.getDaysOfWeekSet(),
            schedule.getOneTimeExecutionAt(),
            schedule.getTimeZone(),
            referenceInstant
        );
    }

    /**
     * Core calculation logic across schedule types and time zones.
     *
     * @param scheduleType       type of schedule (ONE_TIME, DAILY, WEEKLY)
     * @param scheduledTime      local time for DAILY/WEEKLY schedules
     * @param daysOfWeek         collection of weekdays for WEEKLY schedules
     * @param oneTimeExecutionAt instant for ONE_TIME schedules
     * @param timeZoneStr        valid IANA time zone identifier
     * @param referenceInstant   instant to evaluate from (usually current time)
     * @return the calculated next run instant in UTC
     */
    public Instant calculateNextRun(
            ScheduleType scheduleType,
            LocalTime scheduledTime,
            Collection<DayOfWeek> daysOfWeek,
            Instant oneTimeExecutionAt,
            String timeZoneStr,
            Instant referenceInstant) {

        if (scheduleType == null) {
            throw new WorkflowValidationException("Schedule type is required");
        }
        ZoneId zoneId = validateAndParseTimeZone(timeZoneStr);
        Instant ref = referenceInstant != null ? referenceInstant : clock.instant();

        return switch (scheduleType) {
            case ONE_TIME -> calculateOneTime(oneTimeExecutionAt, ref);
            case DAILY -> calculateDaily(scheduledTime, zoneId, ref);
            case WEEKLY -> calculateWeekly(scheduledTime, daysOfWeek, zoneId, ref);
        };
    }

    private Instant calculateOneTime(Instant oneTimeExecutionAt, Instant ref) {
        if (oneTimeExecutionAt == null) {
            throw new WorkflowValidationException("Execution time is required for ONE_TIME schedule");
        }
        if (!oneTimeExecutionAt.isAfter(ref)) {
            throw new WorkflowValidationException(
                "One-time execution time must be in the future (scheduled: " + oneTimeExecutionAt + ", current: " + ref + ")"
            );
        }
        return oneTimeExecutionAt;
    }

    private Instant calculateDaily(LocalTime scheduledTime, ZoneId zoneId, Instant ref) {
        if (scheduledTime == null) {
            throw new WorkflowValidationException("Scheduled time is required for DAILY schedule");
        }
        ZonedDateTime refZdt = ref.atZone(zoneId);
        LocalDate today = refZdt.toLocalDate();

        ZonedDateTime candidateToday = ZonedDateTime.of(today, scheduledTime, zoneId);
        if (candidateToday.toInstant().isAfter(ref)) {
            return candidateToday.toInstant();
        }

        LocalDate tomorrow = today.plusDays(1);
        ZonedDateTime candidateTomorrow = ZonedDateTime.of(tomorrow, scheduledTime, zoneId);
        return candidateTomorrow.toInstant();
    }

    private Instant calculateWeekly(LocalTime scheduledTime, Collection<DayOfWeek> daysOfWeek, ZoneId zoneId, Instant ref) {
        if (scheduledTime == null) {
            throw new WorkflowValidationException("Scheduled time is required for WEEKLY schedule");
        }
        if (daysOfWeek == null || daysOfWeek.isEmpty()) {
            throw new WorkflowValidationException("At least one day of the week is required for WEEKLY schedule");
        }

        Set<DayOfWeek> targetDays = Set.copyOf(daysOfWeek);
        ZonedDateTime refZdt = ref.atZone(zoneId);
        LocalDate today = refZdt.toLocalDate();

        if (targetDays.contains(today.getDayOfWeek())) {
            ZonedDateTime candidateToday = ZonedDateTime.of(today, scheduledTime, zoneId);
            if (candidateToday.toInstant().isAfter(ref)) {
                return candidateToday.toInstant();
            }
        }

        for (int i = 1; i <= 7; i++) {
            LocalDate nextDate = today.plusDays(i);
            if (targetDays.contains(nextDate.getDayOfWeek())) {
                ZonedDateTime candidate = ZonedDateTime.of(nextDate, scheduledTime, zoneId);
                return candidate.toInstant();
            }
        }

        throw new WorkflowValidationException("Could not determine next run date for WEEKLY schedule");
    }

    /**
     * Validates and returns a {@link ZoneId} for the provided IANA identifier.
     */
    public ZoneId validateAndParseTimeZone(String timeZoneStr) {
        if (timeZoneStr == null || timeZoneStr.isBlank()) {
            throw new WorkflowValidationException("Time zone is required");
        }
        try {
            return ZoneId.of(timeZoneStr.trim());
        } catch (Exception ex) {
            throw new WorkflowValidationException(
                "Invalid time zone: '" + timeZoneStr + "'. Must be a valid IANA time zone identifier."
            );
        }
    }
}
