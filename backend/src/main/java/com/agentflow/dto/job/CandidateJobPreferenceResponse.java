package com.agentflow.dto.job;

import com.agentflow.entity.CandidateJobPreference;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

public record CandidateJobPreferenceResponse(
    Long id,
    Long profileId,
    JsonNode targetTitles,
    JsonNode preferredLocations,
    JsonNode workplaceTypes,
    JsonNode employmentTypes,
    BigDecimal minSalary,
    String currency,
    Instant createdAt,
    Instant updatedAt
) {
    public static CandidateJobPreferenceResponse fromEntity(CandidateJobPreference entity) {
        if (entity == null) {
            return null;
        }
        return new CandidateJobPreferenceResponse(
            entity.getId(),
            entity.getProfile() != null ? entity.getProfile().getId() : null,
            entity.getTargetTitles(),
            entity.getPreferredLocations(),
            entity.getWorkplaceTypes(),
            entity.getEmploymentTypes(),
            entity.getMinSalary(),
            entity.getCurrency(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
