package com.agentflow.service;

import java.time.Instant;

public interface WorkflowSchedulerService {

    /**
     * Periodic poller entry point triggered by Spring's scheduled runner.
     */
    void pollAndExecuteDueSchedules();

    /**
     * Deterministically processes due schedules relative to an explicit reference instant.
     *
     * @param referenceInstant the reference time to evaluate schedules against (usually current time)
     * @return count of schedules claimed and processed
     */
    int processDueSchedules(Instant referenceInstant);
}
