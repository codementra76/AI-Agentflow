package com.agentflow.dto.job;

import com.agentflow.entity.CandidateResume;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record CandidateResumeResponse(
    Long id,
    Long profileId,
    String versionName,
    String rawText,
    boolean isPrimary,
    JsonNode parsedSkills,
    Instant createdAt,
    Instant updatedAt
) {
    public static CandidateResumeResponse fromEntity(CandidateResume entity) {
        if (entity == null) {
            return null;
        }
        return new CandidateResumeResponse(
            entity.getId(),
            entity.getProfile() != null ? entity.getProfile().getId() : null,
            entity.getVersionName(),
            entity.getRawText(),
            entity.isPrimary(),
            entity.getParsedSkills(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
