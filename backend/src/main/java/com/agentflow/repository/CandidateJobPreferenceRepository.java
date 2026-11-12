package com.agentflow.repository;

import com.agentflow.entity.CandidateJobPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CandidateJobPreferenceRepository extends JpaRepository<CandidateJobPreference, Long> {

    /**
     * Find the 1-to-1 job preferences defined for a candidate profile.
     */
    Optional<CandidateJobPreference> findByProfileId(Long profileId);

    /**
     * Check whether job preferences already exist for a candidate profile.
     */
    boolean existsByProfileId(Long profileId);

    /**
     * Delete job preferences for a candidate profile.
     */
    void deleteByProfileId(Long profileId);
}
