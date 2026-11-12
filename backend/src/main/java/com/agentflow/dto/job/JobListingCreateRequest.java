package com.agentflow.dto.job;

import com.agentflow.entity.EmploymentType;
import com.agentflow.entity.JobApplicationStatus;
import com.agentflow.entity.WorkplaceType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JobListingCreateRequest(
    @NotBlank(message = "Job title is required")
    @Size(max = 255, message = "Job title cannot exceed 255 characters")
    String title,

    @NotBlank(message = "Company name is required")
    @Size(max = 255, message = "Company name cannot exceed 255 characters")
    String company,

    @Size(max = 255, message = "Location cannot exceed 255 characters")
    String location,

    WorkplaceType workplaceType,

    EmploymentType employmentType,

    @Size(max = 100, message = "Salary range cannot exceed 100 characters")
    String salaryRange,

    @Size(max = 50, message = "Source cannot exceed 50 characters")
    String source,

    @Size(max = 500, message = "Source URL cannot exceed 500 characters")
    String sourceUrl,

    String rawDescription,

    JsonNode parsedRequirements,

    JobApplicationStatus initialApplicationStatus
) {}
