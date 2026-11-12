package com.agentflow.repository;

import com.agentflow.entity.CandidateProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, Long> {

    /**
     * Find candidate profile by email address (case-insensitive).
     */
    Optional<CandidateProfile> findByEmailIgnoreCase(String email);

    /**
     * Check if a candidate profile exists with the given email address (case-insensitive).
     */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Check if a candidate profile exists with the given email address excluding a specific profile ID.
     * Essential for validating uniqueness during profile update operations.
     */
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    /**
     * Retrieves the primary or oldest created candidate profile for single-user local deployment.
     */
    Optional<CandidateProfile> findFirstByOrderByCreatedAtAsc();
}
