package com.agentflow.repository;

import com.agentflow.entity.CandidateResume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CandidateResumeRepository extends JpaRepository<CandidateResume, Long> {

    /**
     * Find all resume versions belonging to a candidate profile, ordered by creation date descending.
     */
    List<CandidateResume> findByProfileIdOrderByCreatedAtDesc(Long profileId);

    /**
     * Find all resume versions belonging to a candidate profile.
     */
    List<CandidateResume> findByProfileId(Long profileId);

    /**
     * Find the primary resume designated for a candidate profile.
     */
    Optional<CandidateResume> findByProfileIdAndIsPrimaryTrue(Long profileId);

    /**
     * Check if a resume with the specified version name already exists for a candidate profile (case-insensitive).
     */
    boolean existsByProfileIdAndVersionNameIgnoreCase(Long profileId, String versionName);

    /**
     * Check if a resume with the specified version name already exists for a candidate profile,
     * excluding a specific resume ID (for version rename operations).
     */
    boolean existsByProfileIdAndVersionNameIgnoreCaseAndIdNot(Long profileId, String versionName, Long id);

    /**
     * Count total resume variations stored for a candidate profile.
     */
    long countByProfileId(Long profileId);
}
