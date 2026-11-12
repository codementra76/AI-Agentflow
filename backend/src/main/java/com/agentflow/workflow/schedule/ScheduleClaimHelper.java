package com.agentflow.workflow.schedule;

import com.agentflow.repository.WorkflowScheduleRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Dedicated helper executing atomic schedule claiming and status recording
 * in independent, short-lived transactions (Propagation.REQUIRES_NEW) so that
 * long-running workflow executions do not hold open database transactions.
 */
@Component
public class ScheduleClaimHelper {

    private final WorkflowScheduleRepository scheduleRepository;

    public ScheduleClaimHelper(WorkflowScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    /**
     * Atomically claims a schedule by updating its next_run_at and status in its own transaction.
     * Returns 1 if successfully claimed, 0 if already claimed or modified.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int claimSchedule(
            Long id,
            Instant expectedNextRunAt,
            Instant newNextRunAt,
            boolean enabled,
            Instant claimTime,
            String status) {
        return scheduleRepository.claimSchedule(id, expectedNextRunAt, newNextRunAt, enabled, claimTime, status);
    }

    /**
     * Updates schedule outcome status in its own transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatus(Long id, String status, Instant updateTime) {
        scheduleRepository.updateScheduleStatus(id, status, updateTime);
    }
}
