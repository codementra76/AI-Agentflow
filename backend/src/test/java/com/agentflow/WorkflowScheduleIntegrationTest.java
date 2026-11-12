package com.agentflow;

import com.agentflow.dto.ScheduleStatusRequest;
import com.agentflow.dto.WorkflowScheduleRequest;
import com.agentflow.entity.ScheduleType;
import com.agentflow.entity.Workflow;
import com.agentflow.entity.WorkflowSchedule;
import com.agentflow.repository.WorkflowRepository;
import com.agentflow.repository.WorkflowScheduleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WorkflowScheduleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private WorkflowScheduleRepository scheduleRepository;

    private Workflow testWorkflow;

    @BeforeEach
    void setUp() {
        scheduleRepository.deleteAll();
        workflowRepository.deleteAll();

        Workflow workflow = new Workflow("Scheduled Pipeline Test", "Test description", null, true);
        testWorkflow = workflowRepository.save(workflow);
    }

    @Test
    @DisplayName("PUT then GET: Create daily schedule and verify persistence and nextRunAt calculation")
    void shouldCreateDailyScheduleAndRetrieveIt() throws Exception {
        WorkflowScheduleRequest request = new WorkflowScheduleRequest(
            ScheduleType.DAILY,
            LocalTime.of(14, 30),
            null,
            null,
            null,
            "America/New_York",
            objectMapper.createObjectNode().put("env", "staging"),
            true
        );

        mockMvc.perform(put("/api/v1/workflows/" + testWorkflow.getId() + "/schedule")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.workflowId").value(testWorkflow.getId()))
            .andExpect(jsonPath("$.data.scheduleType").value("DAILY"))
            .andExpect(jsonPath("$.data.scheduledTime").value("14:30:00"))
            .andExpect(jsonPath("$.data.timeZone").value("America/New_York"))
            .andExpect(jsonPath("$.data.enabled").value(true))
            .andExpect(jsonPath("$.data.nextRunAt").isNotEmpty())
            .andExpect(jsonPath("$.data.inputData.env").value("staging"));

        // GET schedule
        mockMvc.perform(get("/api/v1/workflows/" + testWorkflow.getId() + "/schedule"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.workflowId").value(testWorkflow.getId()))
            .andExpect(jsonPath("$.data.scheduleType").value("DAILY"))
            .andExpect(jsonPath("$.data.scheduledTime").value("14:30:00"));

        assertThat(scheduleRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("PUT update: Overwrites existing schedule safely (one-schedule-per-workflow)")
    void shouldUpdateExistingScheduleToWeekly() throws Exception {
        // Initial daily schedule
        WorkflowSchedule schedule = new WorkflowSchedule(testWorkflow, ScheduleType.DAILY, "UTC");
        schedule.setScheduledTime(LocalTime.of(8, 0));
        schedule.setNextRunAt(Instant.now().plus(1, ChronoUnit.DAYS));
        scheduleRepository.save(schedule);

        // Update to weekly with multiple days
        WorkflowScheduleRequest updateRequest = new WorkflowScheduleRequest(
            ScheduleType.WEEKLY,
            LocalTime.of(9, 15),
            List.of("MONDAY", "WEDNESDAY", "FRIDAY"),
            null,
            null,
            "UTC",
            null,
            true
        );

        mockMvc.perform(put("/api/v1/workflows/" + testWorkflow.getId() + "/schedule")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.scheduleType").value("WEEKLY"))
            .andExpect(jsonPath("$.data.scheduledTime").value("09:15:00"))
            .andExpect(jsonPath("$.data.daysOfWeek", hasItems("MONDAY", "WEDNESDAY", "FRIDAY")))
            .andExpect(jsonPath("$.data.nextRunAt").isNotEmpty());

        assertThat(scheduleRepository.count()).isEqualTo(1);
        WorkflowSchedule updated = scheduleRepository.findByWorkflowId(testWorkflow.getId()).orElseThrow();
        assertThat(updated.getScheduleType()).isEqualTo(ScheduleType.WEEKLY);
        assertThat(updated.getDaysOfWeekSet()).containsExactly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
    }

    @Test
    @DisplayName("PATCH /status: Disabling clears nextRunAt; Re-enabling recomputes nextRunAt")
    void shouldToggleScheduleStatusAndRecalculateNextRun() throws Exception {
        WorkflowSchedule schedule = new WorkflowSchedule(testWorkflow, ScheduleType.DAILY, "UTC");
        schedule.setScheduledTime(LocalTime.of(12, 0));
        schedule.setNextRunAt(Instant.now().plus(2, ChronoUnit.HOURS));
        schedule.setEnabled(true);
        scheduleRepository.save(schedule);

        // 1. Disable schedule
        ScheduleStatusRequest disableReq = new ScheduleStatusRequest(false);
        mockMvc.perform(patch("/api/v1/workflows/" + testWorkflow.getId() + "/schedule/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(disableReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.enabled").value(false))
            .andExpect(jsonPath("$.data.nextRunAt").isEmpty());

        WorkflowSchedule disabledInDb = scheduleRepository.findByWorkflowId(testWorkflow.getId()).orElseThrow();
        assertThat(disabledInDb.isEnabled()).isFalse();
        assertThat(disabledInDb.getNextRunAt()).isNull();

        // 2. Re-enable schedule
        ScheduleStatusRequest enableReq = new ScheduleStatusRequest(true);
        mockMvc.perform(patch("/api/v1/workflows/" + testWorkflow.getId() + "/schedule/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(enableReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.enabled").value(true))
            .andExpect(jsonPath("$.data.nextRunAt").isNotEmpty());

        WorkflowSchedule reEnabledInDb = scheduleRepository.findByWorkflowId(testWorkflow.getId()).orElseThrow();
        assertThat(reEnabledInDb.isEnabled()).isTrue();
        assertThat(reEnabledInDb.getNextRunAt()).isNotNull();
    }

    @Test
    @DisplayName("ONE_TIME: Re-enabling after scheduled time has passed is rejected")
    void shouldRejectReEnablingPastOneTimeSchedule() throws Exception {
        // Disabled one-time schedule in the past
        WorkflowSchedule schedule = new WorkflowSchedule(testWorkflow, ScheduleType.ONE_TIME, "UTC");
        schedule.setOneTimeExecutionAt(Instant.now().minus(1, ChronoUnit.HOURS));
        schedule.setEnabled(false);
        schedule.setNextRunAt(null);
        scheduleRepository.save(schedule);

        ScheduleStatusRequest enableReq = new ScheduleStatusRequest(true);
        mockMvc.perform(patch("/api/v1/workflows/" + testWorkflow.getId() + "/schedule/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(enableReq)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("Cannot enable one-time schedule because scheduled execution time")));
    }

    @Test
    @DisplayName("Validation: Reject invalid time zone with 400 Bad Request")
    void shouldRejectInvalidTimeZone() throws Exception {
        WorkflowScheduleRequest request = new WorkflowScheduleRequest(
            ScheduleType.DAILY,
            LocalTime.of(10, 0),
            null,
            null,
            null,
            "Invalid/Timezone_Name",
            null,
            true
        );

        mockMvc.perform(put("/api/v1/workflows/" + testWorkflow.getId() + "/schedule")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("Invalid time zone: 'Invalid/Timezone_Name'")));
    }

    @Test
    @DisplayName("Validation: Reject WEEKLY schedule with invalid weekday")
    void shouldRejectInvalidWeekday() throws Exception {
        WorkflowScheduleRequest request = new WorkflowScheduleRequest(
            ScheduleType.WEEKLY,
            LocalTime.of(10, 0),
            List.of("MONDAY", "NOT_A_DAY"),
            null,
            null,
            "UTC",
            null,
            true
        );

        mockMvc.perform(put("/api/v1/workflows/" + testWorkflow.getId() + "/schedule")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("Invalid day of week: 'NOT_A_DAY'")));
    }

    @Test
    @DisplayName("Validation: Reject ONE_TIME schedule with past date when enabled")
    void shouldRejectPastOneTimeScheduleOnCreate() throws Exception {
        WorkflowScheduleRequest request = new WorkflowScheduleRequest(
            ScheduleType.ONE_TIME,
            null,
            null,
            null,
            Instant.now().minus(10, ChronoUnit.MINUTES),
            "UTC",
            null,
            true
        );

        mockMvc.perform(put("/api/v1/workflows/" + testWorkflow.getId() + "/schedule")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("One-time execution time must be in the future")));
    }

    @Test
    @DisplayName("Missing Resource: GET/DELETE on non-existent workflow returns 404")
    void shouldReturn404ForNonExistentWorkflow() throws Exception {
        mockMvc.perform(get("/api/v1/workflows/99999/schedule"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message", containsString("Workflow not found with id: 99999")));

        mockMvc.perform(delete("/api/v1/workflows/99999/schedule"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message", containsString("Workflow not found with id: 99999")));
    }

    @Test
    @DisplayName("Missing Resource: GET on workflow without schedule returns 404")
    void shouldReturn404ForWorkflowWithoutSchedule() throws Exception {
        mockMvc.perform(get("/api/v1/workflows/" + testWorkflow.getId() + "/schedule"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message", containsString("Schedule not found for workflow id: " + testWorkflow.getId())));
    }

    @Test
    @DisplayName("DELETE /schedule: Deletes schedule, leaves workflow intact")
    void shouldDeleteScheduleSuccessfully() throws Exception {
        WorkflowSchedule schedule = new WorkflowSchedule(testWorkflow, ScheduleType.DAILY, "UTC");
        schedule.setScheduledTime(LocalTime.of(10, 0));
        scheduleRepository.save(schedule);

        mockMvc.perform(delete("/api/v1/workflows/" + testWorkflow.getId() + "/schedule"))
            .andExpect(status().isNoContent());

        assertThat(scheduleRepository.findByWorkflowId(testWorkflow.getId())).isEmpty();
        assertThat(workflowRepository.existsById(testWorkflow.getId())).isTrue();
    }

    @Test
    @DisplayName("Cascade Delete: Deleting workflow automatically removes associated schedule")
    void shouldCascadeDeleteScheduleWhenWorkflowIsDeleted() {
        WorkflowSchedule schedule = new WorkflowSchedule(testWorkflow, ScheduleType.DAILY, "UTC");
        schedule.setScheduledTime(LocalTime.of(12, 0));
        scheduleRepository.save(schedule);

        assertThat(scheduleRepository.existsByWorkflowId(testWorkflow.getId())).isTrue();

        workflowRepository.delete(testWorkflow);

        assertThat(scheduleRepository.existsByWorkflowId(testWorkflow.getId())).isFalse();
    }

    @Test
    @DisplayName("Repository: Querying due schedules filters by enabled and nextRunAt")
    void shouldQueryDueSchedulesAccurately() {
        Instant now = Instant.now();

        // 1. Due enabled schedule
        WorkflowSchedule due = new WorkflowSchedule(testWorkflow, ScheduleType.DAILY, "UTC");
        due.setEnabled(true);
        due.setNextRunAt(now.minus(1, ChronoUnit.MINUTES));
        scheduleRepository.save(due);

        // 2. Future enabled schedule
        Workflow wfFuture = workflowRepository.save(new Workflow("Future WF", "Desc", null, true));
        WorkflowSchedule future = new WorkflowSchedule(wfFuture, ScheduleType.DAILY, "UTC");
        future.setEnabled(true);
        future.setNextRunAt(now.plus(1, ChronoUnit.HOURS));
        scheduleRepository.save(future);

        // 3. Past but DISABLED schedule
        Workflow wfDisabled = workflowRepository.save(new Workflow("Disabled WF", "Desc", null, true));
        WorkflowSchedule disabled = new WorkflowSchedule(wfDisabled, ScheduleType.DAILY, "UTC");
        disabled.setEnabled(false);
        disabled.setNextRunAt(now.minus(5, ChronoUnit.MINUTES));
        scheduleRepository.save(disabled);

        List<WorkflowSchedule> dueSchedules = scheduleRepository.findByEnabledTrueAndNextRunAtLessThanEqual(now);

        assertThat(dueSchedules).hasSize(1);
        assertThat(dueSchedules.get(0).getId()).isEqualTo(due.getId());
    }
}
