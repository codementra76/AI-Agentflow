-- V4: Create workflow_schedules table for scheduled workflow execution

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
);

CREATE INDEX idx_workflow_schedules_next_run ON workflow_schedules(enabled, next_run_at);
