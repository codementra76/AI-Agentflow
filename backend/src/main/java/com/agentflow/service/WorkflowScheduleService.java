package com.agentflow.service;

import com.agentflow.dto.ScheduleStatusRequest;
import com.agentflow.dto.WorkflowScheduleRequest;
import com.agentflow.dto.WorkflowScheduleResponse;

public interface WorkflowScheduleService {

    WorkflowScheduleResponse getScheduleByWorkflowId(Long workflowId);

    WorkflowScheduleResponse saveOrUpdateSchedule(Long workflowId, WorkflowScheduleRequest request);

    WorkflowScheduleResponse updateScheduleStatus(Long workflowId, ScheduleStatusRequest request);

    void deleteSchedule(Long workflowId);
}
