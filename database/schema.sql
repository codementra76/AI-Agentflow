-- AgentFlow Complete Database Schema
-- Target: MySQL 8.x / 9.x
-- Database: agentflow_db1

CREATE DATABASE IF NOT EXISTS agentflow_db1
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE agentflow_db1;

-- Table: workflows
CREATE TABLE IF NOT EXISTS workflows (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    workflow_definition JSON,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_workflows_name ON workflows(name);
CREATE INDEX idx_workflows_active ON workflows(active);

-- Table: workflow_executions
CREATE TABLE IF NOT EXISTS workflow_executions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    input_data JSON,
    output_data JSON,
    error_message TEXT,
    started_at TIMESTAMP NULL DEFAULT NULL,
    completed_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_workflow_executions_workflow
        FOREIGN KEY (workflow_id) REFERENCES workflows (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_workflow_executions_workflow_id ON workflow_executions(workflow_id);
CREATE INDEX idx_workflow_executions_status ON workflow_executions(status);
CREATE INDEX idx_workflow_executions_started_at ON workflow_executions(started_at);

-- Table: node_execution_logs
CREATE TABLE IF NOT EXISTS node_execution_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_id BIGINT NOT NULL,
    node_id VARCHAR(100) NOT NULL,
    node_type VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    input_data JSON,
    output_data JSON,
    error_message TEXT,
    started_at TIMESTAMP NULL DEFAULT NULL,
    completed_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_node_execution_logs_execution
        FOREIGN KEY (execution_id) REFERENCES workflow_executions (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_node_execution_logs_execution_id ON node_execution_logs(execution_id);
CREATE INDEX idx_node_execution_logs_node_id ON node_execution_logs(node_id);
CREATE INDEX idx_node_execution_logs_status ON node_execution_logs(status);

-- Table: workflow_schedules (V4)
CREATE TABLE IF NOT EXISTS workflow_schedules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    schedule_type VARCHAR(20) NOT NULL,
    scheduled_time TIME NULL DEFAULT NULL,
    day_of_week VARCHAR(100) NULL DEFAULT NULL,
    one_time_execution_at TIMESTAMP NULL DEFAULT NULL,
    time_zone VARCHAR(50) NOT NULL,
    input_data JSON NULL DEFAULT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    next_run_at TIMESTAMP NULL DEFAULT NULL,
    last_run_at TIMESTAMP NULL DEFAULT NULL,
    last_status VARCHAR(50) NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_workflow_schedules_workflow_id UNIQUE (workflow_id),
    CONSTRAINT fk_workflow_schedules_workflow
        FOREIGN KEY (workflow_id) REFERENCES workflows (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_workflow_schedules_next_run ON workflow_schedules(enabled, next_run_at);

-- ===================================================================
-- Job Search Assistant Tables (V6)
-- ===================================================================

-- Table: candidate_profiles
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_candidate_profiles_email ON candidate_profiles(email);

-- Table: candidate_resumes
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_candidate_resumes_profile_id ON candidate_resumes(profile_id);
CREATE INDEX idx_candidate_resumes_primary ON candidate_resumes(profile_id, is_primary);

-- Table: candidate_job_preferences
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_job_preferences_profile_id ON candidate_job_preferences(profile_id);

-- Table: job_listings
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_job_listings_company ON job_listings(company);
CREATE INDEX idx_job_listings_title ON job_listings(title);
CREATE INDEX idx_job_listings_workplace_type ON job_listings(workplace_type);
CREATE INDEX idx_job_listings_source_url ON job_listings(source_url);
CREATE INDEX idx_job_listings_created_at ON job_listings(created_at);

-- Table: job_applications
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_job_applications_job_id ON job_applications(job_id);
CREATE INDEX idx_job_applications_resume_id ON job_applications(resume_id);
CREATE INDEX idx_job_applications_status ON job_applications(status);

