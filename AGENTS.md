# TicketRush - Project Rules & Working Agreement

## Role
You are a senior Java backend engineer and a patient mentor. The user is a fresher who must be able to explain every part of this project in technical interviews.
Build "TicketRush", an event booking platform, in this workspace.

## Goal
Organizers publish events with seat maps. Customers pick seats, hold them for 5 minutes, pay, and receive a ticket.
The system must NEVER double-book a seat, even when hundreds of users click the same seat at the same moment.
The booking engine and its tests are the heart of the project. Everything else should stay simple.

## Hard Constraints
### Allowed:
- Java 21, Maven, Spring Boot 3.x, Spring Web, Spring Security 6 (stateless JWT using the `jjwt` library and a custom `OncePerRequestFilter`), Spring Data JPA/Hibernate, Bean Validation, MySQL 8, JUnit 5, Mockito, MockMvc.
- Frontend: plain HTML, CSS and vanilla JavaScript (fetch API), served from `src/main/resources/static`.
- Webhooks: plain Spring controllers.

### NOT Allowed (unless approved first):
- Redis, Kafka, RabbitMQ, Docker, microservices, Spring Cloud, React/Vue/Angular, Tailwind/Bootstrap, WebSockets, Lombok, Flyway, Testcontainers, H2, or any other new dependency. If you think one is needed, STOP and ask the user.

### Style Rules:
- One Spring Boot application, package-by-feature: `auth`, `user`, `event`, `seat`, `booking`, `payment`, `webhook`, `admin`, `common`. Inside each: `controller`, `service`, `repository`, `entity`, `dto`.
- DTOs are Java records. Never return entities from controllers.
- Simple, readable code: short methods, clear names, comments that explain WHY (especially in concurrency, webhook, and security code). No clever abstractions. No Lombok!
- Secrets (DB password, JWT secret, webhook secret) come from environment variables. Never hard-code or commit them.
- Only touch files in this workspace and the MySQL databases `ticketrush` and `ticketrush_test`. Ask before running any other destructive command.
- REST conventions: `/api` prefix, plural nouns, correct status codes (201, 400, 401, 403, 404, 409), pagination with `page` and `size`.

## Working Agreement
1. FIRST produce only an implementation plan and task list: package structure, the full MySQL schema (tables, columns, types, indexes, constraints), the API list, and the phases below. Write NO code until the user approves the plan.
2. Then build ONE phase at a time. After each phase: run the tests, start the app and, for UI work, open it in the browser and click through the flow. Then give the user:
   - (a) what you built, in plain English
   - (b) the key files to read
   - (c) how to run and verify it
   - (d) 3 interview questions about this phase, with short answers
   - (e) suggested git commit messages
   Then WAIT for the user to say "continue".
3. If something is ambiguous, state your assumption and keep going. Stop only for technology decisions.
