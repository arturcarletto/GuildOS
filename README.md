# Guild OS

Guild OS is a platform for managing, automating, and analyzing Discord communities. This repository currently contains only the production-oriented foundation for its backend.

## Project status

The project is at the initial bootstrap stage. It provides a runnable Spring Boot service, PostgreSQL persistence foundation, Flyway migrations, a real-database integration test, local Docker Compose infrastructure, and backend CI. Discord integration, authentication, community analytics, automation, AI features, and a frontend are not implemented yet.

## Technology stack

- Java 21
- Spring Boot 4.1.0
- Spring MVC, Spring Data JPA, Jakarta Bean Validation, and Actuator
- PostgreSQL 17
- Flyway
- Maven Wrapper
- JUnit and Testcontainers
- Docker Compose
- GitHub Actions

## Repository structure

```text
GuildOS/
|-- .github/workflows/backend-ci.yml
|-- backend/
|   |-- .mvn/wrapper/
|   |-- src/main/
|   |-- src/test/
|   |-- mvnw
|   |-- mvnw.cmd
|   `-- pom.xml
|-- docs/
|   |-- adr/0001-modular-monolith.md
|   `-- architecture.md
|-- .editorconfig
|-- .env.example
|-- .gitattributes
|-- .gitignore
|-- compose.yaml
`-- README.md
```

## Prerequisites

- JDK 21 available on `PATH`
- Docker Desktop with Docker Compose enabled
- PowerShell 7 or Windows PowerShell 5.1

Maven does not need to be installed; the repository includes the Maven Wrapper.

## Start locally on Windows PowerShell

From the repository root, optionally create a local Compose environment file:

```powershell
Copy-Item .env.example .env
```

The committed values are non-sensitive local-development defaults. Change the values in `.env` if they conflict with your environment.

Start PostgreSQL and wait until its health status is `healthy`:

```powershell
docker compose up -d postgres
docker compose ps
```

Run the backend with the `local` profile:

```powershell
Set-Location backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

The local profile connects to `jdbc:postgresql://localhost:5432/guildos` with the documented local-only `guildos_app` / `guildos_local` credentials. `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` can override them.

In another PowerShell window, verify application health:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

The response should report `status` as `UP`. Actuator exposes only `health` and `info`, and health details are not public.

Stop local infrastructure from the repository root:

```powershell
docker compose down
```

Add `-v` only when you intentionally want to delete the local PostgreSQL volume.

## Run tests and verification

The integration test starts its own PostgreSQL container, runs Flyway, loads the Spring application context, and checks the resulting schema. Docker Desktop must be running.

```powershell
Set-Location backend
.\mvnw.cmd test
.\mvnw.cmd verify
```

CI runs the same Maven `verify` lifecycle on pushes to `main` and pull requests targeting `main`.

## Configuration model

Shared configuration requires `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`, making non-local runtime configuration explicit. The `local` profile supplies documented local-only defaults. Hibernate validates the schema but never creates or updates it; Flyway owns all schema changes.

See [the architecture overview](docs/architecture.md) and [ADR 0001](docs/adr/0001-modular-monolith.md) for the initial design decisions.
