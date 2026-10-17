# AgentFlow Database Management

This directory contains the database design and reference scripts for AgentFlow.

## Database Name
* gentflow_db

## Engine & Encoding
* MySQL 8.x / 9.x
* Default Charset: utf8mb4
* Collation: utf8mb4_unicode_ci

## Schema Architecture
1. **workflows**: Stores workflow definitions, graphs in JSON format, and metadata.
2. **workflow_executions**: Tracks individual runs of a workflow, input/output data, execution status, and timestamps.
3. **
ode_execution_logs**: Detailed log traces for each node executed in a workflow run.

## Migration Strategy
Database migrations are managed automatically through **Flyway** in the Spring Boot backend (ackend/src/main/resources/db/migration):
- V1__init_schema.sql: Initial draft schema.
- V2__create_workflows_table.sql: Workflows table with JSON workflow_definition column.
- V3__create_execution_tables.sql: Workflow executions and node execution logs with foreign key constraints.

## Manual Initialization (Optional)
If running manually via MySQL CLI or MySQL Workbench:
`ash
mysql -u root -p < database/schema.sql
`
