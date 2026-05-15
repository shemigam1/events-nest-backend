# EventsNest — Backend API

A full-lifecycle event management platform built with Spring Boot. EventsNest covers ticketing, organizer tools, manager panels, vendor marketplace, contracts with escrow, and an intelligence layer — across a six-milestone roadmap.

---

## Features

| Module | Description |
|--------|-------------|
| **Auth** | JWT-based registration, login, and token refresh |
| **Events** | Create, manage, and discover events with cover images |
| **Ticketing** | Ticket tiers, capacity management, and QR-code issuance |
| **Bookings** | End-to-end booking flow with payment gateway integration |
| **Check-in** | QR code and short-code scanning for event entry |
| **Organizer Console** | Event analytics, ratings, member management |
| **Manager Panel** | Managers assigned by organizers to help run events |
| **Vendor Marketplace** | Vendor discovery, inquiries, and in-app chat negotiation |
| **Contracts & Escrow** | Milestone-based contracts with escrow-protected payments |
| **Budget Tracking** | Per-event budget and expense management |
| **Guest List / RSVP** | Guest list management and RSVP flow |
| **Chat** | Real-time WebSocket (STOMP) messaging for vendor negotiation |
| **Notifications** | Async email and push notifications via Kafka consumers |
| **Admin Panel** | Admin invitation, event approval/rejection |

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Java 21 |
| Framework | Spring Boot 4.x |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Messaging | Apache Kafka |
| Cache | Redis (Lettuce) |
| Real-time | WebSocket (STOMP) |
| Payments | CBN-licensed payment gateway (HMAC-verified callbacks) |
| Storage | Local filesystem (dev) / AWS S3 (prod) |
| Auth | JWT (HS512, `java-jwt`) |
| API Docs | SpringDoc OpenAPI 3.0 (Swagger UI) |
| Metrics | Micrometer + Prometheus |
| Containerisation | Docker (multi-stage, `eclipse-temurin:25`) |

---

## Prerequisites

- **Java 21+** — `java -version`
- **Maven 3.9+** — `mvn -version` (or use the included `./mvnw`)
- **PostgreSQL 16** running locally with a database named `events_nest_db`
- **Redis** running on `localhost:6379`
- **Apache Kafka** broker accessible on `localhost:9094`

For a zero-setup local run, start the infrastructure with Docker Compose (see below) and run the app with Maven.

---

## Quick Start

### 1. Clone and set up environment

```bash
git clone https://github.com/your-org/events-nest-server.git
cd events-nest-server

# Copy the example env file and fill in required values
cp env.example .env
```

Open `.env` and set at minimum:

```env
JWT_SIGNING_KEY=<at-least-32-char-random-string>
ADMIN_PASSWORD=<your-admin-password>
```

### 2. Start infrastructure (PostgreSQL, Redis, Kafka)

```bash
# If you have a compose.yaml / docker-compose.yaml for the infra stack:
docker compose up -d postgres redis broker
```

If you don't have a compose file, start each service manually or use managed instances.

### 3. Create the database

```bash
psql -U postgres -c "CREATE DATABASE events_nest_db;"
```

### 4. Run the application

```bash
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`.

---

## Environment Variables

All variables below are read from the system environment or a `.env` file in the project root. Copy `env.example` and fill in values — never commit `.env`.

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `JWT_SIGNING_KEY` | **Yes** | — | HMAC-SHA256 signing key (min 32 chars) |
| `ADMIN_PASSWORD` | **Yes** | — | Password for the bootstrap admin account |
| `ADMIN_EMAIL` | No | `admin@eventsnest.com` | Bootstrap admin email |
| `FRONTEND_URL` | No | `http://localhost:5173` | Used in transactional email deep links |
| `MAIL_PROVIDER` | No | `brevo` | `brevo` or `gmail` |
| `BREVO_API_KEY` | No | — | Required when `MAIL_PROVIDER=brevo` |
| `GMAIL_USERNAME` | No | — | Required when `MAIL_PROVIDER=gmail` |
| `GMAIL_APP_PASSWORD` | No | — | Required when `MAIL_PROVIDER=gmail` |
| `APP_STORAGE_TYPE` | No | `local` | `local` or `s3` |
| `APP_STORAGE_S3_BUCKET` | No | — | S3 bucket name (when `APP_STORAGE_TYPE=s3`) |
| `REDIS_HOST` | No | `localhost` | Redis host |
| `REDIS_PORT` | No | `6379` | Redis port |
| `KAFKA_BOOTSTRAP_SERVERS` | No | `localhost:9094` | Kafka bootstrap servers |
| `APP_SEEDER_ENABLED` | No | `false` | Set to `true` to seed demo data on startup |

---

## API Documentation (Swagger UI)

Once the application is running, the interactive API explorer is available at:

```
http://localhost:8080/swagger-ui.html
```

The OpenAPI JSON spec is at:

```
http://localhost:8080/v3/api-docs
```

### Authenticating in Swagger UI

1. Call `POST /api/v1/auth/login` with your credentials.
2. Copy the `accessToken` from the response.
3. Click the **Authorize** button at the top of the Swagger UI page.
4. Paste the token into the **bearerAuth (HTTP, Bearer)** field and click **Authorize**.

All subsequent calls from Swagger UI will include the `Authorization: Bearer <token>` header automatically.

---

## Running Tests

```bash
# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=BookingServiceTest
```

Tests use an H2 in-memory database (PostgreSQL compatibility mode) and mock out Kafka, Redis, and Mail — no external services required. Every `@SpringBootTest` class must import `IntegrationTestConfig` to ensure schema isolation.

---

## Building the Docker Image

```bash
docker build -t events-nest-server:latest .
```

