package com.agentflow.service.impl;

import com.agentflow.dto.job.CandidateProfileRequest;
import com.agentflow.dto.job.CandidateProfileResponse;
import com.agentflow.entity.CandidateProfile;
import com.agentflow.exception.AgentFlowException;
import com.agentflow.exception.ConflictException;
import com.agentflow.exception.ResourceNotFoundException;
import com.agentflow.repository.CandidateProfileRepository;
import com.agentflow.service.CandidateProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;

@Service
@Transactional(readOnly = true)
public class CandidateProfileServiceImpl implements CandidateProfileService {

    private static final Logger log = LoggerFactory.getLogger(CandidateProfileServiceImpl.class);

    private final CandidateProfileRepository profileRepository;

    public CandidateProfileServiceImpl(CandidateProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Override
    public CandidateProfileResponse getCurrentProfile() {
        CandidateProfile profile = profileRepository.findFirstByOrderByCreatedAtAsc()
            .orElseThrow(() -> new ResourceNotFoundException("No candidate profile found. Please create a candidate profile."));
        return CandidateProfileResponse.fromEntity(profile);
    }

    @Override
    public CandidateProfileResponse getProfileById(Long id) {
        CandidateProfile profile = findProfileOrThrow(id);
        return CandidateProfileResponse.fromEntity(profile);
    }

    @Override
    @Transactional
    public CandidateProfileResponse createProfile(CandidateProfileRequest request) {
        validateRequest(request);

        String normalizedEmail = normalizeEmail(request.email());
        if (profileRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("A candidate profile with email '" + normalizedEmail + "' already exists");
        }

        CandidateProfile profile = new CandidateProfile();
        applyRequestToEntity(request, profile, normalizedEmail);

        CandidateProfile saved = profileRepository.save(profile);
        log.info("Created candidate profile [id: {}]", saved.getId());
        return CandidateProfileResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public CandidateProfileResponse updateProfile(Long id, CandidateProfileRequest request) {
        CandidateProfile profile = findProfileOrThrow(id);
        validateRequest(request);

        String normalizedEmail = normalizeEmail(request.email());
        if (profileRepository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail, id)) {
            throw new ConflictException("Another candidate profile with email '" + normalizedEmail + "' already exists");
        }

        applyRequestToEntity(request, profile, normalizedEmail);

        CandidateProfile updated = profileRepository.save(profile);
        log.info("Updated candidate profile [id: {}]", updated.getId());
        return CandidateProfileResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public void deleteProfile(Long id) {
        CandidateProfile profile = findProfileOrThrow(id);
        profileRepository.delete(profile);
        log.info("Deleted candidate profile [id: {}]", id);
    }

    private CandidateProfile findProfileOrThrow(Long id) {
        return profileRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found with id: " + id));
    }

    private void applyRequestToEntity(CandidateProfileRequest request, CandidateProfile profile, String normalizedEmail) {
        profile.setFullName(request.fullName().trim());
        profile.setEmail(normalizedEmail);
        profile.setPhone(trimToNull(request.phone()));
        profile.setHeadline(trimToNull(request.headline()));
        profile.setLocation(trimToNull(request.location()));
        profile.setPortfolioUrl(trimToNull(request.portfolioUrl()));
        profile.setLinkedinUrl(trimToNull(request.linkedinUrl()));
        profile.setGithubUrl(trimToNull(request.githubUrl()));
        profile.setSummary(trimToNull(request.summary()));
    }

    private void validateRequest(CandidateProfileRequest request) {
        if (request == null) {
            throw new AgentFlowException("Profile request cannot be null");
        }
        if (request.fullName() == null || request.fullName().trim().isEmpty()) {
            throw new AgentFlowException("Full name is required");
        }
        if (request.email() == null || request.email().trim().isEmpty()) {
            throw new AgentFlowException("Email address is required");
        }

        validateUrl("portfolioUrl", request.portfolioUrl());
        validateUrl("linkedinUrl", request.linkedinUrl());
        validateUrl("githubUrl", request.githubUrl());
    }

    private void validateUrl(String fieldName, String url) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }
        String trimmed = url.trim();
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new AgentFlowException("Invalid URL format for " + fieldName + ": Must use http or https scheme");
            }
        } catch (URISyntaxException e) {
            throw new AgentFlowException("Invalid URL format for " + fieldName + ": " + e.getMessage());
        }
    }

    private String normalizeEmail(String email) {
        return email != null ? email.trim().toLowerCase() : "";
    }

    private String trimToNull(String value) {
        return (value != null && !value.trim().isEmpty()) ? value.trim() : null;
    }
}
