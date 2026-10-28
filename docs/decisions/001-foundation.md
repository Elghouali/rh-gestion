# ADR 001 — First executable milestone
Spring Boot 3.5.16 / Spring Cloud 2025.0.3, Java 21 and Angular 21 are used together with the installed Node 24.14 runtime. Versions were checked against the official registries on 28 October 2026.
Only Administration and Gateway are implemented now. Inventory, Commerce, Finance and Insights are future services; no placeholder endpoint pretends to implement them.
The initial admin slice uses Spring JdbcClient for read-only establishment queries, with Flyway/PostgreSQL. JPA can be added where an aggregate warrants it.
Local fixtures are isolated in db/dev and activated only by the local profile. Ibrahim is assigned M01 for demonstration, the stock manager to the depot; these assignments require confirmation.
Local OIDC uses explicit public/internal endpoints so browser issuer URLs do not depend on Docker DNS. Local ports bind to loopback. Compose uses start-dev and HTTP and is not a production deployment.
BFF sessions and authorized clients are in memory for this single-instance development milestone. Restarting the BFF requires sign-in again. Shared encrypted session persistence, online permission revocation across services, business workflows and production TLS are later gates.
RabbitMQ is an optional messaging profile until outbox/inbox are implemented. No event-driven transaction is claimed yet.
