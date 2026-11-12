package com.agentflow.service.impl;

import com.agentflow.dto.job.CandidateJobPreferenceRequest;
import com.agentflow.dto.job.CandidateJobPreferenceResponse;
import com.agentflow.entity.CandidateJobPreference;
import com.agentflow.entity.CandidateProfile;
import com.agentflow.entity.EmploymentType;
import com.agentflow.entity.WorkplaceType;
import com.agentflow.exception.AgentFlowException;
import com.agentflow.exception.ConflictException;
import com.agentflow.exception.ResourceNotFoundException;
import com.agentflow.repository.CandidateJobPreferenceRepository;
import com.agentflow.repository.CandidateProfileRepository;
import com.agentflow.service.CandidateJobPreferenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CandidateJobPreferenceServiceImpl implements CandidateJobPreferenceService {

    private static final Logger log = LoggerFactory.getLogger(CandidateJobPreferenceServiceImpl.class);

    private final CandidateJobPreferenceRepository preferenceRepository;
    private final CandidateProfileRepository profileRepository;
    private final ObjectMapper objectMapper;

    public CandidateJobPreferenceServiceImpl(CandidateJobPreferenceRepository preferenceRepository,
                                             CandidateProfileRepository profileRepository,
                                             ObjectMapper objectMapper) {
        this.preferenceRepository = preferenceRepository;
        this.profileRepository = profileRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public CandidateJobPreferenceResponse getPreferencesByProfileId(Long profileId) {
        verifyProfileExists(profileId);
        CandidateJobPreference preference = preferenceRepository.findByProfileId(profileId)
            .orElseThrow(() -> new ResourceNotFoundException("No job preferences found for profile id: " + profileId));
        return CandidateJobPreferenceResponse.fromEntity(preference);
    }

    @Override
    @Transactional
    public CandidateJobPreferenceResponse createPreferences(Long profileId, CandidateJobPreferenceRequest request) {
        if (request == null) {
            throw new AgentFlowException("Job preferences request cannot be null");
        }

        CandidateProfile profile = profileRepository.findById(profileId)
            .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found with id: " + profileId));

        if (preferenceRepository.existsByProfileId(profileId)) {
            throw new ConflictException("Job preferences already exist for profile id: " + profileId + ". Use update instead.");
        }

        validatePreferencesRequest(request);

        CandidateJobPreference preference = new CandidateJobPreference();
        preference.setProfile(profile);
        applyRequestToEntity(request, preference);

        CandidateJobPreference saved = preferenceRepository.save(preference);
        log.info("Created job preferences [id: {}] for profile [id: {}]", saved.getId(), profileId);

        return CandidateJobPreferenceResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public CandidateJobPreferenceResponse updatePreferences(Long profileId, CandidateJobPreferenceRequest request) {
        if (request == null) {
            throw new AgentFlowException("Job preferences request cannot be null");
        }

        verifyProfileExists(profileId);
        CandidateJobPreference preference = preferenceRepository.findByProfileId(profileId)
            .orElseThrow(() -> new ResourceNotFoundException("No job preferences found for profile id: " + profileId));

        validatePreferencesRequest(request);
        applyRequestToEntity(request, preference);

        CandidateJobPreference updated = preferenceRepository.save(preference);
        log.info("Updated job preferences [id: {}] for profile [id: {}]", updated.getId(), profileId);

        return CandidateJobPreferenceResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public CandidateJobPreferenceResponse saveOrUpdatePreferences(Long profileId, CandidateJobPreferenceRequest request) {
        if (preferenceRepository.existsByProfileId(profileId)) {
            return updatePreferences(profileId, request);
        } else {
            return createPreferences(profileId, request);
        }
    }

    private void verifyProfileExists(Long profileId) {
        if (!profileRepository.existsById(profileId)) {
            throw new ResourceNotFoundException("Candidate profile not found with id: " + profileId);
        }
    }

    private void validatePreferencesRequest(CandidateJobPreferenceRequest request) {
        if (request.minSalary() != null && request.minSalary().compareTo(BigDecimal.ZERO) < 0) {
            throw new AgentFlowException("Minimum salary cannot be negative");
        }

        if (request.workplaceTypes() != null) {
            for (String wt : request.workplaceTypes()) {
                if (wt != null && !wt.isBlank()) {
                    try {
                        WorkplaceType.fromString(wt);
                    } catch (IllegalArgumentException ex) {
                        throw new AgentFlowException("Invalid workplace type: '" + wt + "'");
                    }
                }
            }
        }

        if (request.employmentTypes() != null) {
            for (String et : request.employmentTypes()) {
                if (et != null && !et.isBlank()) {
                    try {
                        EmploymentType.fromString(et);
                    } catch (IllegalArgumentException ex) {
                        throw new AgentFlowException("Invalid employment type: '" + et + "'");
                    }
                }
            }
        }
    }

    private void applyRequestToEntity(CandidateJobPreferenceRequest request, CandidateJobPreference preference) {
        preference.setTargetTitles(stringListToJsonArray(request.targetTitles()));
        preference.setPreferredLocations(stringListToJsonArray(request.preferredLocations()));
        preference.setWorkplaceTypes(stringListToJsonArray(request.workplaceTypes()));
        preference.setEmploymentTypes(stringListToJsonArray(request.employmentTypes()));

        preference.setMinSalary(request.minSalary());
        String currency = (request.currency() != null && !request.currency().isBlank())
            ? request.currency().trim().toUpperCase()
            : "USD";
        preference.setCurrency(currency);
    }

    private ArrayNode stringListToJsonArray(List<String> list) {
        if (list == null) {
            return null;
        }
        ArrayNode array = objectMapper.createArrayNode();
        for (String item : list) {
            if (item != null && !item.isBlank()) {
                array.add(item.trim());
            }
        }
        return array;
    }
}
