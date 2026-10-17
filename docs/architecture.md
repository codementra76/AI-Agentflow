# AgentFlow Architecture Documentation

## Overview
AgentFlow is an enterprise-grade AI workflow automation platform designed to orchestrate autonomous AI agents, tool chains, and multi-step business logic into resilient, observable automation pipelines.

## Project Structure
`	ext
agentflow/
├── backend/                  # Spring Boot 3.4+ REST backend & orchestration engine
│   ├── src/main/java/com/agentflow/
│   │   ├── config/           # Application, CORS, Jackson, and security configurations
│   │   ├── controller/       # REST API endpoints
│   │   ├── dto/              # Data transfer objects and API request/response contracts
│   │   ├── entity/           # JPA domain entities (Workflow, WorkflowExecution, NodeExecutionLog)
│   │   ├── exception/        # Global error handling and custom domain exceptions
│   │   ├── repository/       # Spring Data JPA data access layer
│   │   ├── service/          # Business logic services
│   │   └── workflow/         # Workflow execution engine interfaces & status definitions
│   └── src/main/resources/
│       ├── db/migration/     # Flyway SQL migrations
│       └── application.properties
├── frontend/                 # React + Vite frontend application
├── database/                 # Reference SQL schemas and database documentation
├── docs/                     # System documentation, design docs, and API specs
├── .env.example              # Environment variable template with placeholders
├── .gitignore                # Global git ignore configuration
└── README.md                 # Project root documentation
`

## Backend Technology Stack
* **Language**: Java 25 LTS
* **Framework**: Spring Boot 3.4.3
* **Data Access**: Spring Data JPA + Hibernate 6.6
* **Database Driver**: MySQL Connector/J
* **Migrations**: Flyway
* **JSON Processing**: Jackson (with native JSON column support)
* **Monitoring**: Spring Boot Actuator
* **Testing**: JUnit 5, Mockito, Spring Boot Test, H2 In-Memory DB

## Database Schema & Relationships
* workflows (1) ──< workflow_executions (N) ──< 
ode_execution_logs (N)
* Foreign keys with ON DELETE CASCADE ensure data integrity.
* JSON columns are used for flexible node graphs, dynamic input payloads, and output states.
