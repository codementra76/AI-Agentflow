-- V5: Add trigger_type column to workflow_executions table

ALTER TABLE workflow_executions
ADD COLUMN trigger_type VARCHAR(30) NOT NULL DEFAULT 'MANUAL';

CREATE INDEX idx_workflow_executions_trigger_type ON workflow_executions(trigger_type);
