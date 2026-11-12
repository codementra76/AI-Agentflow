package com.agentflow.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "job_applications")
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Job listing is required")
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private JobListing job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private CandidateResume resume;

    @NotNull(message = "Application status is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private JobApplicationStatus status = JobApplicationStatus.SAVED;

    @Column(name = "applied_date")
    private LocalDate appliedDate;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "match_score", precision = 5, scale = 2)
    private BigDecimal matchScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "match_breakdown", columnDefinition = "json")
    private JsonNode matchBreakdown;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public JobApplication() {
        this.status = JobApplicationStatus.SAVED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public JobApplication(JobListing job) {
        this.job = job;
        this.status = JobApplicationStatus.SAVED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public JobApplication(JobListing job, JobApplicationStatus status) {
        this.job = job;
        this.status = status != null ? status : JobApplicationStatus.SAVED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = JobApplicationStatus.SAVED;
        }
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public JobListing getJob() {
        return job;
    }

    public void setJob(JobListing job) {
        this.job = job;
    }

    public CandidateResume getResume() {
        return resume;
    }

    public void setResume(CandidateResume resume) {
        this.resume = resume;
    }

    public JobApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(JobApplicationStatus status) {
        this.status = status;
    }

    public LocalDate getAppliedDate() {
        return appliedDate;
    }

    public void setAppliedDate(LocalDate appliedDate) {
        this.appliedDate = appliedDate;
    }

    public LocalDate getFollowUpDate() {
        return followUpDate;
    }

    public void setFollowUpDate(LocalDate followUpDate) {
        this.followUpDate = followUpDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public BigDecimal getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(BigDecimal matchScore) {
        this.matchScore = matchScore;
    }

    public JsonNode getMatchBreakdown() {
        return matchBreakdown;
    }

    public void setMatchBreakdown(JsonNode matchBreakdown) {
        this.matchBreakdown = matchBreakdown;
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
