package com.agentflow.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "candidate_resumes")
public class CandidateResume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Candidate profile is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CandidateProfile profile;

    @NotBlank(message = "Version name is required")
    @Size(max = 100, message = "Version name must not exceed 100 characters")
    @Column(name = "version_name", nullable = false, length = 100)
    private String versionName;

    @NotBlank(message = "Resume raw text is required")
    @Column(name = "raw_text", nullable = false, columnDefinition = "LONGTEXT")
    private String rawText;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_skills", columnDefinition = "json")
    private JsonNode parsedSkills;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CandidateResume() {
        this.isPrimary = false;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public CandidateResume(CandidateProfile profile, String versionName, String rawText, boolean isPrimary) {
        this.profile = profile;
        this.versionName = versionName;
        this.rawText = rawText;
        this.isPrimary = isPrimary;
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CandidateProfile getProfile() {
        return profile;
    }

    public void setProfile(CandidateProfile profile) {
        this.profile = profile;
    }

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public boolean isPrimary() {
        return isPrimary;
    }

    public void setPrimary(boolean primary) {
        isPrimary = primary;
    }

    public JsonNode getParsedSkills() {
        return parsedSkills;
    }

    public void setParsedSkills(JsonNode parsedSkills) {
        this.parsedSkills = parsedSkills;
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