The multi-stage Dockerfile uses `eclipse-temurin:25-jdk` for the build and `eclipse-temurin:25-jre` for the runtime image, running as a non-root `spring` user.

### Running the container

```bash
docker run -p 8080:8080 \
  -e JWT_SIGNING_KEY=<key> \
  -e ADMIN_PASSWORD=<password> \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/events_nest_db \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=<password> \
  -e REDIS_HOST=<host> \
  -e KAFKA_BOOTSTRAP_SERVERS=<host>:9092 \
  events-nest-server:latest
```

---

## Project Structure

```
src/
├── main/
│   ├── java/group/moniepoint/eventsnestserver/
│   │   ├── admin/          # Admin panel
│   │   ├── auth/           # JWT auth + /me profile
│   │   ├── bookings/       # Booking lifecycle
│   │   ├── budget/         # Event budget tracking
│   │   ├── calendar/       # Google Calendar integration
│   │   ├── chat/           # WebSocket STOMP messaging
│   │   ├── checkin/        # QR code / short-code check-in
│   │   ├── comments/       # Social comments
│   │   ├── config/         # Spring configuration (OpenAPI, cache, etc.)
│   │   ├── contracts/      # Vendor contracts + escrow
│   │   ├── events/         # Event management + analytics
│   │   ├── guestlist/      # Guest list + RSVP
│   │   ├── manager/        # Manager panel
│   │   ├── notifications/  # Async notification system
│   │   ├── payments/       # Payment gateway
│   │   ├── programme/      # Event agenda
│   │   ├── ratings/        # Post-event ratings
│   │   ├── security/       # JWT filter + rate limiting
│   │   ├── seed/           # Dev/demo data seeders
│   │   ├── sse/            # Server-sent events
│   │   ├── storage/        # Local / S3 file storage
│   │   ├── tickets/        # Ticket management
│   │   ├── tiers/          # Ticket tier configuration
│   │   └── vendor/         # Vendor marketplace + inquiries
│   └── resources/
│       ├── application.properties
│       ├── db/migration/   # Flyway SQL migrations
│       └── templates/      # Thymeleaf email templates
└── test/
    ├── java/               # Unit + integration tests
    └── resources/
        └── application-test.properties
```

---

## API Overview

| Base Path | Module |
|-----------|--------|
| `POST /api/v1/auth/**` | Registration, login, token refresh |
| `GET/POST /api/v1/events/**` | Event CRUD (public reads, auth writes) |
| `GET/POST /api/v1/events/{id}/tiers` | Ticket tiers |
| `POST /api/v1/bookings/**` | Create and manage bookings |
| `POST /api/v1/payments/**` | Payment initiation and gateway callback |
| `POST /api/v1/events/{id}/checkin` | Check-in scan (no auth) |
| `GET/POST /api/v1/organizer/**` | Organizer console |
| `GET/POST /api/v1/manager/**` | Manager panel |
| `GET/POST /api/v1/chat/**` | Chat conversations |
| `GET/POST /api/v1/contracts/**` | Vendor contracts |
| `GET/POST /api/v1/contracts/{id}/escrow` | Escrow management |
| `GET/POST /api/v1/me/**` | User profile + notifications |
| `GET/POST /api/v1/admin/**` | Admin operations |
| `GET /api/v1/sse/**` | Server-sent events stream |

Full interactive documentation: `http://localhost:8080/swagger-ui.html`

---

## Architecture

The backend is a **Spring Boot monolith** (D8 — monolith-first until load evidence demands extraction). Key architectural decisions:

- **Two platform roles only** — `USER` and `ADMIN` on the JWT. Event-scoped roles (`ORGANIZER`, `ATTENDEE`, `MANAGER`, `VENDOR`, `CHECKIN_STAFF`) live in the `event_memberships` table and are fetched per-request from the DB (never from the JWT).
- **Kafka for all notifications** — email, push, and SMS are sent asynchronously by Kafka consumers. Requests never block on notification delivery.
- **Optimistic locking** — `@Version` on `TicketTier.availableCapacity` and `Booking` prevents overbooking under concurrent load without row locks (D6).
- **Payment gateway** — HMAC-SHA512 verified callbacks + `UNIQUE` constraint on `payment_gateway_ref` prevent replay and double-processing (D7).
- **Escrow-protected contracts** — Vendor payments held until each milestone is confirmed by the organizer (D3).

See [`docs/DESIGN_DECISIONS_MASTER.md`](docs/DESIGN_DECISIONS_MASTER.md) for the full architecture guide.

---

## Documentation

| Document | Description |
|----------|-------------|
| [`docs/DESIGN_DECISIONS_MASTER.md`](docs/DESIGN_DECISIONS_MASTER.md) | Architecture overview — start here |
| [`docs/DESIGN_DECISIONS.md`](docs/DESIGN_DECISIONS.md) | Deep rationale for each decision (D1–D9) |
| [`docs/DATA_MODEL.md`](docs/DATA_MODEL.md) | Database schema, indexes, constraints |
| [`docs/API_PATTERNS.md`](docs/API_PATTERNS.md) | Reusable endpoint code patterns |
| [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) | AWS EKS + RDS deployment guide |
| [`docs/CALENDAR_INTEGRATION.md`](docs/CALENDAR_INTEGRATION.md) | Google Calendar integration |

---

## Health & Metrics

| Endpoint | Description |
|----------|-------------|
| `GET /actuator/health` | Aggregate health (DB, Kafka, Redis) |
| `GET /actuator/health/liveness` | Liveness probe |
| `GET /actuator/health/readiness` | Readiness probe |
| `GET /actuator/prometheus` | Prometheus metrics scrape endpoint |

---

**Built for:** Moniepoint DreamDev Bootcamp Capstone
**Stack:** Spring Boot 4 · PostgreSQL 16 · Apache Kafka · Redis · Docker
