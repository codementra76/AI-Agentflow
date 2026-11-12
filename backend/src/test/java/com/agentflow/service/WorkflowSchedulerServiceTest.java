package com.agentflow.service;

import com.agentflow.dto.WorkflowExecutionRequest;
import com.agentflow.dto.WorkflowExecutionResponse;
import com.agentflow.entity.ScheduleType;
import com.agentflow.entity.Workflow;
import com.agentflow.entity.WorkflowSchedule;
import com.agentflow.repository.WorkflowExecutionRepository;
import com.agentflow.repository.WorkflowRepository;
import com.agentflow.repository.WorkflowScheduleRepository;
import com.agentflow.service.impl.WorkflowSchedulerServiceImpl;
import com.agentflow.workflow.schedule.ScheduleCalculator;
import com.agentflow.workflow.schedule.ScheduleClaimHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowSchedulerServiceTest {

    @Mock
    private WorkflowScheduleRepository scheduleRepository;

    @Mock
    private WorkflowRepository workflowRepository;

    @Mock
    private WorkflowExecutionRepository executionRepository;

    @Mock
    private WorkflowExecutionService executionService;

    @Mock
    private ScheduleCalculator scheduleCalculator;

    @Mock
    private ScheduleClaimHelper claimHelper;

    private Clock fixedClock;
    private WorkflowSchedulerService schedulerService;

    private Workflow sampleWorkflow;
    private WorkflowSchedule sampleSchedule;
    private Instant fixedNow;

    @BeforeEach
    void setUp() {
        fixedNow = Instant.parse("2026-10-23T12:00:00Z");
        fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);

        schedulerService = new WorkflowSchedulerServiceImpl(
            scheduleRepository,
            workflowRepository,
            executionRepository,
            executionService,
            scheduleCalculator,
            claimHelper,
            fixedClock
        );

        sampleWorkflow = new Workflow("Test WF", "Desc", null, true);
        sampleWorkflow.setId(10L);

        sampleSchedule = new WorkflowSchedule(sampleWorkflow, ScheduleType.DAILY, "UTC");
        sampleSchedule.setId(100L);
        sampleSchedule.setScheduledTime(LocalTime.of(10, 0));
        sampleSchedule.setNextRunAt(fixedNow.minus(10, ChronoUnit.MINUTES));
        sampleSchedule.setEnabled(true);
    }

    @Test
    @DisplayName("Should claim and execute on-time due schedule successfully")
    void shouldClaimAndExecuteDueSchedule() {
        when(scheduleRepository.findByEnabledTrueAndNextRunAtLessThanEqual(fixedNow))
            .thenReturn(List.of(sampleSchedule));

        Instant futureNext = fixedNow.plus(1, ChronoUnit.DAYS);
        when(scheduleCalculator.calculateNextRun(sampleSchedule, fixedNow)).thenReturn(futureNext);
        when(claimHelper.claimSchedule(eq(100L), any(), eq(futureNext), eq(true), eq(fixedNow), eq("RUNNING")))
            .thenReturn(1);
        when(workflowRepository.findById(10L)).thenReturn(Optional.of(sampleWorkflow));
        when(executionRepository.existsByWorkflowIdAndStatus(10L, "RUNNING")).thenReturn(false);

        WorkflowExecutionResponse mockResp = new WorkflowExecutionResponse(
            1L, 10L, "Test WF", "COMPLETED", "SCHEDULED", null, null, null, fixedNow, fixedNow, fixedNow, fixedNow, List.of()
        );
        when(executionService.executeWorkflow(eq(10L), any(WorkflowExecutionRequest.class), eq("SCHEDULED")))
            .thenReturn(mockResp);

        int processed = schedulerService.processDueSchedules(fixedNow);
        assertThat(processed).isEqualTo(1);

        verify(claimHelper).updateStatus(100L, "COMPLETED", fixedNow);
    }

    @Test
    @DisplayName("Should skip dispatch when claim returns 0 (already claimed by another poller)")
    void shouldSkipWhenClaimFails() {
        when(scheduleRepository.findByEnabledTrueAndNextRunAtLessThanEqual(fixedNow))
            .thenReturn(List.of(sampleSchedule));

        Instant futureNext = fixedNow.plus(1, ChronoUnit.DAYS);
        when(scheduleCalculator.calculateNextRun(sampleSchedule, fixedNow)).thenReturn(futureNext);
        when(claimHelper.claimSchedule(eq(100L), any(), eq(futureNext), eq(true), eq(fixedNow), eq("RUNNING")))
            .thenReturn(0); // Lost race

        int processed = schedulerService.processDueSchedules(fixedNow);
        assertThat(processed).isEqualTo(0);

        verifyNoInteractions(workflowRepository);
        verifyNoInteractions(executionService);
    }

    @Test
    @DisplayName("Should record MISSED and not execute when schedule is overdue >= 24 hours")
    void shouldMarkMissedWhenOverdueBeyond24Hours() {
        sampleSchedule.setNextRunAt(fixedNow.minus(25, ChronoUnit.HOURS));

        when(scheduleRepository.findByEnabledTrueAndNextRunAtLessThanEqual(fixedNow))
            .thenReturn(List.of(sampleSchedule));

        Instant futureNext = fixedNow.plus(1, ChronoUnit.DAYS);
        when(scheduleCalculator.calculateNextRun(sampleSchedule, fixedNow)).thenReturn(futureNext);
        when(claimHelper.claimSchedule(eq(100L), any(), eq(futureNext), eq(true), eq(fixedNow), eq("MISSED")))
            .thenReturn(1);

        int processed = schedulerService.processDueSchedules(fixedNow);
        assertThat(processed).isEqualTo(1);

        verify(claimHelper).claimSchedule(eq(100L), any(), eq(futureNext), eq(true), eq(fixedNow), eq("MISSED"));
        verifyNoInteractions(executionService);
    }

    @Test
    @DisplayName("Should record SKIPPED_INACTIVE when workflow is inactive")
    void shouldSkipInactiveWorkflow() {
        sampleWorkflow.setActive(false);

        when(scheduleRepository.findByEnabledTrueAndNextRunAtLessThanEqual(fixedNow))
            .thenReturn(List.of(sampleSchedule));

        Instant futureNext = fixedNow.plus(1, ChronoUnit.DAYS);
        when(scheduleCalculator.calculateNextRun(sampleSchedule, fixedNow)).thenReturn(futureNext);
        when(claimHelper.claimSchedule(eq(100L), any(), eq(futureNext), eq(true), eq(fixedNow), eq("RUNNING")))
            .thenReturn(1);
        when(workflowRepository.findById(10L)).thenReturn(Optional.of(sampleWorkflow));

        int processed = schedulerService.processDueSchedules(fixedNow);
        assertThat(processed).isEqualTo(1);

        verify(claimHelper).updateStatus(100L, "SKIPPED_INACTIVE", fixedNow);
        verifyNoInteractions(executionService);
    }

    @Test
    @DisplayName("Should record SKIPPED_OVERLAP when workflow is already RUNNING")
    void shouldSkipOverlappingExecution() {
        when(scheduleRepository.findByEnabledTrueAndNextRunAtLessThanEqual(fixedNow))
            .thenReturn(List.of(sampleSchedule));

        Instant futureNext = fixedNow.plus(1, ChronoUnit.DAYS);
        when(scheduleCalculator.calculateNextRun(sampleSchedule, fixedNow)).thenReturn(futureNext);
        when(claimHelper.claimSchedule(eq(100L), any(), eq(futureNext), eq(true), eq(fixedNow), eq("RUNNING")))
            .thenReturn(1);
        when(workflowRepository.findById(10L)).thenReturn(Optional.of(sampleWorkflow));
        when(executionRepository.existsByWorkflowIdAndStatus(10L, "RUNNING")).thenReturn(true);

        int processed = schedulerService.processDueSchedules(fixedNow);
        assertThat(processed).isEqualTo(1);

        verify(claimHelper).updateStatus(100L, "SKIPPED_OVERLAP", fixedNow);
        verifyNoInteractions(executionService);
    }

    @Test
    @DisplayName("Should record FAILED status when executionService throws an exception")
    void shouldRecordFailedWhenExecutionThrowsException() {
        when(scheduleRepository.findByEnabledTrueAndNextRunAtLessThanEqual(fixedNow))
            .thenReturn(List.of(sampleSchedule));

        Instant futureNext = fixedNow.plus(1, ChronoUnit.DAYS);
        when(scheduleCalculator.calculateNextRun(sampleSchedule, fixedNow)).thenReturn(futureNext);
        when(claimHelper.claimSchedule(eq(100L), any(), eq(futureNext), eq(true), eq(fixedNow), eq("RUNNING")))
            .thenReturn(1);
        when(workflowRepository.findById(10L)).thenReturn(Optional.of(sampleWorkflow));
        when(executionRepository.existsByWorkflowIdAndStatus(10L, "RUNNING")).thenReturn(false);

        when(executionService.executeWorkflow(eq(10L), any(), eq("SCHEDULED")))
            .thenThrow(new RuntimeException("Simulated engine failure"));

        int processed = schedulerService.processDueSchedules(fixedNow);
        assertThat(processed).isEqualTo(1);

        verify(claimHelper).updateStatus(100L, "FAILED", fixedNow);
    }
}
