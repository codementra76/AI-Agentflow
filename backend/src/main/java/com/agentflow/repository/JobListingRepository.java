package com.agentflow.repository;

import com.agentflow.entity.EmploymentType;
import com.agentflow.entity.JobListing;
import com.agentflow.entity.WorkplaceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobListingRepository extends JpaRepository<JobListing, Long> {

    /**
     * Find a job listing by its deterministic 64-character SHA-256 content hash.
     */
    Optional<JobListing> findByContentHash(String contentHash);

    /**
     * Check if a job listing with the specified content hash already exists.
     */
    boolean existsByContentHash(String contentHash);

    /**
     * Find a job listing by its canonical source URL.
     */
    Optional<JobListing> findBySourceUrl(String sourceUrl);

    /**
     * Check if a job listing already exists with the given source URL.
     */
    boolean existsBySourceUrl(String sourceUrl);

    /**
     * Find job listings matching title or company keywords (case-insensitive) with pagination.
     */
    Page<JobListing> findByTitleContainingIgnoreCaseOrCompanyContainingIgnoreCase(
            String titleKeyword, String companyKeyword, Pageable pageable);

    /**
     * Find job listings matching title or company keywords (case-insensitive).
     */
    List<JobListing> findByTitleContainingIgnoreCaseOrCompanyContainingIgnoreCase(
            String titleKeyword, String companyKeyword);

    /**
     * Filter job listings by workplace type (REMOTE, HYBRID, ON_SITE) with pagination.
     */
    Page<JobListing> findByWorkplaceType(WorkplaceType workplaceType, Pageable pageable);

    /**
     * Filter job listings by employment type (FULL_TIME, CONTRACT, PART_TIME, INTERNSHIP) with pagination.
     */
    Page<JobListing> findByEmploymentType(EmploymentType employmentType, Pageable pageable);

    /**
     * Unified, non-fragile query for searching and filtering job listings with optional parameters.
     * When a filter parameter is null, that filter condition is automatically bypassed.
     */
    @Query("SELECT j FROM JobListing j WHERE " +
           "(:search IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(j.company) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:workplaceType IS NULL OR j.workplaceType = :workplaceType) AND " +
           "(:employmentType IS NULL OR j.employmentType = :employmentType)")
    Page<JobListing> searchJobListings(
            @Param("search") String search,
            @Param("workplaceType") WorkplaceType workplaceType,
            @Param("employmentType") EmploymentType employmentType,
            Pageable pageable
    );
}
