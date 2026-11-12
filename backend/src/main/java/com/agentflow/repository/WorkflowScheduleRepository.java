package com.agentflow.repository;

import com.agentflow.entity.WorkflowSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowScheduleRepository extends JpaRepository<WorkflowSchedule, Long> {

    Optional<WorkflowSchedule> findByWorkflowId(Long workflowId);

    boolean existsByWorkflowId(Long workflowId);

    void deleteByWorkflowId(Long workflowId);

    List<WorkflowSchedule> findByEnabledTrueAndNextRunAtLessThanEqual(Instant dueTime);

    List<WorkflowSchedule> findByEnabled(boolean enabled);

    @Modifying
    @Query("UPDATE WorkflowSchedule s SET s.nextRunAt = :newNextRunAt, s.enabled = :enabled, " +
           "s.lastRunAt = :claimTime, s.lastStatus = :status, s.updatedAt = :claimTime " +
           "WHERE s.id = :id AND s.enabled = true AND s.nextRunAt = :expectedNextRunAt")
    int claimSchedule(
        @Param("id") Long id,
        @Param("expectedNextRunAt") Instant expectedNextRunAt,
        @Param("newNextRunAt") Instant newNextRunAt,
        @Param("enabled") boolean enabled,
        @Param("claimTime") Instant claimTime,
        @Param("status") String status
    );

    @Modifying
    @Query("UPDATE WorkflowSchedule s SET s.lastStatus = :status, s.updatedAt = :updateTime " +
           "WHERE s.id = :id")
    int updateScheduleStatus(
        @Param("id") Long id,
        @Param("status") String status,
        @Param("updateTime") Instant updateTime
    );
}
