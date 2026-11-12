package com.agentflow.service.impl;

import com.agentflow.dto.WorkflowExecutionRequest;
import com.agentflow.dto.WorkflowExecutionResponse;
import com.agentflow.entity.ScheduleType;
import com.agentflow.entity.Workflow;
import com.agentflow.entity.WorkflowSchedule;
import com.agentflow.repository.WorkflowExecutionRepository;
import com.agentflow.repository.WorkflowRepository;
import com.agentflow.repository.WorkflowScheduleRepository;
import com.agentflow.service.WorkflowExecutionService;
import com.agentflow.service.WorkflowSchedulerService;
import com.agentflow.workflow.schedule.ScheduleCalculator;
import com.agentflow.workflow.schedule.ScheduleClaimHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Production scheduler engine responsible for polling due schedules, atomically
 * claiming them, enforcing safety policies (missed runs, inactive workflows, overlap
 * prevention), dispatching workflow executions, and advancing schedule records.
 */
@Service
public class WorkflowSchedulerServiceImpl implements WorkflowSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowSchedulerServiceImpl.class);
    private static final Duration STALE_THRESHOLD = Duration.ofHours(24);

    private final WorkflowScheduleRepository scheduleRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowExecutionRepository executionRepository;
    private final WorkflowExecutionService executionService;
    private final ScheduleCalculator scheduleCalculator;
    private final ScheduleClaimHelper claimHelper;
    private final Clock clock;

    public WorkflowSchedulerServiceImpl(
            WorkflowScheduleRepository scheduleRepository,
            WorkflowRepository workflowRepository,
            WorkflowExecutionRepository executionRepository,
            WorkflowExecutionService executionService,
            ScheduleCalculator scheduleCalculator,
            ScheduleClaimHelper claimHelper,
            Clock clock) {
        this.scheduleRepository = scheduleRepository;
        this.workflowRepository = workflowRepository;
        this.executionRepository = executionRepository;
        this.executionService = executionService;
        this.scheduleCalculator = scheduleCalculator;
        this.claimHelper = claimHelper;
        this.clock = clock;
    }

    @Override
    @Scheduled(fixedDelayString = "${agentflow.scheduling.poll-interval-ms:15000}")
    public void pollAndExecuteDueSchedules() {
        processDueSchedules(clock.instant());
    }

    @Override
    public int processDueSchedules(Instant referenceInstant) {
        Instant now = referenceInstant != null ? referenceInstant : clock.instant();
        List<WorkflowSchedule> dueSchedules = scheduleRepository.findByEnabledTrueAndNextRunAtLessThanEqual(now);

        if (dueSchedules.isEmpty()) {
            return 0;
        }

        log.debug("Found {} due schedule(s) for evaluation at reference instant {}", dueSchedules.size(), now);
        int processedCount = 0;

        for (WorkflowSchedule schedule : dueSchedules) {
            try {
                boolean handled = processSingleSchedule(schedule, now);
                if (handled) {
                    processedCount++;
                }
            } catch (Exception ex) {
                log.error("Unexpected error processing schedule ID [{}]: {}", schedule.getId(), ex.getMessage(), ex);
            }
        }

        return processedCount;
    }

    private boolean processSingleSchedule(WorkflowSchedule schedule, Instant now) {
        Long scheduleId = schedule.getId();
        Long workflowId = schedule.getWorkflow().getId();
        Instant expectedDue = schedule.getNextRunAt();
        ScheduleType type = schedule.getScheduleType();

        // 1. Determine next future run and whether schedule remains enabled
        Instant nextRunAfter;
        boolean nextEnabled;

        if (type == ScheduleType.ONE_TIME) {
            nextRunAfter = null;
            nextEnabled = false;
        } else {
            nextRunAfter = scheduleCalculator.calculateNextRun(schedule, now);
            nextEnabled = true;
        }

        // 2. Missed-run policy: if overdue by 24h or more, do not execute; advance and record MISSED
        Duration overdueDuration = Duration.between(expectedDue, now);
        if (overdueDuration.compareTo(STALE_THRESHOLD) >= 0) {
            log.warn("Schedule [{}] for workflow [{}] is overdue by {} hours (due: {}, now: {}). Marking MISSED without executing.",
                scheduleId, workflowId, overdueDuration.toHours(), expectedDue, now);
            int claimed = claimHelper.claimSchedule(scheduleId, expectedDue, nextRunAfter, nextEnabled, now, "MISSED");
            return claimed > 0;
        }

        // 3. Overdue < 24h: Perform atomic claim for catch-up or on-time execution
        int claimed = claimHelper.claimSchedule(scheduleId, expectedDue, nextRunAfter, nextEnabled, now, "RUNNING");
        if (claimed == 0) {
            log.debug("Schedule [{}] already claimed by another poller or updated; skipping.", scheduleId);
            return false;
        }

        // 4. Verify workflow exists and is active
        Optional<Workflow> workflowOpt = workflowRepository.findById(workflowId);
        if (workflowOpt.isEmpty() || !workflowOpt.get().isActive()) {
            log.warn("Workflow [{}] is inactive or does not exist. Skipping scheduled execution.", workflowId);
            claimHelper.updateStatus(scheduleId, "SKIPPED_INACTIVE", now);
            return true;
        }

        // 5. Overlap prevention: verify no existing execution has status RUNNING
        boolean isAlreadyRunning = executionRepository.existsByWorkflowIdAndStatus(workflowId, "RUNNING");
        if (isAlreadyRunning) {
            log.warn("Workflow [{}] currently has a RUNNING execution. Skipping overlapping scheduled execution.", workflowId);
            claimHelper.updateStatus(scheduleId, "SKIPPED_OVERLAP", now);
            return true;
        }

        // 6. Launch execution with persisted input data and trigger_type = SCHEDULED
        try {
            log.info("Launching scheduled execution for workflow [{}] (schedule ID: {})", workflowId, scheduleId);
            WorkflowExecutionRequest request = new WorkflowExecutionRequest(schedule.getInputData());
            WorkflowExecutionResponse response = executionService.executeWorkflow(workflowId, request, "SCHEDULED");

            String finalStatus = "FAILED".equalsIgnoreCase(response.status()) ? "FAILED" : "COMPLETED";
            claimHelper.updateStatus(scheduleId, finalStatus, clock.instant());
        } catch (Exception ex) {
            log.error("Execution failed for scheduled workflow [{}]: {}", workflowId, ex.getMessage(), ex);
            claimHelper.updateStatus(scheduleId, "FAILED", clock.instant());
        }

        return true;
    }
}
