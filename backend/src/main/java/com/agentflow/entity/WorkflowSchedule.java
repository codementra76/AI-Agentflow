package com.agentflow.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "workflow_schedules")
public class WorkflowSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Workflow workflow;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false, length = 20)
    private ScheduleType scheduleType;

    @Column(name = "scheduled_time")
    private LocalTime scheduledTime;

    @Column(name = "day_of_week", length = 100)
    private String dayOfWeek;

    @Column(name = "one_time_execution_at")
    private Instant oneTimeExecutionAt;

    @Column(name = "time_zone", nullable = false, length = 50)
    private String timeZone;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_data", columnDefinition = "json")
    private JsonNode inputData;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "last_status", length = 50)
    private String lastStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public WorkflowSchedule() {
        this.enabled = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public WorkflowSchedule(Workflow workflow, ScheduleType scheduleType, String timeZone) {
        this.workflow = workflow;
        this.scheduleType = scheduleType;
        this.timeZone = timeZone;
        this.enabled = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Set<DayOfWeek> getDaysOfWeekSet() {
        if (dayOfWeek == null || dayOfWeek.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(dayOfWeek.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(s -> DayOfWeek.valueOf(s.toUpperCase()))
            .collect(Collectors.toCollection(TreeSet::new));
    }

    public void setDaysOfWeekSet(Collection<DayOfWeek> days) {
        if (days == null || days.isEmpty()) {
            this.dayOfWeek = null;
        } else {
            this.dayOfWeek = days.stream()
                .distinct()
                .sorted()
                .map(Enum::name)
                .collect(Collectors.joining(","));
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Workflow getWorkflow() {
        return workflow;
    }

    public void setWorkflow(Workflow workflow) {
        this.workflow = workflow;
    }

    public ScheduleType getScheduleType() {
        return scheduleType;
    }

    public void setScheduleType(ScheduleType scheduleType) {
        this.scheduleType = scheduleType;
    }

    public LocalTime getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(LocalTime scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(String dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public Instant getOneTimeExecutionAt() {
        return oneTimeExecutionAt;
    }

    public void setOneTimeExecutionAt(Instant oneTimeExecutionAt) {
        this.oneTimeExecutionAt = oneTimeExecutionAt;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public JsonNode getInputData() {
        return inputData;
    }

    public void setInputData(JsonNode inputData) {
        this.inputData = inputData;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(Instant nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(Instant lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(String lastStatus) {
        this.lastStatus = lastStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
