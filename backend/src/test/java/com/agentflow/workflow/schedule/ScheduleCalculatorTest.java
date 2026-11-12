package com.agentflow.workflow.schedule;

import com.agentflow.entity.ScheduleType;
import com.agentflow.entity.Workflow;
import com.agentflow.entity.WorkflowSchedule;
import com.agentflow.exception.WorkflowValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleCalculatorTest {

    private ScheduleCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ScheduleCalculator(Clock.systemUTC());
    }

    @Test
    @DisplayName("ONE_TIME: Future instant returns exact scheduled time")
    void shouldCalculateFutureOneTimeSchedule() {
        Instant ref = Instant.parse("2026-10-23T10:00:00Z");
        Instant target = Instant.parse("2026-10-24T15:30:00Z");

        Instant nextRun = calculator.calculateNextRun(
            ScheduleType.ONE_TIME, null, null, target, "UTC", ref
        );

        assertThat(nextRun).isEqualTo(target);
    }

    @Test
    @DisplayName("ONE_TIME: Past instant throws WorkflowValidationException")
    void shouldRejectPastOneTimeSchedule() {
        Instant ref = Instant.parse("2026-10-23T10:00:00Z");
        Instant pastTarget = Instant.parse("2026-10-23T09:59:59Z");

        assertThatThrownBy(() -> calculator.calculateNextRun(
            ScheduleType.ONE_TIME, null, null, pastTarget, "UTC", ref
        ))
        .isInstanceOf(WorkflowValidationException.class)
        .hasMessageContaining("One-time execution time must be in the future");
    }

    @Test
    @DisplayName("ONE_TIME: Missing execution time throws WorkflowValidationException")
    void shouldRejectMissingOneTimeExecutionAt() {
        Instant ref = Instant.parse("2026-10-23T10:00:00Z");

        assertThatThrownBy(() -> calculator.calculateNextRun(
            ScheduleType.ONE_TIME, null, null, null, "UTC", ref
        ))
        .isInstanceOf(WorkflowValidationException.class)
        .hasMessageContaining("Execution time is required for ONE_TIME schedule");
    }

    @Test
    @DisplayName("DAILY: Scheduled time later today runs today")
    void shouldCalculateDailyScheduleLaterToday() {
        // 10:00 UTC = 06:00 in America/New_York (EDT, UTC-4)
        Instant ref = Instant.parse("2026-10-23T10:00:00Z");
        LocalTime scheduledTime = LocalTime.of(9, 30); // 09:30 EDT is 13:30 UTC

        Instant nextRun = calculator.calculateNextRun(
            ScheduleType.DAILY, scheduledTime, null, null, "America/New_York", ref
        );

        assertThat(nextRun).isEqualTo(Instant.parse("2026-10-23T13:30:00Z"));
    }

    @Test
    @DisplayName("DAILY: Scheduled time earlier today rolls over to tomorrow")
    void shouldCalculateDailyScheduleTomorrowWhenPassed() {
        // 14:00 UTC = 10:00 in America/New_York (EDT, UTC-4)
        Instant ref = Instant.parse("2026-10-23T14:00:00Z");
        LocalTime scheduledTime = LocalTime.of(9, 30); // 09:30 EDT has passed

        Instant nextRun = calculator.calculateNextRun(
            ScheduleType.DAILY, scheduledTime, null, null, "America/New_York", ref
        );

        // Tomorrow is 2026-10-24 at 09:30 EDT = 13:30 UTC
        assertThat(nextRun).isEqualTo(Instant.parse("2026-10-24T13:30:00Z"));
    }

    @Test
    @DisplayName("DAILY: Missing scheduled time throws WorkflowValidationException")
    void shouldRejectMissingDailyScheduledTime() {
        Instant ref = Instant.parse("2026-10-23T10:00:00Z");

        assertThatThrownBy(() -> calculator.calculateNextRun(
            ScheduleType.DAILY, null, null, null, "UTC", ref
        ))
        .isInstanceOf(WorkflowValidationException.class)
        .hasMessageContaining("Scheduled time is required for DAILY schedule");
    }

    @Test
    @DisplayName("WEEKLY: Single weekday later today runs today")
    void shouldCalculateWeeklySingleDayLaterToday() {
        // 2026-10-23 is a Friday
        // 08:00 UTC = 08:00 in Europe/London (BST, UTC+1 is 09:00)
        Instant ref = Instant.parse("2026-10-23T08:00:00Z");
        LocalTime scheduledTime = LocalTime.of(15, 0);

        Instant nextRun = calculator.calculateNextRun(
            ScheduleType.WEEKLY, scheduledTime, List.of(DayOfWeek.FRIDAY), null, "UTC", ref
        );

        assertThat(nextRun).isEqualTo(Instant.parse("2026-10-23T15:00:00Z"));
    }

    @Test
    @DisplayName("WEEKLY: Single weekday earlier today rolls over to next week")
    void shouldCalculateWeeklySingleDayNextWeekWhenPassed() {
        // 2026-10-23 is a Friday
        Instant ref = Instant.parse("2026-10-23T16:00:00Z");
        LocalTime scheduledTime = LocalTime.of(15, 0);

        Instant nextRun = calculator.calculateNextRun(
            ScheduleType.WEEKLY, scheduledTime, List.of(DayOfWeek.FRIDAY), null, "UTC", ref
        );

        // Next Friday is 2026-10-30 at 15:00 UTC
        assertThat(nextRun).isEqualTo(Instant.parse("2026-10-30T15:00:00Z"));
    }

    @Test
    @DisplayName("WEEKLY: Multiple days [MON, WED, FRI] advances sequentially")
    void shouldCalculateWeeklyMultipleDaysCorrectly() {
        // 2026-10-19 is a Monday
        LocalTime scheduledTime = LocalTime.of(12, 0);
        Set<DayOfWeek> days = Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);

        // 1. Monday morning -> runs Monday 12:00
        Instant mondayMorning = Instant.parse("2026-10-19T08:00:00Z");
        Instant run1 = calculator.calculateNextRun(ScheduleType.WEEKLY, scheduledTime, days, null, "UTC", mondayMorning);
        assertThat(run1).isEqualTo(Instant.parse("2026-10-19T12:00:00Z"));

        // 2. Monday afternoon -> runs Wednesday 12:00
        Instant mondayAfternoon = Instant.parse("2026-10-19T13:00:00Z");
        Instant run2 = calculator.calculateNextRun(ScheduleType.WEEKLY, scheduledTime, days, null, "UTC", mondayAfternoon);
        assertThat(run2).isEqualTo(Instant.parse("2026-10-21T12:00:00Z"));

        // 3. Wednesday afternoon -> runs Friday 12:00
        Instant wednesdayAfternoon = Instant.parse("2026-10-21T13:00:00Z");
        Instant run3 = calculator.calculateNextRun(ScheduleType.WEEKLY, scheduledTime, days, null, "UTC", wednesdayAfternoon);
        assertThat(run3).isEqualTo(Instant.parse("2026-10-23T12:00:00Z"));

        // 4. Friday afternoon -> runs next Monday 12:00
        Instant fridayAfternoon = Instant.parse("2026-10-23T13:00:00Z");
        Instant run4 = calculator.calculateNextRun(ScheduleType.WEEKLY, scheduledTime, days, null, "UTC", fridayAfternoon);
        assertThat(run4).isEqualTo(Instant.parse("2026-10-26T12:00:00Z"));
    }

    @Test
    @DisplayName("WEEKLY: Empty days collection throws WorkflowValidationException")
    void shouldRejectEmptyWeeklyDays() {
        Instant ref = Instant.parse("2026-10-23T10:00:00Z");

        assertThatThrownBy(() -> calculator.calculateNextRun(
            ScheduleType.WEEKLY, LocalTime.of(9, 0), List.of(), null, "UTC", ref
        ))
        .isInstanceOf(WorkflowValidationException.class)
        .hasMessageContaining("At least one day of the week is required for WEEKLY schedule");
    }

    @Test
    @DisplayName("DST Spring-Forward Gap: Shifts 02:30 AM gap time forward to 03:30 AM EDT")
    void shouldHandleDaylightSavingSpringForwardGapPolicy() {
        // In America/New_York, 2026-03-08 transitions from EST to EDT at 02:00:00 -> 03:00:00.
        // 02:30:00 does not exist locally.
        // Policy: java.time shifts to 03:30 EDT (-04:00) = 07:30 UTC.
        Instant ref = Instant.parse("2026-03-08T05:00:00Z"); // 00:00 EST on transition day
        LocalTime scheduledTime = LocalTime.of(2, 30);

        Instant nextRun = calculator.calculateNextRun(
            ScheduleType.DAILY, scheduledTime, null, null, "America/New_York", ref
        );

        // 03:30:00 EDT is 07:30:00 UTC
        assertThat(nextRun).isEqualTo(Instant.parse("2026-03-08T07:30:00Z"));
    }

    @Test
    @DisplayName("DST Fall-Back Overlap: Chooses earlier offset (EDT) for ambiguous 01:30 AM")
    void shouldHandleDaylightSavingFallBackOverlapPolicy() {
        // In America/New_York, 2026-11-01 transitions from EDT to EST at 02:00:00 -> 01:00:00.
        // 01:30:00 occurs twice: 01:30 EDT (-04:00, 05:30 UTC) and 01:30 EST (-05:00, 06:30 UTC).
        // Policy: selects the earlier offset (EDT).
        Instant ref = Instant.parse("2026-11-01T04:00:00Z"); // 00:00 EDT on transition day
        LocalTime scheduledTime = LocalTime.of(1, 30);

        Instant nextRun = calculator.calculateNextRun(
            ScheduleType.DAILY, scheduledTime, null, null, "America/New_York", ref
        );

        // Earlier offset EDT (-04:00) gives 05:30:00 UTC
        assertThat(nextRun).isEqualTo(Instant.parse("2026-11-01T05:30:00Z"));
    }

    @Test
    @DisplayName("Time Zone: Invalid IANA identifier throws WorkflowValidationException")
    void shouldRejectInvalidTimeZone() {
        Instant ref = Instant.parse("2026-10-23T10:00:00Z");

        assertThatThrownBy(() -> calculator.calculateNextRun(
            ScheduleType.DAILY, LocalTime.of(9, 0), null, null, "Mars/Olympus_Mons", ref
        ))
        .isInstanceOf(WorkflowValidationException.class)
        .hasMessageContaining("Invalid time zone: 'Mars/Olympus_Mons'");
    }

    @Test
    @DisplayName("Schedule Entity: Disabled schedule returns null nextRunAt")
    void shouldReturnNullWhenScheduleIsDisabled() {
        Workflow workflow = new Workflow("Sample", "Desc", null, true);
        WorkflowSchedule schedule = new WorkflowSchedule(workflow, ScheduleType.DAILY, "UTC");
        schedule.setScheduledTime(LocalTime.of(10, 0));
        schedule.setEnabled(false);

        Instant nextRun = calculator.calculateNextRun(schedule, Instant.now());
        assertThat(nextRun).isNull();
    }
}
