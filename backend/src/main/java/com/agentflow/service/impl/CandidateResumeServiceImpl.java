package com.agentflow.service.impl;

import com.agentflow.dto.job.CandidateResumeCreateRequest;
import com.agentflow.dto.job.CandidateResumeResponse;
import com.agentflow.dto.job.CandidateResumeUpdateRequest;
import com.agentflow.entity.CandidateProfile;
import com.agentflow.entity.CandidateResume;
import com.agentflow.exception.AgentFlowException;
import com.agentflow.exception.ConflictException;
import com.agentflow.exception.ResourceNotFoundException;
import com.agentflow.repository.CandidateProfileRepository;
import com.agentflow.repository.CandidateResumeRepository;
import com.agentflow.service.CandidateResumeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CandidateResumeServiceImpl implements CandidateResumeService {

    private static final Logger log = LoggerFactory.getLogger(CandidateResumeServiceImpl.class);

    private final CandidateResumeRepository resumeRepository;
    private final CandidateProfileRepository profileRepository;

    public CandidateResumeServiceImpl(CandidateResumeRepository resumeRepository,
                                      CandidateProfileRepository profileRepository) {
        this.resumeRepository = resumeRepository;
        this.profileRepository = profileRepository;
    }

    @Override
    @Transactional
    public CandidateResumeResponse createResume(Long profileId, CandidateResumeCreateRequest request) {
        if (request == null) {
            throw new AgentFlowException("Resume request cannot be null");
        }

        CandidateProfile profile = profileRepository.findById(profileId)
            .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found with id: " + profileId));

        String trimmedVersion = validateAndTrimVersionName(request.versionName());
        String trimmedText = validateAndTrimRawText(request.rawText());

        if (resumeRepository.existsByProfileIdAndVersionNameIgnoreCase(profileId, trimmedVersion)) {
            throw new ConflictException("A resume with version name '" + trimmedVersion + "' already exists for this profile");
        }

        long existingCount = resumeRepository.countByProfileId(profileId);
        boolean shouldBePrimary = Boolean.TRUE.equals(request.isPrimary()) || existingCount == 0;

        if (shouldBePrimary) {
            unsetExistingPrimaryResume(profileId);
        }

        CandidateResume resume = new CandidateResume();
        resume.setProfile(profile);
        resume.setVersionName(trimmedVersion);
        resume.setRawText(trimmedText);
        resume.setPrimary(shouldBePrimary);
        resume.setParsedSkills(request.parsedSkills());

        CandidateResume saved = resumeRepository.save(resume);
        log.info("Created resume version [id: {}, version: '{}'] for profile [id: {}]",
            saved.getId(), trimmedVersion, profileId);

        return CandidateResumeResponse.fromEntity(saved);
    }

    @Override
    public List<CandidateResumeResponse> getResumesByProfileId(Long profileId) {
        verifyProfileExists(profileId);
        return resumeRepository.findByProfileIdOrderByCreatedAtDesc(profileId)
            .stream()
            .map(CandidateResumeResponse::fromEntity)
            .toList();
    }

    @Override
    public CandidateResumeResponse getResumeById(Long id) {
        CandidateResume resume = findResumeOrThrow(id);
        return CandidateResumeResponse.fromEntity(resume);
    }

    @Override
    @Transactional
    public CandidateResumeResponse updateResume(Long id, CandidateResumeUpdateRequest request) {
        if (request == null) {
            throw new AgentFlowException("Resume update request cannot be null");
        }

        CandidateResume resume = findResumeOrThrow(id);
        Long profileId = resume.getProfile().getId();

        String trimmedVersion = validateAndTrimVersionName(request.versionName());
        String trimmedText = validateAndTrimRawText(request.rawText());

        if (resumeRepository.existsByProfileIdAndVersionNameIgnoreCaseAndIdNot(profileId, trimmedVersion, id)) {
            throw new ConflictException("Another resume with version name '" + trimmedVersion + "' already exists for this profile");
        }

        if (Boolean.TRUE.equals(request.isPrimary()) && !resume.isPrimary()) {
            unsetExistingPrimaryResume(profileId);
            resume.setPrimary(true);
        } else if (Boolean.FALSE.equals(request.isPrimary()) && resume.isPrimary()) {
            // Cannot unmark primary if this is the only resume for the profile
            long totalResumes = resumeRepository.countByProfileId(profileId);
            if (totalResumes <= 1) {
                log.warn("Cannot unset primary flag when only one resume exists for profile [{}]", profileId);
            } else {
                resume.setPrimary(false);
            }
        }

        resume.setVersionName(trimmedVersion);
        resume.setRawText(trimmedText);
        if (request.parsedSkills() != null) {
            resume.setParsedSkills(request.parsedSkills());
        }

        CandidateResume updated = resumeRepository.save(resume);
        log.info("Updated resume version [id: {}, version: '{}']", updated.getId(), trimmedVersion);
        return CandidateResumeResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public CandidateResumeResponse setPrimaryResume(Long id) {
        CandidateResume resume = findResumeOrThrow(id);
        if (resume.isPrimary()) {
            return CandidateResumeResponse.fromEntity(resume);
        }

        Long profileId = resume.getProfile().getId();
        unsetExistingPrimaryResume(profileId);

        resume.setPrimary(true);
        CandidateResume updated = resumeRepository.save(resume);
        log.info("Set resume [id: {}] as primary for profile [{}]", id, profileId);

        return CandidateResumeResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public void deleteResume(Long id) {
        CandidateResume resume = findResumeOrThrow(id);
        Long profileId = resume.getProfile().getId();
        boolean wasPrimary = resume.isPrimary();

        resumeRepository.delete(resume);
        log.info("Deleted resume [id: {}] for profile [{}]", id, profileId);

        // If the deleted resume was primary, promote the newest remaining resume to primary
        if (wasPrimary) {
            List<CandidateResume> remaining = resumeRepository.findByProfileIdOrderByCreatedAtDesc(profileId);
            if (!remaining.isEmpty()) {
                CandidateResume newPrimary = remaining.get(0);
                newPrimary.setPrimary(true);
                resumeRepository.save(newPrimary);
                log.info("Auto-promoted resume [id: {}] to primary for profile [{}]", newPrimary.getId(), profileId);
            }
        }
    }

    @Override
    public CandidateResumeResponse getPrimaryResume(Long profileId) {
        verifyProfileExists(profileId);
        CandidateResume primary = resumeRepository.findByProfileIdAndIsPrimaryTrue(profileId)
            .orElseThrow(() -> new ResourceNotFoundException("No primary resume found for profile id: " + profileId));
        return CandidateResumeResponse.fromEntity(primary);
    }

    private void unsetExistingPrimaryResume(Long profileId) {
        resumeRepository.findByProfileIdAndIsPrimaryTrue(profileId)
            .ifPresent(existingPrimary -> {
                existingPrimary.setPrimary(false);
                resumeRepository.save(existingPrimary);
            });
    }

    private CandidateResume findResumeOrThrow(Long id) {
        return resumeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Resume not found with id: " + id));
    }

    private void verifyProfileExists(Long profileId) {
        if (!profileRepository.existsById(profileId)) {
            throw new ResourceNotFoundException("Candidate profile not found with id: " + profileId);
        }
    }

    private String validateAndTrimVersionName(String versionName) {
        if (versionName == null || versionName.trim().isEmpty()) {
            throw new AgentFlowException("Resume version name is required");
        }
        String trimmed = versionName.trim();
        if (trimmed.length() > 100) {
            throw new AgentFlowException("Resume version name must not exceed 100 characters");
        }
        return trimmed;
    }

    private String validateAndTrimRawText(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            throw new AgentFlowException("Resume content must not be empty");
        }
        return rawText.trim();
    }
}
