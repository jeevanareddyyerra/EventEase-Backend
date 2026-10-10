# EventEase — Backend REST API Service

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**EventEase Backend** is a modern, enterprise-grade REST API service providing event lifecycle automation, role-based access control (RBAC), volunteer quota coordination, team management, real-time attendance tracking, verifiable digital certificates, and an AI-driven Assistant with Semantic Retrieval-Augmented Generation (RAG).

Built with **Spring Boot 3.4.3** and **Java 21**, designed for decoupled deployment alongside the [EventEase-Frontend](https://github.com/jeevanayerra/EventEase-Frontend) application.

---

## Architecture & Technology Stack

- **Framework**: Spring Boot 3.4.3
- **Runtime**: Eclipse Temurin OpenJDK 21
- **Persistence**: Spring Data JPA, Hibernate, MySQL 8.0 (InnoDB)
- **Security**: Spring Security 6 (Stateless JWT HMAC-SHA256, BCrypt Password Hashing, CORS Filtering)
- **API Documentation**: SpringDoc OpenAPI 2.8.5 (OpenAPI 3.1 & Interactive Swagger UI)
- **Monitoring & Health**: Spring Boot Actuator (`/actuator/health`, `/actuator/info`)
- **AI & Semantic RAG**: Microsoft ONNX Runtime 1.20.1 (`all-MiniLM-L6-v2` dense vector embeddings) + Google Gemini API (`gemini-3.8-flash`)
- **Testing**: JUnit 5, Mockito, SpringBootTest, H2 in-memory test database

---

## Key Features

1. **Role-Based Access Control (RBAC)**:
   - `USER`: Browse events, register tickets, lead/join teams, access issued certificates, chat with AI assistant.
   - `ORGANIZER`: Propose events, manage attendee rosters, recruit volunteers, mark real-time attendance, complete events, issue certificates.
   - `ADMIN`: Platform-wide governance, approve/reject event proposals, inspect platform statistics.

2. **Event Lifecycle State Machine**:
   - `PENDING` &rarr; `APPROVED` &rarr; `PUBLISHED` &rarr; `COMPLETED` / `CANCELLED`.
   - Transition rules strictly prevent invalid state bypasses.

3. **Volunteer Recruitment & Team Management**:
   - Quota definition (`VolunteerRequirement`) with applicant intake and organizer approval (`ACCEPTED`/`REJECTED`).
   - Team formation with leader verification, size limits, and duplicate-membership prevention.

4. **Attendance Verification & Digital Certificates**:
   - Organizers verify attendance (`PRESENT`/`ABSENT`).
   - Digital certificates are locked until event completion and verified attendance, issued with cryptographic verification URLs.

5. **Local Semantic RAG Pipeline**:
   - Ingests event FAQs, guidelines, rules, and schedules into 384-dimensional dense vectors.
   - Performs cosine similarity retrieval against local document chunks.
   - Grounded prompt synthesis through Google Gemini API with verifiable multi-source citations and anti-hallucination guardrails.

---

## Prerequisites

- **Java JDK 21+** (Eclipse Temurin recommended)
- **Apache Maven 3.9+**
- **MySQL 8.0** (or Docker Desktop to run containerized MySQL)

---

## Configuration & Environment Variables

Copy the environment configuration template before running:

```bash
cp .env.example .env
```

### Environment Variables Reference

| Variable | Required | Default / Example | Description |
| :--- | :---: | :--- | :--- |
| `PORT` | Optional | `8080` | Spring Boot HTTP listening port |
| `DB_URL` | **Yes** | `jdbc:mysql://localhost:3306/eventease` | JDBC connection string |
| `DB_USERNAME` | **Yes** | `eventease_app` | Dedicated database application user |
| `DB_PASSWORD` | **Yes** | *(configured locally)* | Database user password |
| `JWT_SECRET` | **Yes** | *(64-hex char secret)* | Secret key for HMAC-SHA256 JWT signing |
| `JWT_EXPIRATION` | Optional | `86400000` (24h in ms) | JWT validity duration in milliseconds |
| `CORS_ALLOWED_ORIGINS`| Optional | `http://localhost:3000,http://localhost:5173` | Allowed frontend origins |
| `GEMINI_API_KEY` | Optional | `""` | Google Gemini API key for live generative synthesis |
| `GEMINI_MODEL` | Optional | `gemini-3.8-flash` | Gemini model identifier |
| `GEMINI_ENABLED` | Optional | `true` | Enable/disable AI generative completions |

> [!IMPORTANT]
> Never commit `.env` or files containing real secrets to version control. Root `.env` is automatically ignored by `.gitignore`.

---

## Quickstart & Local Development

### 1. Start Local MySQL Database (Docker)
A helper compose file is included to run a MySQL 8.0 instance locally:
```bash
docker compose -f compose.dev.yaml up -d
```

### 2. Run the Application with Maven
```bash
mvn spring-boot:run
```
The backend starts on `http://localhost:8080`.

### 3. Run Automated Tests
```bash
mvn test
```
*Executes all 376 unit, integration, and security tests using an in-memory H2 database.*

---

## API Documentation & Interactive Exploration

- **Interactive Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **OpenAPI 3.1 Spec (JSON)**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Actuator Health Probe**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- **Actuator Info**: [http://localhost:8080/actuator/info](http://localhost:8080/actuator/info)

---

## Docker Container Build

The backend includes a production multi-stage `Dockerfile` based on `maven:3.9-eclipse-temurin-21` (build stage) and `eclipse-temurin:21-jre-jammy` (runtime stage), running as an unprivileged non-root user:

```bash
docker build -t eventease-backend .
```

Run container:
```bash
docker run -d -p 8080:8080 --env-file .env eventease-backend
```

---

## Demonstration Data Seeding

To populate realistic sample accounts, events, teams, attendance records, certificates, and RAG knowledge documents:

```bash
node scripts/seed_demo_data.js
```
*To perform a clean re-population, pass `--reset`:*
```bash
node scripts/seed_demo_data.js --reset
```

---

## License

This project is licensed under the MIT License.
