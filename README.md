# 🎟️ TicketRush - High-Concurrency Event & Cinema Ticket Booking Platform

[![Java](https://img.shields.io/badge/Java-21-orange.svg?style=flat&logo=openjdk)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.4-brightgreen.svg?style=flat&logo=springboot)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg?style=flat&logo=mysql)](https://www.mysql.com/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**TicketRush** is a production-grade event and cinema ticket booking platform designed to handle high-traffic ticket sales with zero double-booking guarantees. Organizers can publish events with structured seat maps, customers can pick seats in real-time, hold them for 5 minutes during checkout, pay securely via idempotent webhooks, and download confirmation tickets.

---

## ⚡ Key Highlights & Engineering Features

- 🛡️ **Zero Double-Booking Guarantee:**
  Uses database-level pessimistic write locking (`SELECT ... FOR UPDATE` via `LockModeType.PESSIMISTIC_WRITE`) to ensure that even when hundreds of concurrent requests hit the exact same seat within milliseconds, only one transaction succeeds while others fail gracefully with a `409 Conflict`.
- ⏱️ **5-Minute Reservation TTL & Auto-Release:**
  Seats held in `HELD` status expire automatically after 5 minutes if checkout is not completed. A background Spring `@Scheduled` task cleans up expired holds every 30 seconds and restores the seats to `AVAILABLE`.
- 🎬 **Live Hindi Cinema Catalog Sync (TMDB API):**
  Integrates with The Movie Database (TMDB) API to dynamically fetch ongoing theatrical releases and upcoming Hindi cinema blockbusters, complete with posters, synopses, release dates, and auto-generated cinema seat maps.
- 💺 **Interactive Dynamic Seat Maps:**
  Visual cinema hall layout featuring VIP, Premium, and Standard seating tiers, row markers on both left and right flanks, centered stage/screen orientation, and real-time total price calculation.
- 🔐 **Stateless JWT Security & RBAC:**
  Custom `OncePerRequestFilter` with stateless JWT authentication using `jjwt`. Role-based access control protecting Customer, Organizer, and Admin workflows.
- 💳 **Idempotent Webhook Payment Processing:**
  Accepts payment gateway webhooks verified via cryptographic HMAC-SHA256 signatures with replay attack protection and idempotency checks.
- 👤 **Full Customer Account & Settings:**
  Customer profile hub with live stat cards (total bookings, tickets booked, total spend), personal details editor, BCrypt password changer, and one-click ticket downloads.
- 🚀 **Cloud-Ready (Render + TiDB Cloud):**
  Environment-variable-driven configuration with dynamic port binding (`server.port=${PORT:8088}`) and SSL support for MySQL-compatible cloud databases like TiDB Cloud.

---

## 🏗️ Architecture & Technology Stack

| Layer | Technology |
|---|---|
| **Language** | Java 21 LTS |
| **Framework** | Spring Boot 3.2.4 |
| **Security** | Spring Security 6 (Stateless JWT via JJWT, BCrypt) |
| **Persistence** | Spring Data JPA / Hibernate 6, MySQL 8 / TiDB Cloud |
| **Validation** | Jakarta Bean Validation (`spring-boot-starter-validation`) |
| **Testing** | JUnit 5, Mockito, MockMvc, `CompletableFuture` Concurrency Tests |
| **Frontend** | Vanilla JavaScript (Fetch API), Semantic HTML5, Modern Responsive CSS |
| **APIs** | RESTful conventions with `/api` prefix and standard HTTP status codes |

---

## 📂 Project Structure & Architecture

TicketRush follows a clean **Package-by-Feature** architecture. Each domain module encapsulates its own controller, service, repository, entity, and Java records (DTOs). This guarantees high cohesion, zero tight coupling, and instant maintainability.

```text
ticketrush/
├── .mvn/wrapper/                               # Maven wrapper configuration & binaries
├── src/
│   ├── main/
│   │   ├── java/com/ticketrush/
│   │   │   ├── TicketRushApplication.java      # Application entry point & @EnableScheduling
│   │   │   │
│   │   │   ├── admin/                          # Platform administration & analytics
│   │   │   │   ├── controller/                 # AdminController (/api/admin/*)
│   │   │   │   ├── dto/                        # PlatformStatsResponse, AdminUserResponse, etc.
│   │   │   │   └── service/                    # AdminService (revenue, user roles, booking audits)
│   │   │   │
│   │   │   ├── auth/                           # Stateless JWT authentication & registration
│   │   │   │   ├── controller/                 # AuthController (/api/auth/register, /login)
│   │   │   │   ├── dto/                        # RegisterRequest, LoginRequest, AuthResponse
│   │   │   │   └── service/                    # AuthService (BCrypt hashing, JWT issuance)
│   │   │   │
│   │   │   ├── booking/                        # Core high-concurrency booking engine
│   │   │   │   ├── controller/                 # BookingController (/api/bookings/*)
│   │   │   │   ├── dto/                        # HoldSeatsRequest, BookingDetailResponse, etc.
│   │   │   │   ├── entity/                     # Booking, BookingSeat, BookingStatus
│   │   │   │   ├── repository/                 # BookingRepository, BookingSeatRepository
│   │   │   │   └── service/                    # BookingService (Pessimistic write locking, 5-min TTL)
│   │   │   │                                   # HoldExpiryScheduler (auto-releases expired seats)
│   │   │   │
│   │   │   ├── common/                         # Shared infrastructure, security & error handling
│   │   │   │   ├── config/                     # SecurityConfig, DataInitializer (seed catalog data)
│   │   │   │   ├── exception/                  # GlobalExceptionHandler, Custom Exceptions (409, 404, 400)
│   │   │   │   ├── security/                   # JwtTokenProvider, JwtAuthenticationFilter, UserPrincipal
│   │   │   │   └── util/                       # SignatureUtils (HMAC-SHA256 signature verification)
│   │   │   │
│   │   │   ├── event/                          # Event publishing, catalog search & live cinema feed
│   │   │   │   ├── catalog/                    # Live TMDB / Ticketmaster external API sync
│   │   │   │   │   ├── client/                 # TmdbApiClient, TicketmasterApiClient (RestTemplate)
│   │   │   │   │   ├── controller/             # CatalogController (/api/events/catalog/sync)
│   │   │   │   │   ├── dto/                    # External API response mapping records
│   │   │   │   │   └── service/                # CatalogSyncService (auto-seeds Hindi cinema & seat maps)
│   │   │   │   ├── controller/                 # EventController (/api/events/*)
│   │   │   │   ├── dto/                        # CreateEventRequest, EventDetailResponse, etc.
│   │   │   │   ├── entity/                     # Event, EventStatus (DRAFT, PUBLISHED, CANCELLED)
│   │   │   │   ├── repository/                 # EventRepository (custom JPA search queries)
│   │   │   │   └── service/                    # EventService (event lifecycle management)
│   │   │   │
│   │   │   ├── payment/                        # Payment session checkout & verification
│   │   │   │   ├── controller/                 # PaymentController (/api/payments/*)
│   │   │   │   ├── dto/                        # InitiatePaymentRequest, PaymentStatusResponse
│   │   │   │   ├── entity/                     # Payment, PaymentStatus (PENDING, SUCCESS, FAILED)
│   │   │   │   ├── repository/                 # PaymentRepository
│   │   │   │   └── service/                    # PaymentService, MockPaymentGateway
│   │   │   │
│   │   │   ├── seat/                           # Seat grid generation & row/tier management
│   │   │   │   ├── controller/                 # SeatController (/api/events/{id}/seats)
│   │   │   │   ├── dto/                        # SeatMapResponse, SeatResponse (VIP, Premium, Standard)
│   │   │   │   ├── entity/                     # EventSeat, SeatStatus (AVAILABLE, HELD, BOOKED)
│   │   │   │   ├── repository/                 # EventSeatRepository (with PESSIMISTIC_WRITE locks)
│   │   │   │   └── service/                    # SeatService (hall layout generation)
│   │   │   │
│   │   │   ├── user/                           # Customer profile & account settings
│   │   │   │   ├── controller/                 # UserController (/api/users/me/*)
│   │   │   │   ├── dto/                        # UserProfileResponse, ChangePasswordRequest, Stats
│   │   │   │   ├── entity/                     # User, Role (CUSTOMER, ORGANIZER, ADMIN)
│   │   │   │   ├── repository/                 # UserRepository
│   │   │   │   └── service/                    # UserService (profile edits & password hashing)
│   │   │   │
│   │   │   └── webhook/                        # Cryptographic gateway webhook listener
│   │   │       ├── controller/                 # WebhookController (/api/webhooks/payment)
│   │   │       ├── dto/                        # WebhookPayload, WebhookResponse
│   │   │       ├── entity/                     # WebhookEvent (idempotency ledger)
│   │   │       ├── repository/                 # WebhookEventRepository
│   │   │       └── service/                    # WebhookService (idempotent signature verification)
│   │   │
│   │   └── resources/
│   │       ├── application.properties          # Environment-driven application configurations
│   │       └── static/                         # Lightweight responsive frontend (vanilla JS & CSS)
│   │           ├── css/
│   │           │   └── styles.css              # Dark theme styling, row labels & cinema seat map
│   │           ├── js/
│   │           │   └── api.js                  # Centralized Fetch API client & JWT session manager
│   │           ├── index.html                  # Landing catalog with live Hindi cinema releases & pagination
│   │           ├── event-details.html          # Interactive seat map selection & screen view
│   │           ├── checkout.html               # 5-minute reservation timer & checkout summary
│   │           ├── mock-pay.html               # Payment gateway simulation & webhook trigger
│   │           ├── ticket.html                 # Confirmed booking pass with QR & print/download
│   │           ├── my-bookings.html            # Customer booking history & status tracker
│   │           ├── profile.html                # Account settings, profile editor & password change
│   │           ├── login.html / register.html  # Authentication pages
│   │           ├── organizer.html              # Event publisher & seat tier setup dashboard
│   │           └── admin.html                  # System analytics, user administration & booking audit
│   │
│   └── test/java/com/ticketrush/               # Comprehensive automated test suite
│       ├── auth/AuthIntegrationTest.java       # JWT token lifecycle & role verification tests
│       ├── booking/
│       │   ├── BookingUnitTest.java            # Unit tests for reservation business logic
│       │   ├── ConcurrencyBookingTest.java     # 50-thread concurrent seat race test (pessimistic lock)
│       │   └── naive/                          # Demonstration comparing naive race conditions vs locks
│       ├── event/
│       │   ├── EventIntegrationTest.java       # Event publishing & seat map generation tests
│       │   └── catalog/                        # TMDB live sync tests & fallback catalog unit tests
│       ├── user/UserIntegrationTest.java       # User profile & password change integration tests
│       └── webhook/WebhookIntegrationTest.java # HMAC-SHA256 signature tampering & idempotency tests
│
├── .env.example                                # Environment variable configuration template
├── .gitignore                                  # Git exclusion rules (target/, .idea/, logs, secrets)
├── pom.xml                                     # Maven dependencies and build plugins
├── README.md                                   # Project documentation & architecture manual
└── system.properties                           # Java 21 runtime declaration for cloud deployers
```

---

## 📐 Database Schema

TicketRush utilizes a relational schema optimized for ACID transactions, locking, and auditability:

```
+---------------+        +---------------+        +---------------+
|     users     | 1    * |    events     | 1    * |     seats     |
|---------------|<-------|---------------|<-------|---------------|
| id (PK)       |        | id (PK)       |        | id (PK)       |
| email (UQ)    |        | organizer_id  |        | event_id (FK) |
| password_hash |        | title         |        | seat_number   |
| full_name     |        | venue         |        | row_name      |
| role          |        | start_time    |        | tier          |
+---------------+        +---------------+        | price         |
        |                        |                | status        |
        | 1                      | 1              +---------------+
        |                        |                        |
        | *                      | *                      | 1
+---------------+        +---------------+                |
|   payments    | 1    * |   bookings    | 1            * |
|---------------|<-------|---------------|<---------------+
| id (PK)       |        | id (PK)       | (via booking_seats)
| booking_id    |        | user_id (FK)  |
| amount        |        | event_id (FK) |
| status        |        | status        |
| txn_id (UQ)   |        | expires_at    |
+---------------+        +---------------+
```

---

## 🔒 Concurrency Design: Preventing Double Booking

When a customer initiates a seat hold:
```sql
SELECT s FROM Seat s WHERE s.id IN :seatIds FOR UPDATE
```
1. Spring Data JPA issues `SELECT ... FOR UPDATE` via `LockModeType.PESSIMISTIC_WRITE`.
2. MySQL places an exclusive row-level lock on the selected seat rows.
3. The transaction inspects seat status. If any seat is not `AVAILABLE`, the transaction rolls back immediately and returns `409 Conflict`.
4. If available, seats are transitioned to `HELD`, associated with a new `Booking` in `PENDING` status, and stamped with `expires_at = now() + 5 minutes`.
5. The lock is released on transaction commit (`@Transactional`), completing within ~5-15 milliseconds.

### Verification
A multithreaded test (`BookingConcurrencyIntegrationTest`) spawns 50 parallel threads attempting to reserve the exact same seat simultaneously. Exactly 1 transaction succeeds (201 Created), and 49 receive a clean 409 Conflict with zero database corruption.

---

## 🚦 API Endpoints

### 🔑 Authentication (`/api/auth`)
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `POST` | `/api/auth/register` | Register a new user | Public |
| `POST` | `/api/auth/login` | Login and obtain JWT token | Public |

### 🎭 Events (`/api/events`)
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `GET` | `/api/events` | List all upcoming events (paginated) | Public |
| `GET` | `/api/events/{id}` | Get event details and seat catalog | Public |
| `POST` | `/api/events` | Create a new event with seat configuration | Organizer / Admin |
| `PUT` | `/api/events/{id}` | Update event details | Organizer / Admin |
| `DELETE`| `/api/events/{id}` | Cancel/delete event | Organizer / Admin |

### 💺 Bookings & Seats (`/api/bookings`)
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `POST` | `/api/bookings` | Hold seats (5-minute TTL reservation) | Customer |
| `GET` | `/api/bookings/my` | List current user's booking history | Customer |
| `GET` | `/api/bookings/{id}` | Get booking details & ticket | Customer / Admin |
| `POST` | `/api/bookings/{id}/cancel` | Cancel booking and release seats | Customer |

### 💳 Payments & Webhooks (`/api/payments`, `/api/webhooks`)
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `POST` | `/api/payments/checkout` | Initiate payment for held booking | Customer |
| `POST` | `/api/webhooks/payment` | Gateway webhook callback (HMAC-SHA256) | Gateway Signature |

### 👤 User Account & Profile (`/api/users`)
| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `GET` | `/api/users/me` | Get current user profile and stats | Authenticated |
| `PUT` | `/api/users/me` | Update full name / profile | Authenticated |
| `PUT` | `/api/users/me/password` | Change password (BCrypt verified) | Authenticated |

---

## 🛠️ Local Development Setup

### 1. Prerequisites
- **Java 21** or higher (`java -version`)
- **Maven 3.9+** (`mvn -version`)
- **MySQL 8.0+** running locally (or a free cloud database like TiDB Cloud)

### 2. Configure Database
Create the MySQL database:
```sql
CREATE DATABASE ticketrush;
CREATE DATABASE ticketrush_test;
```

### 3. Set Environment Variables
Copy `.env.example` or export the variables:
```bash
export DB_URL="jdbc:mysql://localhost:3306/ticketrush?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
export DB_USERNAME="root"
export DB_PASSWORD="your_mysql_password"
export JWT_SECRET="404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
```

### 4. Build and Run
```bash
# Run unit and integration tests
./mvnw clean test

# Package JAR
./mvnw clean package -DskipTests

# Run application
java -jar target/ticketrush-0.0.1-SNAPSHOT.jar
```

Open your browser at `http://localhost:8088`.

---

## ☁️ Deployment Guide (Render + TiDB Cloud)

1. **Database (TiDB Cloud Serverless):**
   - Create a free cluster on [TiDB Cloud](https://tidbcloud.com/).
   - Copy the JDBC URL, username, and password.
2. **Web Service (Render):**
   - Create a **New Web Service** connected to this repository.
   - **Environment:** `Java` / `Docker` or Native Java with `system.properties` (`java.runtime.version=21`).
   - **Build Command:** `./mvnw clean package -DskipTests`
   - **Start Command:** `java -jar target/ticketrush-0.0.1-SNAPSHOT.jar`
   - **Environment Variables:**
     - `DB_URL`: TiDB Cloud JDBC URL (`jdbc:mysql://...:4000/ticketrush?useSSL=true&verifyServerCertificate=true&sslMode=VERIFY_IDENTITY`)
     - `DB_USERNAME`: TiDB username
     - `DB_PASSWORD`: TiDB password
     - `JWT_SECRET`: Random 256-bit string
     - `WEBHOOK_SECRET`: Random secret string
     - `TMDB_API_KEY`: *(Optional)* Your TMDB API key

---

## 🧪 Testing

TicketRush has comprehensive automated test coverage covering concurrency, security, and domain rules:
```bash
./mvnw test
```
- `BookingConcurrencyIntegrationTest`: 50 concurrent threads racing for the same seat.
- `HoldTtlExpiryIntegrationTest`: Verifies scheduled task releases seats past 5-minute TTL.
- `WebhookSignatureTest`: Validates HMAC-SHA256 signature tampering and replay attack defense.
- `AuthSecurityTest`: Verifies JWT issuance, expiration, and role rejection (403 Forbidden).

---

## 📜 License
Distributed under the MIT License. See `LICENSE` for more information.
