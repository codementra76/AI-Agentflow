package com.agentflow.service.impl;

import com.agentflow.dto.job.*;
import com.agentflow.entity.EmploymentType;
import com.agentflow.entity.JobApplication;
import com.agentflow.entity.JobApplicationStatus;
import com.agentflow.entity.JobListing;
import com.agentflow.entity.WorkplaceType;
import com.agentflow.exception.AgentFlowException;
import com.agentflow.exception.ConflictException;
import com.agentflow.exception.ResourceNotFoundException;
import com.agentflow.repository.JobApplicationRepository;
import com.agentflow.repository.JobListingRepository;
import com.agentflow.service.JobListingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class JobListingServiceImpl implements JobListingService {

    private static final Logger log = LoggerFactory.getLogger(JobListingServiceImpl.class);

    private final JobListingRepository listingRepository;
    private final JobApplicationRepository applicationRepository;

    public JobListingServiceImpl(JobListingRepository listingRepository,
                                 JobApplicationRepository applicationRepository) {
        this.listingRepository = listingRepository;
        this.applicationRepository = applicationRepository;
    }

    @Override
    @Transactional
    public JobListingSaveResult createJobListing(JobListingCreateRequest request) {
        if (request == null) {
            throw new AgentFlowException("Job listing request cannot be null");
        }

        validateCreateRequest(request);

        String trimmedCompany = request.company().trim();
        String trimmedTitle = request.title().trim();
        String trimmedLocation = request.location() != null ? request.location().trim() : null;
        String trimmedSourceUrl = (request.sourceUrl() != null && !request.sourceUrl().isBlank())
            ? request.sourceUrl().trim()
            : null;

        String contentHash = JobListing.calculateContentHash(trimmedCompany, trimmedTitle, trimmedLocation);

        // 1. Check duplicate by content hash (normalized company, title, location)
        Optional<JobListing> existingByHash = listingRepository.findByContentHash(contentHash);
        if (existingByHash.isPresent()) {
            return buildDuplicateResult(existingByHash.get());
        }

        // 2. Check duplicate by canonical source URL if provided
        if (trimmedSourceUrl != null) {
            Optional<JobListing> existingByUrl = listingRepository.findBySourceUrl(trimmedSourceUrl);
            if (existingByUrl.isPresent()) {
                return buildDuplicateResult(existingByUrl.get());
            }
        }

        // 3. Create new JobListing
        JobListing listing = new JobListing();
        listing.setTitle(trimmedTitle);
        listing.setCompany(trimmedCompany);
        listing.setLocation(trimmedLocation);
        listing.setWorkplaceType(request.workplaceType());
        listing.setEmploymentType(request.employmentType());
        listing.setSalaryRange(trimToNull(request.salaryRange()));
        listing.setSource(request.source() != null && !request.source().isBlank() ? request.source().trim() : "MANUAL");
        listing.setSourceUrl(trimmedSourceUrl);
        listing.setContentHash(contentHash);
        listing.setRawDescription(trimToNull(request.rawDescription()));
        listing.setParsedRequirements(request.parsedRequirements());

        JobListing savedListing;
        try {
            savedListing = listingRepository.saveAndFlush(listing);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent insert race condition fallback
            log.warn("Database unique constraint triggered for contentHash [{}]. Recovering as duplicate.", contentHash);
            Optional<JobListing> concurrentListing = listingRepository.findByContentHash(contentHash);
            if (concurrentListing.isPresent()) {
                return buildDuplicateResult(concurrentListing.get());
            }
            throw ex;
        }

        // 4. Initialize associated JobApplication record with SAVED (or requested initial) status
        JobApplicationStatus initialStatus = request.initialApplicationStatus() != null
            ? request.initialApplicationStatus()
            : JobApplicationStatus.SAVED;

        JobApplication application = new JobApplication(savedListing, initialStatus);
        JobApplication savedApplication = applicationRepository.save(application);

        log.info("Created job listing [id: {}, title: '{}', company: '{}'] with application [id: {}, status: {}]",
            savedListing.getId(), trimmedTitle, trimmedCompany, savedApplication.getId(), initialStatus);

        return JobListingSaveResult.created(
            JobListingResponse.fromEntity(savedListing),
            JobApplicationResponse.fromEntity(savedApplication)
        );
    }

    @Override
    public JobListingResponse getJobListingById(Long id) {
        JobListing listing = findListingOrThrow(id);
        return JobListingResponse.fromEntity(listing);
    }

    @Override
    public Page<JobListingResponse> searchJobListings(String search, WorkplaceType workplaceType,
                                                      EmploymentType employmentType, Pageable pageable) {
        String trimmedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        return listingRepository.searchJobListings(trimmedSearch, workplaceType, employmentType, pageable)
            .map(JobListingResponse::fromEntity);
    }

    @Override
    @Transactional
    public JobListingResponse updateJobListing(Long id, JobListingUpdateRequest request) {
        if (request == null) {
            throw new AgentFlowException("Job listing update request cannot be null");
        }

        JobListing listing = findListingOrThrow(id);

        String trimmedCompany = request.company().trim();
        String trimmedTitle = request.title().trim();
        String trimmedLocation = request.location() != null ? request.location().trim() : null;
        String newHash = JobListing.calculateContentHash(trimmedCompany, trimmedTitle, trimmedLocation);

        if (!newHash.equals(listing.getContentHash())) {
            Optional<JobListing> existing = listingRepository.findByContentHash(newHash);
            if (existing.isPresent() && !existing.get().getId().equals(id)) {
                throw new ConflictException("Another job listing already exists with identical company, title, and location");
            }
            listing.setContentHash(newHash);
        }

        String trimmedSourceUrl = (request.sourceUrl() != null && !request.sourceUrl().isBlank())
            ? request.sourceUrl().trim()
            : null;
        if (trimmedSourceUrl != null && !trimmedSourceUrl.equals(listing.getSourceUrl())) {
            Optional<JobListing> existingByUrl = listingRepository.findBySourceUrl(trimmedSourceUrl);
            if (existingByUrl.isPresent() && !existingByUrl.get().getId().equals(id)) {
                throw new ConflictException("Another job listing already exists with source URL: " + trimmedSourceUrl);
            }
        }

        listing.setTitle(trimmedTitle);
        listing.setCompany(trimmedCompany);
        listing.setLocation(trimmedLocation);
        listing.setWorkplaceType(request.workplaceType());
        listing.setEmploymentType(request.employmentType());
        listing.setSalaryRange(trimToNull(request.salaryRange()));
        if (request.source() != null && !request.source().isBlank()) {
            listing.setSource(request.source().trim());
        }
        listing.setSourceUrl(trimmedSourceUrl);
        listing.setRawDescription(trimToNull(request.rawDescription()));
        if (request.parsedRequirements() != null) {
            listing.setParsedRequirements(request.parsedRequirements());
        }

        JobListing updated = listingRepository.save(listing);
        log.info("Updated job listing [id: {}, title: '{}']", updated.getId(), trimmedTitle);

        return JobListingResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public void deleteJobListing(Long id) {
        JobListing listing = findListingOrThrow(id);
        listingRepository.delete(listing);
        log.info("Deleted job listing [id: {}]", id);
    }

    @Override
    public Optional<JobListingResponse> findByContentHash(String contentHash) {
        if (contentHash == null || contentHash.isBlank()) {
            return Optional.empty();
        }
        return listingRepository.findByContentHash(contentHash.trim())
            .map(JobListingResponse::fromEntity);
    }

    @Override
    public Optional<JobListingResponse> findBySourceUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return Optional.empty();
        }
        return listingRepository.findBySourceUrl(sourceUrl.trim())
            .map(JobListingResponse::fromEntity);
    }

    private JobListingSaveResult buildDuplicateResult(JobListing existingListing) {
        log.info("Duplicate job listing detected [id: {}, company: '{}', title: '{}']. Preserving existing application record.",
            existingListing.getId(), existingListing.getCompany(), existingListing.getTitle());

        JobApplication existingApp = applicationRepository.findByJobId(existingListing.getId()).orElse(null);

        return JobListingSaveResult.duplicate(
            JobListingResponse.fromEntity(existingListing),
            JobApplicationResponse.fromEntity(existingApp)
        );
    }

    private JobListing findListingOrThrow(Long id) {
        return listingRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Job listing not found with id: " + id));
    }

    private void validateCreateRequest(JobListingCreateRequest request) {
        if (request.title() == null || request.title().trim().isEmpty()) {
            throw new AgentFlowException("Job title is required");
        }
        if (request.company() == null || request.company().trim().isEmpty()) {
            throw new AgentFlowException("Company name is required");
        }
    }

    private String trimToNull(String value) {
        return (value != null && !value.trim().isEmpty()) ? value.trim() : null;
    }
}
