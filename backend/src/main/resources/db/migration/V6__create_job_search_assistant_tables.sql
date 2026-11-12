-- V6: Create Job Search Assistant tables
-- candidate_profiles, candidate_resumes, candidate_job_preferences, job_listings, job_applications

-- 1. candidate_profiles: Single candidate personal information and career links
CREATE TABLE IF NOT EXISTS candidate_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL,
    phone VARCHAR(50) NULL DEFAULT NULL,
    headline VARCHAR(255) NULL DEFAULT NULL,
    location VARCHAR(150) NULL DEFAULT NULL,
    portfolio_url VARCHAR(500) NULL DEFAULT NULL,
    linkedin_url VARCHAR(500) NULL DEFAULT NULL,
    github_url VARCHAR(500) NULL DEFAULT NULL,
    summary TEXT NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_candidate_profiles_email UNIQUE (email)
);

CREATE INDEX idx_candidate_profiles_email ON candidate_profiles(email);

-- 2. candidate_resumes: Multiple resume versions for a candidate profile
CREATE TABLE IF NOT EXISTS candidate_resumes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id BIGINT NOT NULL,
    version_name VARCHAR(100) NOT NULL,
    raw_text LONGTEXT NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    parsed_skills JSON NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_candidate_resumes_profile
        FOREIGN KEY (profile_id) REFERENCES candidate_profiles(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_candidate_resumes_profile_id ON candidate_resumes(profile_id);
CREATE INDEX idx_candidate_resumes_primary ON candidate_resumes(profile_id, is_primary);

-- 3. candidate_job_preferences: 1-to-1 job preferences for a candidate profile
CREATE TABLE IF NOT EXISTS candidate_job_preferences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id BIGINT NOT NULL,
    target_titles JSON NULL DEFAULT NULL,
    preferred_locations JSON NULL DEFAULT NULL,
    workplace_types JSON NULL DEFAULT NULL,
    employment_types JSON NULL DEFAULT NULL,
    min_salary DECIMAL(12, 2) NULL DEFAULT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'USD',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_job_preferences_profile UNIQUE (profile_id),
    CONSTRAINT fk_job_preferences_profile
        FOREIGN KEY (profile_id) REFERENCES candidate_profiles(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_job_preferences_profile_id ON candidate_job_preferences(profile_id);

-- 4. job_listings: Discovered, imported, or manually saved job postings
CREATE TABLE IF NOT EXISTS job_listings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    company VARCHAR(255) NOT NULL,
    location VARCHAR(255) NULL DEFAULT NULL,
    workplace_type VARCHAR(50) NULL DEFAULT NULL,
    employment_type VARCHAR(50) NULL DEFAULT NULL,
    salary_range VARCHAR(100) NULL DEFAULT NULL,
    source VARCHAR(50) NOT NULL DEFAULT 'MANUAL',
    source_url VARCHAR(500) NULL DEFAULT NULL,
    content_hash VARCHAR(64) NOT NULL,
    raw_description LONGTEXT NULL DEFAULT NULL,
    parsed_requirements JSON NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_job_listings_content_hash UNIQUE (content_hash)
);

CREATE INDEX idx_job_listings_company ON job_listings(company);
CREATE INDEX idx_job_listings_title ON job_listings(title);
CREATE INDEX idx_job_listings_workplace_type ON job_listings(workplace_type);
CREATE INDEX idx_job_listings_source_url ON job_listings(source_url);
CREATE INDEX idx_job_listings_created_at ON job_listings(created_at);

-- 5. job_applications: Application tracking and match scoring for a job listing
CREATE TABLE IF NOT EXISTS job_applications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    resume_id BIGINT NULL DEFAULT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'SAVED',
    applied_date DATE NULL DEFAULT NULL,
    follow_up_date DATE NULL DEFAULT NULL,
    notes TEXT NULL DEFAULT NULL,
    match_score DECIMAL(5, 2) NULL DEFAULT NULL,
    match_breakdown JSON NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_job_applications_job UNIQUE (job_id),
    CONSTRAINT fk_job_applications_job
        FOREIGN KEY (job_id) REFERENCES job_listings(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_job_applications_resume
        FOREIGN KEY (resume_id) REFERENCES candidate_resumes(id)
        ON DELETE SET NULL
);

CREATE INDEX idx_job_applications_job_id ON job_applications(job_id);
CREATE INDEX idx_job_applications_resume_id ON job_applications(resume_id);
CREATE INDEX idx_job_applications_status ON job_applications(status);
