package com.agentflow.dto.job;

import com.agentflow.entity.JobApplication;
import com.agentflow.entity.JobApplicationStatus;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record JobApplicationResponse(
    Long id,
    Long jobId,
    String jobTitle,
    String jobCompany,
    Long resumeId,
    String resumeVersionName,
    JobApplicationStatus status,
    LocalDate appliedDate,
    LocalDate followUpDate,
    String notes,
    BigDecimal matchScore,
    JsonNode matchBreakdown,
    Instant createdAt,
    Instant updatedAt
) {
    public static JobApplicationResponse fromEntity(JobApplication entity) {
        if (entity == null) {
            return null;
        }
        return new JobApplicationResponse(
            entity.getId(),
            entity.getJob() != null ? entity.getJob().getId() : null,
            entity.getJob() != null ? entity.getJob().getTitle() : null,
            entity.getJob() != null ? entity.getJob().getCompany() : null,
            entity.getResume() != null ? entity.getResume().getId() : null,
            entity.getResume() != null ? entity.getResume().getVersionName() : null,
            entity.getStatus(),
            entity.getAppliedDate(),
            entity.getFollowUpDate(),
            entity.getNotes(),
            entity.getMatchScore(),
            entity.getMatchBreakdown(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
