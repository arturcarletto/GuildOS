# ADR 0001: Start with a modular monolith

## Status

Accepted

## Context

Guild OS will eventually cover several Discord community-management capabilities, but the product boundaries, traffic patterns, and operational needs are not yet established. Beginning with distributed services would add network failure modes, cross-service data consistency concerns, and deployment overhead before those costs solve a demonstrated problem.

The codebase still needs clear boundaries so that features do not become tightly coupled as the platform grows.

## Decision

Build Guild OS as a single Spring Boot deployable organized by business capability. Add packages and module contracts only when the corresponding features are implemented. Keep persistence details within their owning capability and manage the shared PostgreSQL database exclusively with Flyway migrations.

Treat module extraction as an evidence-based future change. Candidates may become services when independent scaling, failure isolation, deployment cadence, or team ownership provides enough benefit to justify distributed-system complexity.

## Consequences

- Local development, integration testing, deployment, and transactions remain simple.
- Refactoring boundaries is inexpensive while the domain is still developing.
- One application and database initially share a release and scaling unit.
- Architectural discipline and automated boundary checks will become necessary as modules are added.
- Later extraction will require explicit APIs, data ownership, and migration planning.

## Alternatives considered

- **Microservices from the start:** rejected because the current scope does not justify distributed operations, messaging, or eventual-consistency concerns.
- **Unstructured layered monolith:** rejected because global controller, service, and repository layers tend to couple unrelated business capabilities.
- **Serverless functions per capability:** rejected because the initial request/database flow benefits from a cohesive application, conventional transactions, and predictable local testing.

