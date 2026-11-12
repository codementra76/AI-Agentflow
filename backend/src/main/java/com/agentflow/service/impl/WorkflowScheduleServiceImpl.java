package com.agentflow.service.impl;

import com.agentflow.dto.ScheduleStatusRequest;
import com.agentflow.dto.WorkflowScheduleRequest;
import com.agentflow.dto.WorkflowScheduleResponse;
import com.agentflow.entity.ScheduleType;
import com.agentflow.entity.Workflow;
import com.agentflow.entity.WorkflowSchedule;
import com.agentflow.exception.ResourceNotFoundException;
import com.agentflow.exception.WorkflowValidationException;
import com.agentflow.repository.WorkflowRepository;
import com.agentflow.repository.WorkflowScheduleRepository;
import com.agentflow.service.WorkflowScheduleService;
import com.agentflow.workflow.schedule.ScheduleCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class WorkflowScheduleServiceImpl implements WorkflowScheduleService {

    private final WorkflowScheduleRepository scheduleRepository;
    private final WorkflowRepository workflowRepository;
    private final ScheduleCalculator scheduleCalculator;
    private final Clock clock;

    public WorkflowScheduleServiceImpl(
            WorkflowScheduleRepository scheduleRepository,
            WorkflowRepository workflowRepository,
            ScheduleCalculator scheduleCalculator,
            Clock clock) {
        this.scheduleRepository = scheduleRepository;
        this.workflowRepository = workflowRepository;
        this.scheduleCalculator = scheduleCalculator;
        this.clock = clock;
    }

    @Override
    public WorkflowScheduleResponse getScheduleByWorkflowId(Long workflowId) {
        findWorkflowOrThrow(workflowId);
        WorkflowSchedule schedule = scheduleRepository.findByWorkflowId(workflowId)
            .orElseThrow(() -> new ResourceNotFoundException("Schedule not found for workflow id: " + workflowId));
        return WorkflowScheduleResponse.fromEntity(schedule);
    }

    @Override
    @Transactional
    public WorkflowScheduleResponse saveOrUpdateSchedule(Long workflowId, WorkflowScheduleRequest request) {
        Workflow workflow = findWorkflowOrThrow(workflowId);

        if (request.scheduleType() == null) {
            throw new WorkflowValidationException("Schedule type is required");
        }
        scheduleCalculator.validateAndParseTimeZone(request.timeZone());

        Set<DayOfWeek> daysOfWeek = request.resolveDaysOfWeek();
        boolean enabled = request.enabled() == null || request.enabled();

        if (request.scheduleType() == ScheduleType.ONE_TIME) {
            if (request.oneTimeExecutionAt() == null) {
                throw new WorkflowValidationException("Execution time is required for ONE_TIME schedule");
            }
            if (enabled && !request.oneTimeExecutionAt().isAfter(clock.instant())) {
                throw new WorkflowValidationException(
                    "One-time execution time must be in the future (scheduled: " + request.oneTimeExecutionAt()
                        + ", current: " + clock.instant() + ")"
                );
            }
        } else if (request.scheduleType() == ScheduleType.DAILY) {
            if (request.scheduledTime() == null) {
                throw new WorkflowValidationException("Scheduled time is required for DAILY schedule");
            }
        } else if (request.scheduleType() == ScheduleType.WEEKLY) {
            if (request.scheduledTime() == null) {
                throw new WorkflowValidationException("Scheduled time is required for WEEKLY schedule");
            }
            if (daysOfWeek.isEmpty()) {
                throw new WorkflowValidationException("At least one day of the week is required for WEEKLY schedule");
            }
        }

        Instant nextRunAt = null;
        if (enabled) {
            nextRunAt = scheduleCalculator.calculateNextRun(
                request.scheduleType(),
                request.scheduledTime(),
                daysOfWeek,
                request.oneTimeExecutionAt(),
                request.timeZone(),
                clock.instant()
            );
        }

        WorkflowSchedule schedule = scheduleRepository.findByWorkflowId(workflowId)
            .orElseGet(() -> new WorkflowSchedule(workflow, request.scheduleType(), request.timeZone().trim()));

        schedule.setWorkflow(workflow);
        schedule.setScheduleType(request.scheduleType());
        schedule.setTimeZone(request.timeZone().trim());
        schedule.setScheduledTime(request.scheduledTime());
        schedule.setDaysOfWeekSet(daysOfWeek);
        schedule.setOneTimeExecutionAt(request.oneTimeExecutionAt());
        schedule.setInputData(request.inputData());
        schedule.setEnabled(enabled);
        schedule.setNextRunAt(nextRunAt);

        WorkflowSchedule saved = scheduleRepository.save(schedule);
        return WorkflowScheduleResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public WorkflowScheduleResponse updateScheduleStatus(Long workflowId, ScheduleStatusRequest request) {
        findWorkflowOrThrow(workflowId);
        WorkflowSchedule schedule = scheduleRepository.findByWorkflowId(workflowId)
            .orElseThrow(() -> new ResourceNotFoundException("Schedule not found for workflow id: " + workflowId));

        if (request.enabled() == null) {
            throw new WorkflowValidationException("The 'enabled' field is required");
        }

        boolean targetEnabled = request.enabled();
        if (targetEnabled == schedule.isEnabled()) {
            return WorkflowScheduleResponse.fromEntity(schedule);
        }

        if (!targetEnabled) {
            schedule.setEnabled(false);
            schedule.setNextRunAt(null);
        } else {
            if (schedule.getScheduleType() == ScheduleType.ONE_TIME) {
                if (schedule.getOneTimeExecutionAt() == null || !schedule.getOneTimeExecutionAt().isAfter(clock.instant())) {
                    throw new WorkflowValidationException(
                        "Cannot enable one-time schedule because scheduled execution time (" + schedule.getOneTimeExecutionAt()
                            + ") has already passed. Please update the scheduled time to a future date/time."
                    );
                }
                schedule.setNextRunAt(schedule.getOneTimeExecutionAt());
            } else {
                Instant next = scheduleCalculator.calculateNextRun(
                    schedule.getScheduleType(),
                    schedule.getScheduledTime(),
                    schedule.getDaysOfWeekSet(),
                    schedule.getOneTimeExecutionAt(),
                    schedule.getTimeZone(),
                    clock.instant()
                );
                schedule.setNextRunAt(next);
            }
            schedule.setEnabled(true);
        }

        WorkflowSchedule saved = scheduleRepository.save(schedule);
        return WorkflowScheduleResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public void deleteSchedule(Long workflowId) {
        findWorkflowOrThrow(workflowId);
        WorkflowSchedule schedule = scheduleRepository.findByWorkflowId(workflowId)
            .orElseThrow(() -> new ResourceNotFoundException("Schedule not found for workflow id: " + workflowId));
        scheduleRepository.delete(schedule);
    }

    private Workflow findWorkflowOrThrow(Long id) {
        return workflowRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Workflow not found with id: " + id));
    }
}
