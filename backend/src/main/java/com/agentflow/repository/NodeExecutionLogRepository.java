package com.agentflow.repository;

import com.agentflow.entity.NodeExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NodeExecutionLogRepository extends JpaRepository<NodeExecutionLog, Long> {
    List<NodeExecutionLog> findByWorkflowExecutionId(Long executionId);
    List<NodeExecutionLog> findByWorkflowExecutionIdOrderByIdAsc(Long executionId);
    List<NodeExecutionLog> findByStatus(String status);
}
