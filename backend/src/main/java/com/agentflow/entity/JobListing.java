package com.agentflow.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

@Entity
@Table(name = "job_listings")
public class JobListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Job title is required")
    @Size(max = 255, message = "Job title must not exceed 255 characters")
    @Column(nullable = false, length = 255)
    private String title;

    @NotBlank(message = "Company name is required")
    @Size(max = 255, message = "Company name must not exceed 255 characters")
    @Column(nullable = false, length = 255)
    private String company;

    @Size(max = 255, message = "Location must not exceed 255 characters")
    @Column(length = 255)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "workplace_type", length = 50)
    private WorkplaceType workplaceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", length = 50)
    private EmploymentType employmentType;

    @Size(max = 100, message = "Salary range must not exceed 100 characters")
    @Column(name = "salary_range", length = 100)
    private String salaryRange;

    @Size(max = 50, message = "Source must not exceed 50 characters")
    @Column(nullable = false, length = 50)
    private String source = "MANUAL";

    @Size(max = 500, message = "Source URL must not exceed 500 characters")
    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @NotBlank(message = "Content hash is required")
    @Size(max = 64, message = "Content hash must be a 64-character SHA-256 hex string")
    @Column(name = "content_hash", nullable = false, unique = true, length = 64)
    private String contentHash;

    @Column(name = "raw_description", columnDefinition = "LONGTEXT")
    private String rawDescription;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_requirements", columnDefinition = "json")
    private JsonNode parsedRequirements;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public JobListing() {
        this.source = "MANUAL";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public JobListing(String title, String company, String location, WorkplaceType workplaceType,
                      EmploymentType employmentType, String rawDescription) {
        this.title = title;
        this.company = company;
        this.location = location;
        this.workplaceType = workplaceType;
        this.employmentType = employmentType;
        this.rawDescription = rawDescription;
        this.source = "MANUAL";
        this.contentHash = calculateContentHash(company, title, location);
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (contentHash == null || contentHash.isBlank()) {
            contentHash = calculateContentHash(company, title, location);
        }
        if (source == null || source.isBlank()) {
            source = "MANUAL";
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

    /**
     * Deterministically generates a 64-character SHA-256 content hash from normalized company,
     * title, and location values. Internal and surrounding whitespace is collapsed and casing is lowercased.
     */
    public static String calculateContentHash(String company, String title, String location) {
        String normCompany = normalize(company);
        String normTitle = normalize(title);
        String normLocation = normalize(location);
        String input = normCompany + "|" + normTitle + "|" + normLocation;
        return sha256Hex(input);
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in JVM", e);
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public WorkplaceType getWorkplaceType() {
        return workplaceType;
    }

    public void setWorkplaceType(WorkplaceType workplaceType) {
        this.workplaceType = workplaceType;
    }

    public EmploymentType getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(EmploymentType employmentType) {
        this.employmentType = employmentType;
    }

    public String getSalaryRange() {
        return salaryRange;
    }

    public void setSalaryRange(String salaryRange) {
        this.salaryRange = salaryRange;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getRawDescription() {
        return rawDescription;
    }

    public void setRawDescription(String rawDescription) {
        this.rawDescription = rawDescription;
    }

    public JsonNode getParsedRequirements() {
        return parsedRequirements;
    }

    public void setParsedRequirements(JsonNode parsedRequirements) {
        this.parsedRequirements = parsedRequirements;
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
