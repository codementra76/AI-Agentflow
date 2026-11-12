package com.agentflow.dto.job;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CandidateProfileRequest(
    @NotBlank(message = "Full name is required")
    @Size(max = 150, message = "Full name cannot exceed 150 characters")
    String fullName,

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Size(max = 150, message = "Email cannot exceed 150 characters")
    String email,

    @Size(max = 50, message = "Phone cannot exceed 50 characters")
    String phone,

    @Size(max = 255, message = "Headline cannot exceed 255 characters")
    String headline,

    @Size(max = 150, message = "Location cannot exceed 150 characters")
    String location,

    @Size(max = 500, message = "Portfolio URL cannot exceed 500 characters")
    String portfolioUrl,

    @Size(max = 500, message = "LinkedIn URL cannot exceed 500 characters")
    String linkedinUrl,

    @Size(max = 500, message = "GitHub URL cannot exceed 500 characters")
    String githubUrl,

    @Size(max = 5000, message = "Summary cannot exceed 5000 characters")
    String summary
) {}
