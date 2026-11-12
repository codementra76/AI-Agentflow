package com.agentflow.dto.job;

import com.agentflow.entity.EmploymentType;
import com.agentflow.entity.JobListing;
import com.agentflow.entity.WorkplaceType;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record JobListingResponse(
    Long id,
    String title,
    String company,
    String location,
    WorkplaceType workplaceType,
    EmploymentType employmentType,
    String salaryRange,
    String source,
    String sourceUrl,
    String contentHash,
    String rawDescription,
    JsonNode parsedRequirements,
    Instant createdAt,
    Instant updatedAt
) {
    public static JobListingResponse fromEntity(JobListing entity) {
        if (entity == null) {
            return null;
        }
        return new JobListingResponse(
            entity.getId(),
            entity.getTitle(),
            entity.getCompany(),
            entity.getLocation(),
            entity.getWorkplaceType(),
            entity.getEmploymentType(),
            entity.getSalaryRange(),
            entity.getSource(),
            entity.getSourceUrl(),
            entity.getContentHash(),
            entity.getRawDescription(),
            entity.getParsedRequirements(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
