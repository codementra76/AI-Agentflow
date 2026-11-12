package com.agentflow.repository;

import com.agentflow.entity.JobApplication;
import com.agentflow.entity.JobApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    /**
     * Find the tracking application record associated with a job listing.
     */
    Optional<JobApplication> findByJobId(Long jobId);

    /**
     * Check whether an active application tracking record exists for a job listing.
     */
    boolean existsByJobId(Long jobId);

    /**
     * Filter applications by status with pagination support.
     */
    Page<JobApplication> findByStatus(JobApplicationStatus status, Pageable pageable);

    /**
     * Filter applications by status.
     */
    List<JobApplication> findByStatus(JobApplicationStatus status);

    /**
     * Filter applications by status ordered by applied date descending.
     */
    List<JobApplication> findByStatusOrderByAppliedDateDesc(JobApplicationStatus status);

    /**
     * Filter applications by status ordered by creation date descending.
     */
    List<JobApplication> findByStatusOrderByCreatedAtDesc(JobApplicationStatus status);

    /**
     * Count total applications by tracking status (e.g. SAVED, APPLIED, INTERVIEWING).
     */
    long countByStatus(JobApplicationStatus status);

    /**
     * Find applications with upcoming or overdue follow-up dates matching active statuses.
     */
    List<JobApplication> findByFollowUpDateLessThanEqualAndStatusIn(
            LocalDate date, Collection<JobApplicationStatus> statuses);

    /**
     * Search and filter applications with optional status filter and pagination.
     * When status is null, all applications are returned according to the pageable.
     */
    @Query("SELECT a FROM JobApplication a WHERE " +
           "(:status IS NULL OR a.status = :status)")
    Page<JobApplication> findByOptionalStatus(
            @Param("status") JobApplicationStatus status,
            Pageable pageable
    );
}
