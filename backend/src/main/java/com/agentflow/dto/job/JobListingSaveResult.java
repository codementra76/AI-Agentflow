package com.agentflow.dto.job;

public record JobListingSaveResult(
    JobListingResponse listing,
    JobApplicationResponse application,
    boolean isDuplicate,
    String message
) {
    public static JobListingSaveResult created(JobListingResponse listing, JobApplicationResponse application) {
        return new JobListingSaveResult(listing, application, false, "Job listing created successfully");
    }

    public static JobListingSaveResult duplicate(JobListingResponse listing, JobApplicationResponse application) {
        return new JobListingSaveResult(
            listing,
            application,
            true,
            "Job listing already exists. Existing application status and notes have been preserved."
        );
    }
}
