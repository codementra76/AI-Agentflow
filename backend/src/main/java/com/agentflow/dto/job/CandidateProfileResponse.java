package com.agentflow.dto.job;

import com.agentflow.entity.CandidateProfile;

import java.time.Instant;

public record CandidateProfileResponse(
    Long id,
    String fullName,
    String email,
    String phone,
    String headline,
    String location,
    String portfolioUrl,
    String linkedinUrl,
    String githubUrl,
    String summary,
    Instant createdAt,
    Instant updatedAt
) {
    public static CandidateProfileResponse fromEntity(CandidateProfile entity) {
        if (entity == null) {
            return null;
        }
        return new CandidateProfileResponse(
            entity.getId(),
            entity.getFullName(),
            entity.getEmail(),
            entity.getPhone(),
            entity.getHeadline(),
            entity.getLocation(),
            entity.getPortfolioUrl(),
            entity.getLinkedinUrl(),
            entity.getGithubUrl(),
            entity.getSummary(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
