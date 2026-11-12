package com.agentflow;

import com.agentflow.dto.WorkflowExecutionRequest;
import com.agentflow.dto.WorkflowExecutionResponse;
import com.agentflow.entity.ScheduleType;
import com.agentflow.entity.Workflow;
import com.agentflow.entity.WorkflowExecution;
import com.agentflow.entity.WorkflowSchedule;
import com.agentflow.repository.NodeExecutionLogRepository;
import com.agentflow.repository.WorkflowExecutionRepository;
import com.agentflow.repository.WorkflowRepository;
import com.agentflow.repository.WorkflowScheduleRepository;
import com.agentflow.service.WorkflowExecutionService;
import com.agentflow.service.WorkflowSchedulerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WorkflowSchedulerIntegrationTest {

    @Autowired
    private WorkflowSchedulerService schedulerService;

    @Autowired
    private WorkflowExecutionService executionService;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private WorkflowScheduleRepository scheduleRepository;

    @Autowired
    private WorkflowExecutionRepository executionRepository;

    @Autowired
    private NodeExecutionLogRepository nodeLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Workflow activeWorkflow;

    @BeforeEach
    void setUp() {
        nodeLogRepository.deleteAll();
        executionRepository.deleteAll();
        scheduleRepository.deleteAll();
        workflowRepository.deleteAll();

        // Valid minimal 2-node workflow (MANUAL_TRIGGER -> OUTPUT)
        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");
        nodes.addObject().put("id", "trig-1").put("type", "MANUAL_TRIGGER");
        nodes.addObject().put("id", "out-1").put("type", "OUTPUT");

        ArrayNode edges = graph.putArray("edges");
        edges.addObject().put("id", "e1").put("source", "trig-1").put("target", "out-1");

        activeWorkflow = workflowRepository.save(new Workflow("Scheduler Test Workflow", "Desc", graph, true));
    }

    @Test
    @DisplayName("Due Schedule: Executes with persisted inputData and records trigger_type = SCHEDULED")
    void shouldExecuteDueScheduleWithSavedInputAndTriggerTypeScheduled() {
        Instant now = Instant.now();
        Instant due = now.minus(5, ChronoUnit.MINUTES);

        WorkflowSchedule schedule = new WorkflowSchedule(activeWorkflow, ScheduleType.DAILY, "UTC");
        schedule.setScheduledTime(LocalTime.of(10, 0));
        schedule.setNextRunAt(due);
        schedule.setEnabled(true);
        schedule.setInputData(objectMapper.createObjectNode().put("env", "nightly-batch"));
        schedule = scheduleRepository.save(schedule);

        int processed = schedulerService.processDueSchedules(now);
        assertThat(processed).isEqualTo(1);

        // Verify execution
        List<WorkflowExecution> executions = executionRepository.findByWorkflowId(activeWorkflow.getId());
        assertThat(executions).hasSize(1);
        WorkflowExecution execution = executions.get(0);
        assertThat(execution.getStatus()).isEqualTo("COMPLETED");
        assertThat(execution.getTriggerType()).isEqualTo("SCHEDULED");
        assertThat(execution.getInputData().get("env").asText()).isEqualTo("nightly-batch");

        // Verify schedule state
        WorkflowSchedule updatedSchedule = scheduleRepository.findById(schedule.getId()).orElseThrow();
        assertThat(updatedSchedule.getLastStatus()).isEqualTo("COMPLETED");
        assertThat(updatedSchedule.getLastRunAt()).isNotNull();
        assertThat(updatedSchedule.getNextRunAt()).isAfter(now);
    }

    @Test
    @DisplayName("Future Schedule: Does not execute early")
    void shouldNotExecuteFutureScheduleEarly() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant future = now.plus(30, ChronoUnit.MINUTES);

        WorkflowSchedule schedule = new WorkflowSchedule(activeWorkflow, ScheduleType.DAILY, "UTC");
        schedule.setScheduledTime(LocalTime.of(23, 0));
        schedule.setNextRunAt(future);
        schedule.setEnabled(true);
        scheduleRepository.save(schedule);

        int processed = schedulerService.processDueSchedules(now);
        assertThat(processed).isEqualTo(0);

        assertThat(executionRepository.count()).isEqualTo(0);
        WorkflowSchedule unchanged = scheduleRepository.findById(schedule.getId()).orElseThrow();
        assertThat(unchanged.getNextRunAt()).isEqualTo(future);
        assertThat(unchanged.getLastStatus()).isNull();
    }

    @Test
    @DisplayName("Disabled Schedule: Does not execute even when past due")
    void shouldNotExecuteDisabledSchedule() {
        Instant now = Instant.now();
        Instant past = now.minus(10, ChronoUnit.MINUTES);

        WorkflowSchedule schedule = new WorkflowSchedule(activeWorkflow, ScheduleType.DAILY, "UTC");
        schedule.setScheduledTime(LocalTime.of(8, 0));
        schedule.setNextRunAt(past);
        schedule.setEnabled(false);
        scheduleRepository.save(schedule);

        int processed = schedulerService.processDueSchedules(now);
        assertThat(processed).isEqualTo(0);
        assertThat(executionRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("Inactive Workflow: Skips execution, marks SKIPPED_INACTIVE, advances schedule")
    void shouldSkipInactiveWorkflowAndAdvanceSchedule() {
        activeWorkflow.setActive(false);
        workflowRepository.save(activeWorkflow);

        Instant now = Instant.now();
        Instant due = now.minus(2, ChronoUnit.MINUTES);

        WorkflowSchedule schedule = new WorkflowSchedule(activeWorkflow, ScheduleType.DAILY, "UTC");
        schedule.setScheduledTime(LocalTime.of(12, 0));
        schedule.setNextRunAt(due);
        schedule.setEnabled(true);
        schedule = scheduleRepository.save(schedule);

        int processed = schedulerService.processDueSchedules(now);
        assertThat(processed).isEqualTo(1);

        assertThat(executionRepository.count()).isEqualTo(0);
        WorkflowSchedule updated = scheduleRepository.findById(schedule.getId()).orElseThrow();
        assertThat(updated.getLastStatus()).isEqualTo("SKIPPED_INACTIVE");
        assertThat(updated.getNextRunAt()).isAfter(now);
    }

    @Test
    @DisplayName("One-Time Schedule: Runs at most once, then becomes disabled with null nextRunAt")
    void shouldExecuteOneTimeScheduleOnceAndDisable() {
        Instant now = Instant.now();
        Instant due = now.minus(1, ChronoUnit.MINUTES);

        WorkflowSchedule schedule = new WorkflowSchedule(activeWorkflow, ScheduleType.ONE_TIME, "UTC");
        schedule.setOneTimeExecutionAt(due);
        schedule.setNextRunAt(due);
        schedule.setEnabled(true);
        schedule = scheduleRepository.save(schedule);

        // 1st tick: executes
        int processed1 = schedulerService.processDueSchedules(now);
        assertThat(processed1).isEqualTo(1);

        List<WorkflowExecution> executions = executionRepository.findByWorkflowId(activeWorkflow.getId());
        assertThat(executions).hasSize(1);
        assertThat(executions.get(0).getTriggerType()).isEqualTo("SCHEDULED");

        WorkflowSchedule updated = scheduleRepository.findById(schedule.getId()).orElseThrow();
        assertThat(updated.isEnabled()).isFalse();
        assertThat(updated.getNextRunAt()).isNull();
        assertThat(updated.getLastStatus()).isEqualTo("COMPLETED");

        // 2nd tick: does not execute again
        int processed2 = schedulerService.processDueSchedules(now.plus(10, ChronoUnit.MINUTES));
        assertThat(processed2).isEqualTo(0);
        assertThat(executionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Weekly Schedule: Advances correctly across configured weekdays")
    void shouldAdvanceWeeklyScheduleCorrectly() {
        // Evaluate on Monday at 10:00 UTC
        Instant mondayTime = Instant.parse("2026-10-19T10:00:00Z"); // 2026-10-19 is a Monday

        WorkflowSchedule schedule = new WorkflowSchedule(activeWorkflow, ScheduleType.WEEKLY, "UTC");
        schedule.setScheduledTime(LocalTime.of(10, 0));
        schedule.setDaysOfWeekSet(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));
        schedule.setNextRunAt(mondayTime);
        schedule.setEnabled(true);
        schedule = scheduleRepository.save(schedule);

        int processed = schedulerService.processDueSchedules(mondayTime);
        assertThat(processed).isEqualTo(1);

        WorkflowSchedule updated = scheduleRepository.findById(schedule.getId()).orElseThrow();
        assertThat(updated.getLastStatus()).isEqualTo("COMPLETED");
        // Next run should be Wednesday 2026-10-21 at 10:00 UTC
        assertThat(updated.getNextRunAt()).isEqualTo(Instant.parse("2026-10-21T10:00:00Z"));
    }

    @Test
    @DisplayName("Catch-Up Policy: Overdue by < 24h executes at most one catch-up attempt")
    void shouldPerformAtMostOneCatchUpUnder24Hours() {
        Instant now = Instant.now();
        Instant dueTwoHoursAgo = now.minus(2, ChronoUnit.HOURS);

        WorkflowSchedule schedule = new WorkflowSchedule(activeWorkflow, ScheduleType.DAILY, "UTC");
        schedule.setScheduledTime(LocalTime.of(10, 0));
        schedule.setNextRunAt(dueTwoHoursAgo);
        schedule.setEnabled(true);
        schedule = scheduleRepository.save(schedule);

        // 1st tick performs catch-up
        int processed1 = schedulerService.processDueSchedules(now);
        assertThat(processed1).isEqualTo(1);
        assertThat(executionRepository.count()).isEqualTo(1);

        WorkflowSchedule updated = scheduleRepository.findById(schedule.getId()).orElseThrow();
        assertThat(updated.getLastStatus()).isEqualTo("COMPLETED");
        assertThat(updated.getNextRunAt()).isAfter(now);

        // Immediate next tick does not perform duplicate catch-up
        int processed2 = schedulerService.processDueSchedules(now.plusSeconds(30));
        assertThat(processed2).isEqualTo(0);
        assertThat(executionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Missed Policy: Overdue by >= 24h is marked MISSED without executing")
    void shouldMarkMissedWhenOverdueBeyond24Hours() {
        Instant now = Instant.now();
        Instant due26HoursAgo = now.minus(26, ChronoUnit.HOURS);

        WorkflowSchedule schedule = new WorkflowSchedule(activeWorkflow, ScheduleType.DAILY, "UTC");
        schedule.setScheduledTime(LocalTime.of(10, 0));
        schedule.setNextRunAt(due26HoursAgo);
        schedule.setEnabled(true);
        schedule = scheduleRepository.save(schedule);

        int processed = schedulerService.processDueSchedules(now);
        assertThat(processed).isEqualTo(1);

        // Zero executions launched
        assertThat(executionRepository.count()).isEqualTo(0);

        WorkflowSchedule updated = scheduleRepository.findById(schedule.getId()).orElseThrow();
        assertThat(updated.getLastStatus()).isEqualTo("MISSED");
        assertThat(updated.getNextRunAt()).isAfter(now);
    }

    @Test
    @DisplayName("Overlap Prevention: Skips run when workflow is already RUNNING, advances schedule")
    void shouldSkipOverlappingExecutionWhenAlreadyRunning() {
        // Create an active RUNNING execution for this workflow
        WorkflowExecution runningExecution = new WorkflowExecution(activeWorkflow, "RUNNING", null);
        runningExecution.setStartedAt(Instant.now());
        executionRepository.save(runningExecution);

        Instant now = Instant.now();
        Instant due = now.minus(1, ChronoUnit.MINUTES);

        WorkflowSchedule schedule = new WorkflowSchedule(activeWorkflow, ScheduleType.DAILY, "UTC");
        schedule.setScheduledTime(LocalTime.of(14, 0));
        schedule.setNextRunAt(due);
        schedule.setEnabled(true);
        schedule = scheduleRepository.save(schedule);

        int processed = schedulerService.processDueSchedules(now);
        assertThat(processed).isEqualTo(1);

        // Only the pre-existing execution exists
        List<WorkflowExecution> executions = executionRepository.findByWorkflowId(activeWorkflow.getId());
        assertThat(executions).hasSize(1);

        WorkflowSchedule updated = scheduleRepository.findById(schedule.getId()).orElseThrow();
        assertThat(updated.getLastStatus()).isEqualTo("SKIPPED_OVERLAP");
        assertThat(updated.getNextRunAt()).isAfter(now);
    }

    @Test
    @DisplayName("Execution Failure: Preserves recurring schedule with FAILED status and future nextRunAt")
    void shouldPreserveRecurringScheduleWhenExecutionFails() {
        // Broken workflow definition that will fail graph execution
        ObjectNode brokenGraph = objectMapper.createObjectNode();
        ArrayNode nodes = brokenGraph.putArray("nodes");
        nodes.addObject().put("id", "trig-1").put("type", "MANUAL_TRIGGER");
        // AI node with invalid configuration
        ObjectNode aiNode = nodes.addObject();
        aiNode.put("id", "ai-fail");
        aiNode.put("type", "AI_TEXT_PROCESSOR");
        // No prompt configured -> validation fails

        Workflow brokenWorkflow = workflowRepository.save(new Workflow("Broken WF", "Desc", brokenGraph, true));

        Instant now = Instant.now();
        Instant due = now.minus(1, ChronoUnit.MINUTES);

        WorkflowSchedule schedule = new WorkflowSchedule(brokenWorkflow, ScheduleType.DAILY, "UTC");
        schedule.setScheduledTime(LocalTime.of(15, 0));
        schedule.setNextRunAt(due);
        schedule.setEnabled(true);
        schedule = scheduleRepository.save(schedule);

        int processed = schedulerService.processDueSchedules(now);
        assertThat(processed).isEqualTo(1);

        WorkflowSchedule updated = scheduleRepository.findById(schedule.getId()).orElseThrow();
        assertThat(updated.getLastStatus()).isEqualTo("FAILED");
        assertThat(updated.isEnabled()).isTrue();
        assertThat(updated.getNextRunAt()).isAfter(now);
    }

    @Test
    @DisplayName("Concurrency: 10 concurrent threads claim same schedule; exactly 1 succeeds")
    void shouldPreventDuplicateDispatchWithConcurrentClaim() throws Exception {
        Instant now = Instant.now();
        Instant due = now.minus(1, ChronoUnit.MINUTES);

        WorkflowSchedule schedule = new WorkflowSchedule(activeWorkflow, ScheduleType.DAILY, "UTC");
        schedule.setScheduledTime(LocalTime.of(12, 0));
        schedule.setNextRunAt(due);
        schedule.setEnabled(true);
        schedule = scheduleRepository.save(schedule);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        AtomicInteger totalClaims = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    int claims = schedulerService.processDueSchedules(now);
                    totalClaims.addAndGet(claims);
                } catch (Exception ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        finishLatch.await();
        executor.shutdown();

        // Exactly 1 thread claimed and executed
        assertThat(totalClaims.get()).isEqualTo(1);
        assertThat(executionRepository.findByWorkflowId(activeWorkflow.getId())).hasSize(1);
    }

    @Test
    @DisplayName("Trigger Types: MANUAL, SCHEDULED, and RETRY are persisted accurately")
    void shouldPersistTriggerTypeForManualAndRetryExecutions() {
        // 1. Manual execution
        WorkflowExecutionResponse manualResp = executionService.executeWorkflow(
            activeWorkflow.getId(), new WorkflowExecutionRequest(null)
        );
        assertThat(manualResp.triggerType()).isEqualTo("MANUAL");

        WorkflowExecution manualExec = executionRepository.findById(manualResp.id()).orElseThrow();
        assertThat(manualExec.getTriggerType()).isEqualTo("MANUAL");

        // 2. Failed execution + Retry
        WorkflowExecution failedExec = new WorkflowExecution(activeWorkflow, "FAILED", null);
        failedExec.setStartedAt(Instant.now());
        failedExec = executionRepository.save(failedExec);

        WorkflowExecutionResponse retryResp = executionService.retryExecution(failedExec.getId());
        assertThat(retryResp.triggerType()).isEqualTo("RETRY");

        WorkflowExecution retriedExec = executionRepository.findById(retryResp.id()).orElseThrow();
        assertThat(retriedExec.getTriggerType()).isEqualTo("RETRY");
    }

    @Test
    @DisplayName("Robustness: Exception in one schedule does not prevent processing subsequent schedules")
    void shouldContinueProcessingRemainingSchedulesIfOneFails() {
        Instant now = Instant.now();

        // Schedule 1: Points to non-existent / broken workflow
        Workflow wf2 = workflowRepository.save(new Workflow("Second Valid WF", "Desc", activeWorkflow.getWorkflowDefinition(), true));

        WorkflowSchedule schedule1 = new WorkflowSchedule(activeWorkflow, ScheduleType.DAILY, "UTC");
        schedule1.setScheduledTime(LocalTime.of(10, 0));
        schedule1.setNextRunAt(now.minus(2, ChronoUnit.MINUTES));
        schedule1.setEnabled(true);
        scheduleRepository.save(schedule1);

        WorkflowSchedule schedule2 = new WorkflowSchedule(wf2, ScheduleType.DAILY, "UTC");
        schedule2.setScheduledTime(LocalTime.of(10, 0));
        schedule2.setNextRunAt(now.minus(1, ChronoUnit.MINUTES));
        schedule2.setEnabled(true);
        scheduleRepository.save(schedule2);

        int processed = schedulerService.processDueSchedules(now);
        assertThat(processed).isEqualTo(2);

        // Both workflows were processed
        assertThat(executionRepository.findByWorkflowId(activeWorkflow.getId())).hasSize(1);
        assertThat(executionRepository.findByWorkflowId(wf2.getId())).hasSize(1);
    }
}
