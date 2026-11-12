package com.agentflow.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "candidate_job_preferences")
public class CandidateJobPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Candidate profile is required")
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CandidateProfile profile;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_titles", columnDefinition = "json")
    private JsonNode targetTitles;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_locations", columnDefinition = "json")
    private JsonNode preferredLocations;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "workplace_types", columnDefinition = "json")
    private JsonNode workplaceTypes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "employment_types", columnDefinition = "json")
    private JsonNode employmentTypes;

    @Column(name = "min_salary", precision = 12, scale = 2)
    private BigDecimal minSalary;

    @Size(max = 10, message = "Currency code must not exceed 10 characters")
    @Column(nullable = false, length = 10)
    private String currency = "USD";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CandidateJobPreference() {
        this.currency = "USD";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public CandidateJobPreference(CandidateProfile profile) {
        this.profile = profile;
        this.currency = "USD";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (currency == null || currency.isBlank()) {
            currency = "USD";
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

    public CandidateProfile getProfile() {
        return profile;
    }

    public void setProfile(CandidateProfile profile) {
        this.profile = profile;
    }

    public JsonNode getTargetTitles() {
        return targetTitles;
    }

    public void setTargetTitles(JsonNode targetTitles) {
        this.targetTitles = targetTitles;
    }

    public JsonNode getPreferredLocations() {
        return preferredLocations;
    }

    public void setPreferredLocations(JsonNode preferredLocations) {
        this.preferredLocations = preferredLocations;
    }

    public JsonNode getWorkplaceTypes() {
        return workplaceTypes;
    }

    public void setWorkplaceTypes(JsonNode workplaceTypes) {
        this.workplaceTypes = workplaceTypes;
    }

    public JsonNode getEmploymentTypes() {
        return employmentTypes;
    }

    public void setEmploymentTypes(JsonNode employmentTypes) {
        this.employmentTypes = employmentTypes;
    }

    public BigDecimal getMinSalary() {
        return minSalary;
    }

    public void setMinSalary(BigDecimal minSalary) {
        this.minSalary = minSalary;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
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
