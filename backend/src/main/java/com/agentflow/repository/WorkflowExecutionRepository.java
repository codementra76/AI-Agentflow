package com.agentflow.repository;

import com.agentflow.entity.WorkflowExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, Long> {
    List<WorkflowExecution> findByWorkflowId(Long workflowId);
    List<WorkflowExecution> findByWorkflowIdOrderByStartedAtDesc(Long workflowId);
    List<WorkflowExecution> findAllByOrderByStartedAtDesc();
    List<WorkflowExecution> findByStatus(String status);
    List<WorkflowExecution> findByStatusOrderByStartedAtDesc(String status);
    List<WorkflowExecution> findByWorkflowIdAndStatusOrderByStartedAtDesc(Long workflowId, String status);
}
