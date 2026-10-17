# AgentFlow

**AgentFlow** is an enterprise-grade AI workflow automation platform designed to orchestrate autonomous AI agents, tool chains, and multi-step business logic into resilient, observable automation pipelines.

---

## 🏛 Architecture Overview

The platform follows a decoupled, modern full-stack architecture:

* **Backend (`/backend`)**:
  * **Runtime**: Java 25 LTS
  * **Framework**: Spring Boot 3.4.3
  * **Build Tool**: Apache Maven 3.9+
  * **Data Layer**: Spring Data JPA / Hibernate 6.6
  * **Database Driver**: MySQL Connector/J
  * **Migrations**: Flyway
  * **Database**: MySQL 8.x / 9.x
  * **API Layer**: REST with Spring Web & Spring Boot Actuator

* **Frontend (`/frontend`)**:
  * **Runtime**: Node.js v24+ / npm v11+
  * **Stack**: React + Vite SPA
  * **Features**: Workflow builder, real-time node execution telemetry, canvas designer

* **Database (`/database`)**:
  * Reference schema definitions (`schema.sql`) and architectural documentation.

* **Documentation (`/docs`)**:
  * Architecture, system design, and API documentation.

---

## 📋 Prerequisites & Verified Environment

| Dependency | Verified Version | Details |
| :--- | :--- | :--- |
| **Java** | `25.0.4.1 LTS` | Oracle JDK 25 Runtime Environment |
| **Apache Maven** | `3.9.16` | Verified via PATH |
| **Node.js** | `v24.18.0` | Node runtime |
| **npm** | `11.16.0` | Node package manager |
| **Git** | `2.54.0.windows.1` | Git for Windows |
| **MySQL** | `9.6` | Windows Service `MySQL96` on port 3306 |

---

## 📁 Repository Structure

```text
agentflow/
├── backend/                  # Spring Boot backend (Java 25)
│   ├── src/main/java/com/agentflow/
│   │   ├── config/           # CORS, Jackson, Web configuration
│   │   ├── controller/       # REST API endpoints
│   │   ├── dto/              # API request & response contracts
│   │   ├── entity/           # JPA entities (Workflow, WorkflowExecution, NodeExecutionLog)
│   │   ├── exception/        # Exception handlers and domain errors
│   │   ├── repository/       # Spring Data JPA repositories
│   │   ├── service/          # Business logic services
│   │   └── workflow/         # Workflow execution statuses and interfaces
│   └── src/main/resources/
│       ├── application.properties # Main application properties
│       └── db/migration/     # Flyway SQL migrations (V1, V2, V3)
├── frontend/                 # React + Vite frontend application
├── database/                 # Reference SQL schema and DB documentation
├── docs/                     # Architectural documentation
├── .env.example              # Template environment variables
├── .gitignore                # Git ignore configuration
└── README.md                 # Project documentation
```

---

## ⚙️ Configuration & Windows Environment Variables

Database credentials are strictly managed through environment variables and never hardcoded in source files.

### 1. Setting Environment Variables in Windows

#### Option A: PowerShell (Current Session)
```powershell
$env:DB_HOST = "127.0.0.1"
$env:DB_PORT = "3306"
$env:DB_NAME = "agentflow_db1"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "your_actual_password_here"
```

#### Option B: Windows System Environment Variables (Persistent)
```powershell
[System.Environment]::SetEnvironmentVariable('DB_USERNAME', 'root', 'User')
[System.Environment]::SetEnvironmentVariable('DB_PASSWORD', 'your_actual_password_here', 'User')
[System.Environment]::SetEnvironmentVariable('DB_NAME', 'agentflow_db1', 'User')
```

#### Option C: Command Prompt (cmd.exe)
```cmd
set DB_USERNAME=root
set DB_PASSWORD=your_actual_password_here
set DB_NAME=agentflow_db1
```

---

## 🗄️ Database Setup & Migrations

### 1. Create the Database (if not existing)
Run in MySQL CLI or MySQL Workbench:
```sql
CREATE DATABASE IF NOT EXISTS agentflow_db1
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

### 2. Schema Tables
Flyway automatically migrates the following tables on backend boot:
* **`workflows`**: Workflow metadata and JSON node graph definition.
* **`workflow_executions`**: Workflow execution instances, status, input/output data.
* **`node_execution_logs`**: Step-level logs and output states for each executed node.

---

## 🚀 Running the Backend

From the project root:
```powershell
cd backend
mvn clean compile
mvn test
mvn spring-boot:run
```

The server starts on port `8085` (Actuator endpoints at `http://localhost:8085/actuator/health`).
