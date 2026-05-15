# EventsNest — Master Architecture & Design Decisions

**Version:** 5.0 | **Last Updated:** May 2026 | **Status:** LOCKED

> This is the single authoritative reference for EventsNest's architecture. All code must comply.
> Deviations require an RFC (pull request with detailed justification). Do not maintain parallel
> design documents — this file supersedes `DESIGN_DECISIONS.md`, `design-decisions.md`,
> `design-decisions-v2.md`, and `caching-and-rate-limiting.md`.

---

## Table of Contents

1. [System Vision](#system-vision)
2. [Nine Core Decisions (D1–D9)](#nine-core-decisions)
3. [Implementation Decisions (§1–§36)](#implementation-decisions)
4. [Scalability](#scalability)
5. [Caching Strategy](#caching-strategy)
6. [Rate Limiting Strategy](#rate-limiting-strategy)
7. [Implementation Patterns](#implementation-patterns)
8. [Data Integrity](#data-integrity)
9. [HTTP Status Codes](#http-status-codes)
10. [Security](#security)
11. [Testing](#testing)
12. [RFC Policy](#rfc-policy)
13. [What Cannot Change](#what-cannot-change)

---

## System Vision

EventsNest is a full-lifecycle event management platform for the Nigerian market. It serves five actor types whose concerns must not bleed into each other:

| Actor | What they do |
|---|---|
| **Attendee** | Browse, book, attend, and review events |
| **Organizer** | Create, configure, and run events end-to-end |
| **Event Manager / Planner** | Hired by organizers to manage event operations; coordinate vendors and staff |
| **Vendor** | Service providers (caterers, AV, photographers, venues) discoverable in the marketplace |
| **Platform Admin** | Moderate content, approve events, manage platform health |

**Tech Stack:** Spring Boot 3 · PostgreSQL 16 · Apache Kafka · Redis · Docker

**Full Roadmap:**
```
M1  ── Core Ticketing (done)
M2  ── Organizer Console, Guest List, Programme, Ratings, Analytics (done)
M3  ── Event Manager Panel (planned)
M4  ── Vendor + Venue Marketplaces (planned)
M5  ── Budget Tracker (planned)
M6  ── Intelligence Layer (planned)
```

---

## Nine Core Decisions

These nine decisions define the system. Violating any requires an RFC and likely a major rewrite.

---

### D1: Two Platform Roles Only — USER and ADMIN (LOCKED)

**Decision:** `users.role` is restricted to `USER` and `ADMIN`. No `VENDOR` or `EVENT_MANAGER` platform roles.

**The One Email Problem:**
A photographer named Alex has multiple identities across the platform: attends conferences (ATTENDEE), organizes workshops (ORGANIZER), provides photography services (VENDOR), and manages logistics (MANAGER). A system with platform-level VENDOR and EVENT_MANAGER roles would require Alex to maintain four separate accounts with four different emails — creating notification fragmentation, password sprawl, billing confusion, and UX nightmares.

**Solution — Event-Scoped Membership:**
```sql
-- users table (minimal)
id, email, password, platform_role (USER)

-- event_memberships table (per-event)
user_id, event_id, role (ATTENDEE, ORGANIZER, VENDOR, MANAGER, CHECKIN_STAFF)
```

At runtime: "For event X, Alex has role ORGANIZER. For event Y, Alex has role VENDOR."

```java
// Platform role (JWT)
enum PlatformRole { USER, ADMIN }

// Event-scoped roles (database)
enum EventRole { ORGANIZER, ATTENDEE, MANAGER, VENDOR, CHECKIN_STAFF }
```

**Tradeoffs:**
- Upside: one account, one password, one email, unified notifications
- Downside: dashboard queries need extra DB lookup for event roles (mitigated by Redis caching)

**When locked:** PRD v3. Never changing.

---

### D2: No Admin Gate for Vendor or Manager Roles (LOCKED)

**Decision:** Organizers (not admins) directly engage vendors and managers. No approval queue.

**The Approval Bottleneck:** Admin approval creates 1-2 day delays. Organizers are accountable and qualified to decide.

**Solution:**
```http
POST /organizer/events/{eventId}/managers
{ "userEmail": "manager@example.com" }
```
Creates `EventMembership(role=MANAGER)` immediately. No approval step.

**Quality Control Without Approval:**
1. **Ratings system** — post-event, attendees rate vendors; bad vendors accumulate low scores
2. **Escrow protection** — funds held until work confirmed; vendor fraud is self-limiting
3. **Organizer accountability** — bad hires hurt their events

**Tradeoffs:**
- Upside: zero friction engagement, full organizer control
- Downside: platform cannot prevent bad vendors (mitigated by escrow + ratings)

**When locked:** PRD v3. Core to marketplace framing.

---

### D3: Escrow as the Financial Safety Mechanism (LOCKED)

**Decision:** Funds held in escrow after contract signing. Released milestone-by-milestone on organizer confirmation.

**The Trust Problem — three models, all unsafe without escrow:**
- Pay upfront: vendor knows funds exist, but organizer at risk if vendor disappears
- Pay after delivery: organizer only pays if vendor delivers, but vendor at risk if organizer refuses
- **Pay per milestone (escrow):** both parties protected; platform arbitrates disputes

**Escrow Flow:**
```
Contract signed → payment gateway holds full amount in escrow
Vendor completes milestone 1 → Organizer marks complete
System releases milestone 1 payment to vendor
...
All milestones complete → Contract COMPLETED, vendor paid in full
```

**Regulatory:** CBN licensing required for production escrow. Current implementation uses payment gateway as intermediary.

**When locked:** PRD v4 (M5 planning). Foundational for vendor marketplace (M4-M5).

---

### D4: Chat-First Vendor and Manager Discovery (LOCKED)

**Decision:** Vendor negotiation happens via in-app WebSocket chat before any contract is drafted.

**Why:** Email negotiation is opaque, slow, and unarchived. In-app chat creates a full audit trail, enables real-time negotiation, generates training data for M6 intelligence layer, and provides dispute resolution evidence.

**Conversation Scopes:**

| Scope | Participants | Trigger |
|---|---|---|
| `EVENT_STAFF` | Organizer + all EventManagers | Event creation |
| `VENDOR_INQUIRY` | Organizer + Vendor | Vendor inquiry created |
| `EVENT_MANAGER_DIRECT` | Organizer + Event Manager | Manager assigned |

**Kafka Event:** `chat.message.sent` fired after every persisted message.

**Flow:**
```
Organizer opens inquiry → VENDOR_INQUIRY conversation created
Vendor joins chat → Both parties negotiate in-app
Full thread archived, timestamped, never lost
Organizer drafts contract → References chat thread
```

**When locked:** PRD v4. Unlocks M4 vendor marketplace.

---

### D5: Event-Scoped Membership Model (LOCKED)

**Decision:** All authorization is per-event. Roles fetched from `event_memberships` at request time, never cached in JWT.

**The Stale Permission Problem:**
If roles were in JWT: organizer revokes Alex's manager access, but Alex's JWT is still valid for 15 minutes after revocation. He can still modify the event during that window.

**Solution — Runtime Resolution:**
```java
// Every event endpoint must do this:
membershipService.ensureRole(userId, eventId, EventRole.ORGANIZER)
    .orElseThrow(() -> new ForbiddenException("Not authorized"));
```

Permission changes take effect immediately. No JWT expiry window.

```sql
SELECT * FROM event_memberships 
WHERE user_id=? AND event_id=? AND role=? AND left_at IS NULL
```

**Tradeoffs:**
- Upside: real-time permission accuracy, can revoke mid-session
- Downside: extra DB query per request (indexed, minimal cost; Redis caches membership lookups)

**When locked:** PRD v3. Core authorization pattern.

---

### D6: Optimistic Locking for Capacity and Payment (LOCKED)

**Decision:** `@Version` field on `ticket_tiers` and `bookings`. On conflict, return HTTP 409 and let client retry.

**The Overbooking Problem:**
Event has 1 ticket remaining. Two users book simultaneously.

With pessimistic locking (row lock): ~10-100x slower under load due to lock contention.

With optimistic locking (`@Version`):
```
Time 1: User A reads version=5
Time 1: User B reads version=5
Time 2: User A UPDATE → version=6 ✓
Time 3: User B UPDATE → version=6 WHERE version=5 ✗ → OptimisticLockingFailureException → HTTP 409
         → Client retries, gets "capacity exhausted" error
```

**Payment Deduplication:** Same pattern prevents duplicate payment gateway callback processing. Webhook retry after timeout sees PAID and the idempotency short-circuit in `finalizeBookingPayment` returns 200.

**When locked:** PRD v1 (M1). Non-negotiable for capacity management.

---

### D7: Payment Gateway Integration (LOCKED)

**Decision:** All money movement (bookings, escrow, payouts) flows through a single HMAC-verified payment gateway.

**Why a single gateway:**
- CBN-licensed, operating legally in Nigeria
- Bank transfer (USSD/bank-to-bank) support — dominant payment method in Nigeria (~90% of users)
- HMAC-verified webhooks (SHA-512) with replay protection
- Merchant verification and KYC built-in

**Callback Flow:**
```
1. Client books ticket → payment initiated
2. Client navigates to payment gateway checkout
3. Gateway processes payment
4. Gateway calls POST /payments/webhook (HMAC-verified)
5. Server marks booking CONFIRMED, issues tickets, fires Kafka event
6. (If webhook delayed) Client polls GET /payments/verify/{ref}
```

**Idempotency:** `payment_gateway_ref` UNIQUE constraint prevents double-processing.

**Tradeoffs:**
- Upside: battle-tested, regulatory compliance, USSD support
- Downside: gateway dependency; switching providers requires API rewrite; ~2.5% + fixed fee per transaction

**When locked:** PRD v1 (M1). Integrated in M3.

---

### D8: Monolith-First, Extract on Evidence (LOCKED)

**Decision:** Single Spring Boot JAR. Extract only when load evidence demands it. Python FastAPI (M6 ML inference) is the only planned extraction.

**Why:** Microservices add network latency, distributed transactions, and operational overhead without evidence of load needing it.

**Ports & Adapters (from Day 1):**
```
domain/
  → Booking (core logic, no framework dependencies)

application/
  → BookingService (orchestrates ports)

adapter/
  → rest/         → BookingController (REST)
  → persistence/  → BookingRepository (JPA)
  → kafka/        → BookingEventPublisher
  → payment/      → PaymentGatewayAdapter  ← can be swapped
```

**Extraction Decision Table:**

| Module | Extraction trigger |
|---|---|
| Check-in | Check-in burst load starves booking latency (port-isolated already) |
| Chat / Messaging | WebSocket connection count exceeds JVM thread budget |
| Marketplace search | Elasticsearch queries slow the operational DB |
| Intelligence / ML | Python-based inference; not feasible in the JVM |

**Tradeoffs:**
- Upside: simpler deployment, single ACID DB, easier debugging, faster time to market
- Downside: scaling limited by monolith bottlenecks

**When locked:** PRD v4 (M3+). Will reassess end of M5.

---

### D9: Milestone Ordering Rationale (LOCKED)

**Decision:** Roadmap sequenced so each milestone unblocks the next.

| Milestone | Core | Why It Must Come Before Next |
|---|---|---|
| **M1** | Ticketing | All other features depend on booking foundation |
| **M2** | Organizer Console, Ratings | Ratings give quality signal before D2 (no admin gate) |
| **M3** | Manager Panel | Establishes MANAGER role model that vendors reuse |
| **M4** | Vendor Marketplace, Chat | Chat is the negotiation surface; contracts reference chat threads |
| **M5** | Contracts, Escrow, Budget | Contracts must exist before escrow; budget tied to contract signing |
| **M6** | Intelligence Layer (ML) | ML requires historical data from M1-M5 |

**Sequential Dependencies:**
```
M1 (Ticketing)
  ↓ provides booking foundation
M2 (Organizer Console)
  ↓ ratings needed before frictionless vendor engagement
M3 (Manager Panel)
  ↓ establishes MANAGER role model
M4 (Vendor Chat & Inquiry)
  ↓ chat threads referenced in contracts
M5 (Contracts & Escrow)
  ↓ historical data for ML training
M6 (Intelligence Layer)
```

**When locked:** PRD v4. Changes require explicit RFC.

---

## Implementation Decisions

These 36 decisions cover implementation-level choices below the architectural level.

---

### §1. Overall Architecture — Level 1 Monolith

Single deployable Spring Boot JAR. Extraction happens only when load evidence demands it. The check-in module specifically follows port-and-adapter constraints from day one (`TicketLookupPort` interface instead of importing `TicketRepository` directly) so it can be extracted without rewriting business logic.

---

### §2. Role Design — Mutually Exclusive USER and ADMIN

`Role.USER` and `Role.ADMIN` are mutually exclusive. Admins are platform moderators, not platform users — they don't book tickets or create events. "Organizer" is NOT a system role; any `USER` who creates an event gets `EventRole.ORGANIZER` membership.

**v2 update — `EVENT_MANAGER` and `VENDOR` as system roles:**

| Role | Access |
|---|---|
| `USER` | Attendee portal — browse, book, review |
| `ORGANIZER` | Not a system role — any USER with an event gets EventRole.ORGANIZER |
| `EVENT_MANAGER` | Manager panel — operates across multiple events; dashboard shows all assigned events |
| `VENDOR` | Vendor portal — catalogue, bookings, contracts, payments (entirely separate from attendee experience) |
| `ADMIN` | Platform moderation — cannot hold other roles simultaneously |

---

### §3. Admin Invitation Flow — Token-Based

New admins are created via token-based invitation (`POST /api/v1/admin/invite`), not by promoting existing users:
1. Existing admin sends invite → `AdminInvitation` record with UUID token, 7-day expiry
2. Invitee receives email with registration link containing token
3. Invitee completes registration → new `User` with `Role.ADMIN` created
4. Invitation marked used — cannot be reused

Promoting a `USER` to `ADMIN` would lock them out of the user portal (see §2), hence the separate invitation flow.

---

### §4. Check-In Staff — Invitation Tokens, Not User Accounts

Check-in staff do not have `User` accounts. Authorization at the scanner is via per-event invitation tokens (`ckin_<24-char-nanoid>`).

- No password management or account sprawl for casual one-event staff
- Tokens scoped to a single event — a leaked token grants only event-bound access
- Easy revocation: organizer deletes the invite row
- Raw token shown only once; SHA-256 hash stored in DB; raw token cleared from `email_jobs` after successful delivery
- Token expires automatically 24 hours after event ends

---

### §5. Check-In Architecture — Port-Isolated, Extraction-Ready

1. **Port isolation** — `CheckInServiceImpl` goes through `TicketLookupPort`, not `TicketRepository` directly
2. **Stateless services** — no in-memory state; any instance can serve any scan
3. **Kafka publish** — `ticket.checked-in` published after every successful scan
4. **Caffeine cache** — QR → ticket lookup cached 5 minutes, evicted on successful check-in
5. **Optimistic check-in** — `UPDATE tickets SET status = 'USED' WHERE id = ? AND status = 'VALID'`; returns 0 if already used

---

### §6. Check-In Window — Organizer-Controlled

Each event has a `checkInStartTime`. Check-in cannot begin before this time. Default is 2 hours before `startTime`. Organizers can override it. `checkInStartTime` must be before `startTime`.

---

### §7. Short Codes for Manual Entry

UUIDs (36 chars) are impractical for manual entry.

**`Events.code`** (8-char NanoID): auto-generated at creation; used to configure scanner app; included in staff invite deep link; resolvable via `GET /api/v1/events/code/{code}`.

**`Ticket.shortCode`** (8-char NanoID): fallback when QR cannot be scanned (cracked screen, bad lighting). Why not `seatNumber`: seat numbers are not secret — any bystander could memorize one. `shortCode` is random and non-guessable.

---

### §8. Check-In Staff Invite Email — Deep Link With Token in URL

```
{frontendUrl}/checkin?eventCode=AB12CD34&eventId=UUID&token=ckin_...
```

Token is already in the email body as plaintext — the URL does not increase attack surface. Token is scoped to one event. Frontend must call `history.replaceState` immediately after reading params to remove them from browser history.

---

### §9. Published Event Editing — EventEditRequest Approval Workflow

Organizers can edit a published event's description, but changes do not go live immediately. Stored as `EventEditRequest` (status: PENDING). The live event is untouched until admin approves.

Why a separate entity over `pending_*` columns: `EventEditProposedChanges` stored as JSON in one `TEXT` column; adding venue, dates, or any other editable field costs zero schema changes.

**Locked fields on published events:** `title`, `venue`, `startTime`, `endTime`, `checkInStartTime` — returns `EventFieldLockedException` if included in update.

---

### §10. Ticket Overbooking Protection — Optimistic Locking

`TicketTier` carries a `@Version` column. Capacity decrement and ticket issuance happen in one `@Transactional` method. `@UniqueConstraint(columnNames = {"tier_id", "seat_number"})` on `Ticket` is the last-resort database-level guard. See [D6](#d6-optimistic-locking-for-capacity-and-payment-locked).

---

### §11. Exception Naming — Domain-Specific Over Generic

`EventNotFoundException` is immediately clear in a stack trace; `ResourceNotFoundException` with a message string is not.

```
EventsNestException (400)
├── InvalidEventStateException (409)
│   ├── EventNotSubmittableException
│   ├── BookingNotCancellableException
│   ├── InsufficientTierCapacityException
│   ├── GuestListNotEnabledException
│   ├── ProgrammeNotEnabledException
│   └── RatingsNotEnabledException
└── UnauthorizedException (403)
    ├── NotEventOrganizerException
    └── BookingCancellationForbiddenException

ResourceNotFoundException (404)
├── EventNotFoundException
├── TicketNotFoundException
├── BookingNotFoundException
├── UserNotFoundException
├── VendorNotFoundException      (M4)
├── ContractNotFoundException    (M4)
└── BudgetNotFoundException      (M5)
```

---

### §12. Email Delivery — Outbox Pattern

Emails are delivered asynchronously via a database-backed job queue. The `EmailJobPoller` retries on a 30-second schedule. Requests never block on notification delivery.

**Why outbox over synchronous:** SMTP/HTTP calls can take seconds; blocking the request thread risks connection pool exhaustion. If the mail provider is down, synchronous delivery fails the entire operation.

**`EmailJobType` inventory:**

| Type | Trigger |
|---|---|
| `BOOKING_CONFIRMED` | Booking service |
| `EVENT_APPROVED` / `EVENT_REJECTED` | Admin service |
| `STAFF_INVITE` | CheckIn invite service |
| `GUEST_RSVP_INVITE` | Guest service (M2.2) |
| `RATING_REQUEST` | Rating scheduler (M2.3) |
| `VENDOR_INQUIRY` | Vendor marketplace (M4) |
| `CONTRACT_SIGNED` | Contract service (M4) |
| `PAYMENT_CONFIRMATION` | Payment gateway callback handler (M3+) |
| `BUDGET_ALERT` | Budget tracker (M5) |

**Dual-provider support:** `@ConditionalOnProperty(name = "mail.provider")` — set `MAIL_PROVIDER=gmail` to switch to Gmail SMTP without code changes.

---

### §13. Kafka — Event-Driven Audit and Notifications

| Topic | Publisher | Consumer |
|---|---|---|
| `booking.confirmed` | `BookingServiceImpl` | Email outbox, analytics |
| `ticket.checked-in` | `CheckInServiceImpl` | Analytics |
| `event.approved` | `AdminServiceImpl` | Email outbox |
| `event.rejected` | `AdminServiceImpl` | Email outbox |
| `chat.message.sent` | `ChatServiceImpl` (M3) | Push notification |
| `vendor.inquiry.created` | `MarketplaceServiceImpl` (M4) | Email outbox |
| `contract.signed` | `ContractServiceImpl` (M4) | Email outbox, budget tracker |
| `audit.events` | Multiple | Audit log |

**Why Kafka over direct service calls:** Booking service does not need to know about email. Consumers can be added or removed without touching the publisher. If a consumer is slow or down, it catches up via `auto-offset-reset=earliest`.

**Rules:**
- Publish AFTER transaction commits
- Consumers must be idempotent
- Always persist notifications for audit and replay
- Correlation ID propagated through Kafka via `CorrelationIdProducerInterceptor` (see §36)

---

### §14. Database — PostgreSQL 16

Switched from MySQL at M2.0 and now complete.

**PostgreSQL capabilities exploited:**

| Feature | Used by |
|---|---|
| Partial indexes | `WHERE status = 'VALID'` on tickets; `WHERE rsvp_token_hash IS NOT NULL` on guests |
| Native UUID type | All entity PKs |
| `ON CONFLICT DO NOTHING` | `TicketCheckInRepository.insertIfAbsent` |
| JSONB | `EventEditRequest.proposedChanges`; planned for vendor catalogue attributes |
| Full-text search (`tsvector`) | Planned for marketplace keyword search (M4) |
| Window functions | Planned for analytics dashboards (M6) |

---

### §15. Rate Limiting Strategy — Bucket4j + Redis

See full specification in [Rate Limiting Strategy](#rate-limiting-strategy).

---

### §16. Draft Event Visibility — Hidden From Public

`GET /api/v1/events/{id}` returns `404` for non-`PUBLISHED` events (`DRAFT`, `PENDING_APPROVAL`, `CANCELLED`).

Why 404 instead of 403: A 403 confirms the event exists. 404 prevents enumeration — an attacker cannot determine whether an ID is a draft or simply doesn't exist.

---

### §17. Organizer Self-Booking Prevention

An event organizer cannot book tickets to their own event. `BookingServiceImpl` checks for `EventRole.ORGANIZER` membership before processing and throws `UnauthorizedException` if found. An organizer booking would skew capacity data, distort revenue figures, and hold seats from real attendees.

---

### §18. Local Development Seeding

`DevDataSeeder` runs on startup under `@Profile("local")` (activated by `APP_PROFILE=local`). Seeds 30+ users with full data ecosystem. Idempotency check via `akin@devmail.com` before seeding. Never runs in production.

---

### §19. User ID Format — NanoID (12 chars)

`users.id` is `VARCHAR(12)` storing a NanoID generated in `@PrePersist`. UUIDs are 36 characters — bulky in URLs. NanoID at 12 characters with a 62-char alphabet gives ~72 bits of entropy, safe up to ~1 billion users. All other entity IDs (`events`, `bookings`, `tickets`, `ticket_tiers`, `event_memberships`) remain UUID.

---

### §20. UUID Generation — `@UuidGenerator`

Entities with UUID PKs use Hibernate's `@UuidGenerator`. `GenerationType.UUID` (JPA 3.1) is inconsistent in Hibernate 7 — the UUID value is sometimes not populated on the in-memory entity after `save()`. `@UuidGenerator` generates the UUID in Java before the INSERT.

---

### §21. `saveAndFlush()` for DB-Generated Values

Service methods that return `@CreationTimestamp`/`@UpdateTimestamp` fields use `saveAndFlush()`, not `save()`. Plain `save()` queues the write until transaction commit; the in-memory entity has null timestamps when the response is assembled. Applied to: `EventServiceImpl.createEvent / updateEvent`, `TicketTierServiceImpl.createTier`, `BookingServiceImpl.createBooking`.

---

### §22. N+1 Prevention — `@EntityGraph`

Repository methods that access lazy-loaded associations are annotated with `@EntityGraph(attributePaths = ...)` to fetch the relationship in a single SQL JOIN.

Applied to:
- `EventRespository.findById` → JOIN `createdBy`
- `BookingRepository.findById` → JOIN `event`, `tier`, `attendee`
- `TicketRepository.findByQrCode / findByShortCode` → JOIN `tier`, `tier.event`, `attendee`

---

### §23. Schema Management — Flyway Replaces `ddl-auto`

`ddl-auto=validate`. Flyway owns all schema changes via versioned migrations in `src/main/resources/db/migration`.

`validate` turns silent runtime failures into loud startup failures. `baseline-on-migrate=true` lets existing databases adopt Flyway without re-executing V1.

---

### §24. Cross-Field Validation — `HasStartEndTime` Interface

`@EndAfterStart` is a class-level constraint. Both `CreateEventRequest` and `UpdateEventRequest` implement `HasStartEndTime`; `EndAfterStartValidator` works against the interface. Class-level constraints produce `getGlobalErrors()` entries; the `MethodArgumentNotValidException` handler collects both field and global errors.

---

### §25. Manual DTO Mapping — `toXxxResponse()` Helpers

Where ModelMapper cannot auto-map a field (entity type differs from DTO type), mapping is done manually in a private helper. ModelMapper configured to skip those fields; the helper sets them explicitly.

Example: `Events.createdBy` is a `User` entity; `EventResponse.createdBy` is a `String`. Same pattern in `BookingServiceImpl.toBookingResponse`, `TicketServiceImpl.toTicketResponse`.

---

### §26. Response Envelope Philosophy

Mutations (`POST`, `PATCH`, `DELETE` that return data) wrap the response in `EventsNestResponse<T>` with `success`, `message`, `data`, `errors`. Pure query endpoints (`GET` returning lists) return the bare list. Wrapping every list in `{ "success": true, "data": [...] }` adds noise — the HTTP status already communicates success.

---

### §27. URL Versioning — `/api/v1/` Prefix

All routes under `/api/v1/`. Versioned from day one so backward-incompatible changes can be introduced on `/api/v2/` without breaking existing clients.

---

### §28. Auto-Organizer on Event Creation

`EventServiceImpl.createEvent` inserts an `EventMembership` row with `role = ORGANIZER` in the same `@Transactional` boundary as the event save. Both writes succeed or both roll back.

---

### §29. Idempotent ATTENDEE Membership Insertion

`BookingServiceImpl.createBooking` checks `existsByEventsIdAndUserIdAndRole(..., ATTENDEE)` before inserting the ATTENDEE membership. A second booking by the same user for the same event does not create a duplicate row. The `UNIQUE(user_id, event_id, role)` constraint is the last-resort guard.

---

### §30. Seat Assignment Algorithm

Seat labels derived algorithmically at booking time:
```
index    = countOfNonRefundedTickets + offset
row      = floor(index / seatsPerRow)
position = (index % seatsPerRow) + 1
label    = rowPrefix + (row + 1) + "-" + position
```
Pre-generating seats would INSERT `rowCount × seatsPerRow` rows at event creation time. Known edge case: cancelled ticket's seat number consumed permanently (seat labels advance monotonically); accepted for v1.

---

### §31. Payment Gateway Integration

See [D7](#d7-payment-gateway-integration-locked) for full flow. `payment_status` enum: `PENDING_PAYMENT`, `PAID`, `FAILED`, `REFUNDED`. `payment_gateway_ref` UNIQUE constraint prevents double-processing. Idempotent `finalizeBookingPayment` and `markBookingFailed` methods.

---

### §32. Planned Complementary Stores

| Store | Purpose | Trigger |
|---|---|---|
| **Redis** | QR cache (replaces Caffeine), distributed locks, rate limiting | When running more than one instance |
| **Read replica** | Public browse/list endpoints | When browse traffic competes with write latency |
| **ClickHouse** | Admin analytics dashboard | When admin queries slow the operational DB |
| **Elasticsearch** | Free-text event search | When search becomes a primary feature |

---

### §33. Tiers Created Inline With Event

`CreateEventRequest` accepts an optional `tiers` array. When present, tiers are created in the same transaction as the event. Requiring a separate `POST /tiers` call for every tier is unnecessary friction. The separate tier endpoint still exists for post-creation changes.

---

### §34. Observability — Micrometer + Prometheus

Booking confirmations increment Micrometer counters (`eventsnest.bookings.confirmed`, `eventsnest.bookings.revenue`). Counter incremented only after Kafka event is published — counts fully-processed bookings, not partial writes.

Actuator: `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/prometheus`.

---

### §35. HikariCP Pool Tuning

```
maximumPoolSize=20   // ~200 concurrent check-in scans (each <100ms)
minimumIdle=5        // keep connections warm between low-traffic periods
maxLifetime=25min    // DB idle timeout is 30 min
idleTimeout=15min    // release unused connections
connectionTimeout=20000ms
```

Revisit when WebSocket connections are added (M3 chat) — WebSocket handlers hold threads differently from HTTP handlers.

---

### §36. Correlation ID Propagation Through Kafka

`CorrelationIdProducerInterceptor` stamps every outbound Kafka record with the current MDC `correlationId`. `CorrelationIdRecordInterceptor` restores it to MDC on the consumer side. Without this, a booking request and its downstream notification email are not linkable in logs.

---

### §37. Event Images — Mandatory, Cloud-Stored

Every event must have a cover image before submission for approval. Images stored in cloud object storage (AWS S3 or Cloudinary); `events.cover_image_url` stores the URL only.

Accepted formats: `image/jpeg`, `image/png`. MIME check + magic bytes validation. Max size: 5 MB. `EventImageRequiredException` thrown if `cover_image_url IS NULL` at submission.

`FileStorageService` interface with `S3FileStorageService` (prod) and `LocalFileStorageService` (local profile, saves to `./uploads/`).

---

### §38. Private / Invite-Only Events

Events have `visibility: EventVisibility { PUBLIC, PRIVATE }`. Private events not listed on public browse endpoint and return 404 to non-invited callers.

Private event flow: organizer creates event → adds guests via guest list → RSVP invite sent with event code → guest accepts → gains access to event detail and booking. The 8-char NanoID event code is the invite link (already non-guessable, already has a resolver endpoint).

---

### §39. Vendor Marketplace (M4)

Searchable directory of service vendors and venues. Vendor profiles include `business_name`, `category`, `location`, `state`, `portfolio_images` (JSONB), `starting_price`, `verified` (admin-set), `rating_avg`.

Discovery via: `GET /api/v1/vendors?category=CATERING&state=Lagos&minRating=4.0`

Search strategy: M4 launch uses PostgreSQL `ILIKE` + filters; M6 adds Elasticsearch for full-text description search.

Inquiry → conversation → contract flow:
```
POST /api/v1/vendors/{vendorId}/inquiries
  → Creates VendorInquiry record
  → Creates Conversation(scope=VENDOR_INQUIRY)
  → Publishes vendor.inquiry.created Kafka event
```

---

### §40. Vendor Contracts (M4)

`Contract` entity: `DRAFT → SENT → SIGNED → COMPLETED | CANCELLED`

Organizer creates contract from within a vendor inquiry conversation. Both parties sign in-app (timestamped DB record with user_id). Amount debited from event budget on SIGNED. Payment gateway payout on COMPLETED.

Anti-abuse for vendor ratings: a rating can only be submitted if the organizer has a COMPLETED contract with that vendor.

---

### §41. Budget Tracker (M5)

`event_budgets` + `budget_line_items` tables. Automatic line item creation on contract signing (`status=COMMITTED`), updated to `PAID` on contract COMPLETED.

Budget alerts via `@Scheduled` daily job: fires `BUDGET_ALERT` email when over-committed or 90% of budget spent.

Income tracking: ticket revenue from payment gateway reflected as income, giving organizers a single P&L view per event.

---

### §42. Intelligence Layer (M6)

Python FastAPI microservice hosts ML models. Spring Boot monolith calls it over HTTP for predictions. This is the only justified extraction from the monolith — Python is a hard constraint for ML, not a scalability concern.

```
Spring Boot (monolith)
  ↓ HTTP (internal)
Python FastAPI (intelligence-service)
  ↓ reads from
PostgreSQL read replica  +  ClickHouse (analytics events)
```

ClickHouse ingests domain events from Kafka for time-series and percentile aggregation queries that would be expensive on PostgreSQL.

Planned capabilities: smart vendor matching, optimal event timing, budget forecasting, attendee churn prediction, rating sentiment analysis.

---

## Scalability

EventsNest is designed to scale gradually as load evidence accumulates. The following plan describes both current mechanisms and the scaling path.

---

### Current Capacity

A single Spring Boot instance with the current configuration can handle:
- ~200 concurrent check-in scans (20 HikariCP connections × <100ms per scan)
- Hundreds of req/sec on public browse/list endpoints (cached responses at <1ms)
- Event-driven notifications at any volume (Kafka consumers are independent, backpressure-safe)

---

### Horizontal Scaling

The monolith is stateless — no in-process state that prevents multiple instances.

**Redis is the prerequisite for multi-instance deployment:**
- Caffeine cache (in-process) is lost on restart and not shared across instances
- Bucket4j in-process rate limit counters reset on restart and are not coordinated
- Redis replaces both: `RedisCacheManager` + Bucket4j Redis backend are already distributed-aware
- WebSocket chat requires a pub-sub broker to route messages across instances; Redis pub-sub serves this role

```
Redis
├── Cache store     →  RedisCacheManager (replaces CaffeineCacheManager)
├── Rate limiting   →  Bucket4j Redis backend (per-IP / per-user buckets)
└── Pub-sub broker  →  Spring WebSocket STOMP message broker (chat)
```

Redis is introduced when the first of these conditions is met:
1. More than one app instance deployed
2. WebSocket chat requires cross-instance message routing
3. Caffeine cache hit rate drops below 60%

---

### Optimistic Locking (D6)

`@Version` on `TicketTier.availableCapacity` and `Booking` prevents overbooking and duplicate payment processing under concurrent load without row locks. Optimistic locking is 10-100x faster than pessimistic locking under concurrent load because there is no lock contention.

---

### Caching Layer

Redis caching eliminates DB hits on the highest-frequency read paths:

| Cache | TTL | What it avoids |
|---|---|---|
| `public-events` (5 min) | Every unauthenticated browse request | Full table scan + join on every page load |
| `event-detail:{id}` (10 min) | Individual event detail pages | 2-3 joins per request |
| `event-config:{id}` (15 min) | Config fetched on every org/manager request | One DB hit per request per event |
| `event-programme:{id}` (15 min) | Programme list on public event pages | Two queries (items + config check) |
| `user-by-email:{email}` (10 min) | JWT filter runs on every authenticated request | One DB hit per API call |
| `vendor-marketplace` (5 min) | Aggregated ratings + completed-event counts | Two bulk queries across all verified vendors |
| `tickets-by-qr:{qrCode}` (5 min) | High-frequency check-in scan bursts | DB hit per scan |

---

### Kafka Async Decoupling

All notifications (email, push) are published to Kafka and consumed asynchronously. Requests never block on notification delivery, which means:
- API latency does not grow with notification load
- Notification consumers can scale independently
- Failed consumers catch up via `auto-offset-reset=earliest` without data loss

---

### Database Scaling Path

| Trigger | Action |
|---|---|
| Browse traffic competing with write latency | Add a PostgreSQL read replica; route `GET /events` and other read-heavy endpoints there |
| Admin analytics queries slowing the operational DB | Introduce ClickHouse for the analytics pipeline; Kafka consumer writes domain events to ClickHouse |
| Vendor/event free-text search queries slow | Introduce Elasticsearch; sync via Debezium CDC or Kafka consumer |

---

### Kubernetes Horizontal Pod Autoscaler (HPA)

The GitHub Actions pipeline deploys to EC2 today. When load evidence justifies Kubernetes:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
spec:
  scaleTargetRef:
    name: events-nest-server
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          averageUtilization: 70
```

Prerequisites: Redis deployed (for distributed cache + rate limiting), sticky WebSocket sessions or STOMP broker over Redis pub-sub.

---

### Service Extraction Triggers (D8)

| Module | Current state | Extraction trigger |
|---|---|---|
| Check-in | Port-isolated in monolith | Check-in burst load starves booking latency |
| Chat / Messaging | WebSocket in monolith (M3) | WebSocket connection count exceeds JVM thread budget |
| Marketplace search | PostgreSQL ILIKE (M4) | Search queries slow operational DB reads |
| Intelligence / ML | Not yet built | Python inference is a hard constraint (M6) |

---

### Capacity Staleness Trade-Off

`availableCapacity` in cached event detail may be stale by up to 10 minutes (cache TTL). This is intentional: correctness is enforced at the write layer by `@Version` optimistic locking, not the read layer. The worst case is a user sees "24 seats left" when there are 22; they attempt a booking and either succeed or receive an accurate error. No overselling is possible regardless of cache staleness.

---

## Caching Strategy

### Stack

- **Spring Cache abstraction** (`@Cacheable`, `@CacheEvict`, `@Caching`) — annotations stay the same regardless of backend
- **RedisCacheManager** configured in `CacheConfig` with per-cache TTLs
- **Why Redis over Caffeine:** Caffeine is lost on restart and not shared across instances; Redis persists independently of the app lifecycle and works across horizontally scaled instances

---

### Cached Endpoints

#### `GET /api/v1/events` — `public-events`
**TTL:** 5 minutes | **Key:** single entry (full published list)

**Eviction triggers:** admin approves event, admin cancels published event, admin approves published-event update, organizer deletes event.

**Not evicted by:** `createEvent` (new events are always DRAFT, never appear until admin-approved), `submitForApproval`, `withdrawSubmission`.

**Reason:** Every unauthenticated visitor hits this endpoint. The list changes only on admin actions — infrequent writes, very high read volume.

---

#### `GET /api/v1/events/{id}` — `event-detail`
**TTL:** 10 minutes | **Key:** `eventId`

**Safety rule:** `PRIVATE` events are **never** written to this cache (`unless` SpEL condition). Their detail is always fetched from DB so the per-caller guest-list authorization check runs on every request.

**Eviction triggers:** admin approves/rejects/cancels event, admin approves pending description edit, organizer updates event metadata, a booking is created or cancelled (capacity changes), organizer deletes event.

**Capacity staleness trade-off:** See [Scalability — Capacity Staleness Trade-Off](#capacity-staleness-trade-off).

---

#### `GET /api/v1/events/{id}/config` — `event-config`
**TTL:** 15 minutes | **Key:** `eventId`

**Eviction triggers:** organizer updates event config (any module toggle), organizer deletes event.

**Note:** Config updates also evict `event-detail` (config is embedded in detail response) and `event-programme` (toggling `programmeEnabled` off must prevent stale programme list from being served).

---

#### `GET /api/v1/events/{id}/programme` — `event-programme`
**TTL:** 15 minutes | **Key:** `eventId`

**Eviction triggers:** organizer adds/updates/deletes a programme item, organizer updates event config, organizer deletes event.

**Reason:** Programme content is static once published. Two DB queries (fetch items + check config) on every public page load eliminated.

---

#### `GET /api/v1/vendors` — `vendor-marketplace`
**TTL:** 5 minutes | **Key:** `serviceType.toLowerCase()` when filtered; `"all"` otherwise

**Eviction triggers:** admin approves vendor verification, admin rejects vendor verification, organizer rates a vendor.

**Reason:** Response aggregates two bulk queries (avg rating + count, completed-event count) across every verified vendor.

---

#### `findByEmail` (internal) — `user-by-email`
**TTL:** 10 minutes | **Key:** `email`

**Eviction triggers:** admin enables or disables a user account.

**Reason:** Called on every authenticated request (JWT filter → load principal). The cached `User` object becomes stale only when `enabled` or `role` changes — eviction is targeted to exactly those writes.

---

#### QR → ticket lookup — `tickets-by-qr`
**TTL:** 5 minutes | **Key:** `qrCode`

**Eviction trigger:** after successful check-in (`markCheckedIn`).

**Reason:** A single ticket may be scanned multiple times at a busy check-in desk. Optimistic `UPDATE WHERE status = VALID` guarantees correctness even if cache is slightly stale.

---

### Endpoints NOT Cached

| Endpoint | Reason |
|---|---|
| `GET /api/v1/events/{id}` (PRIVATE) | Per-caller guest-list authorization must run on every request |
| `GET /api/v1/events/{id}/tiers` | `availableCapacity` changes with every booking; wrong number before checkout is a poor experience |
| `GET /api/v1/organizer/events` | Organizers create/edit/submit and expect to see changes immediately |
| `GET /api/v1/me/bookings` | Personal data that changes on every booking and cancellation |
| `GET /api/v1/me/tickets` | Personal, changes after check-in — a ticket showing `VALID` when `USED` is a trust issue |
| `GET /api/v1/admin/analytics` | Live aggregate data; stale analytics mislead operational decisions |
| All admin moderation lists | Admins make approval decisions from this data; stale lists risk double-approvals |
| All write endpoints (POST / PATCH / DELETE) | Never cached |

---

## Rate Limiting Strategy

### Stack

- **Bucket4j** with a Redis backend (`bucket4j-redis` integration)
- Implemented as a Spring `OncePerRequestFilter` — applied before controllers, after Spring Security
- Buckets keyed by **IP address** for unauthenticated endpoints, **user ID** for authenticated ones

**Why not an API Gateway:** This is a single-deployable monolith. An API Gateway solves routing and cross-service concerns that don't exist here. Bucket4j as a Spring filter achieves the same result at a fraction of operational overhead.

---

### Critical — Must Have

#### `POST /api/v1/auth/login`
**Limit:** 10 attempts / 15 min per IP

Primary brute-force and credential-stuffing vector. Slows automated tools to the point where the attack is not economically viable while allowing a legitimate user who misremembers their password multiple tries.

---

#### `POST /api/v1/auth/register`
**Limit:** 5 registrations / hour per IP

Unlimited registration enables bulk fake account creation — used to hoard tickets, spam events, or inflate user counts. 5 per hour is generous for any legitimate use case.

---

#### `POST /api/v1/auth/refresh`
**Limit:** 30 requests / 10 min per IP

A high refresh rate signals token replay attack or misbehaving client. 30 per 10 minutes accommodates normal SPA behavior (multiple tabs, background refresh).

---

#### `POST /api/v1/admin/invite/complete`
**Limit:** 5 attempts / hour per IP

Public endpoint (no JWT required) accepting an invite token. Without rate limiting, an attacker can enumerate tokens by brute force. 5 per hour per IP makes token guessing infeasible given `ckin_<24-char-nanoid>` token space.

---

#### `POST /api/v1/events/{id}/checkin`
**Limit:** 60 scans / min per IP

Only unauthenticated write endpoint. Staff at a check-in desk scanning QR codes fit comfortably within 60/min (1/sec). Automated attack probing random QR codes would be throttled.

---

### High — Should Have

#### `POST /api/v1/admin/invite`
**Limit:** 20 invites / hour per authenticated user

Each call triggers an email via Brevo. A compromised admin account could send thousands of phishing-style emails with EventsNest branding. 20 per hour contains the blast radius.

---

#### `POST /api/v1/events/{id}/bookings`
**Limit:** 10 bookings / min per authenticated user

Ticket-scalping bots book large numbers of seats across events rapidly. Keyed per user ID (not IP) since authenticated users are identified individually. Legitimate users booking for a group will never approach 10 per minute.

---

#### `POST /api/v1/events/{id}/checkin/invites`
**Limit:** 20 invites / hour per authenticated user

Same email-bombing concern as admin invite. 20 per hour accommodates large event staffing.

---

#### `POST /api/v1/events/{id}/cover-image`
**Limit:** 10 uploads / hour per user

---

#### `POST /api/v1/vendors/{id}/inquiries`
**Limit:** 20 / hour per user

---

#### `SEND /app/chat.send` (WebSocket)
**Limit:** 60 messages / min per user

---

#### `POST /api/v1/payments/webhook`
**Limit:** 200 / min per IP (gateway IPs only)

---

### Medium — Good to Have

#### `GET /api/v1/events`, `GET /api/v1/events/{id}`, `GET /api/v1/events/{id}/tiers`
**Limit:** 120 requests / min per IP

Public unauthenticated endpoints easiest to scrape. 120/min (2/sec) is imperceptible to humans and legitimate frontends but caps automated scrapers. These endpoints are also primary caching candidates — cached responses at <1ms offload most DB pressure regardless.

---

#### `PATCH /api/v1/events/{id}/submit`
**Limit:** 10 submissions / hour per authenticated user

Each submission creates work for the admin moderation queue. An organizer who can submit 100 times per hour could spam the review queue and bury legitimate submissions.

---

## Implementation Patterns

### Authorization Pattern (D5)

```java
@PostMapping("/events/{eventId}/update")
public ResponseEntity<?> updateEvent(
    @PathVariable UUID eventId,
    @RequestBody UpdateEventRequest req,
    @AuthenticationPrincipal UserDetails user) {

  UUID userId = UUID.fromString(user.getUsername());

  // REQUIRED: Check membership BEFORE business logic
  membershipService.ensureRole(userId, eventId, EventRole.ORGANIZER)
      .orElseThrow(() -> new ForbiddenException("Only organizers can update"));

  return ResponseEntity.ok(eventService.update(eventId, req));
}
```

**Rules:**
1. Extract userId from JWT
2. Call `ensureRole()` BEFORE business logic
3. Throw `ForbiddenException` on failure (HTTP 403)
4. Never use `@PreAuthorize` with event roles

---

### Optimistic Locking Pattern (D6)

```java
@Transactional
public Booking createBooking(CreateBookingRequest req) {
  TicketTier tier = tierRepository.findById(req.getTierId()).orElseThrow();

  if (tier.getAvailableCapacity() <= 0) {
    throw new ConflictException("No capacity");
  }

  tier.setAvailableCapacity(tier.getAvailableCapacity() - req.getQuantity());

  try {
    tierRepository.save(tier);
  } catch (OptimisticLockingFailureException e) {
    throw new ConflictException("Tier capacity changed, retry", e);
  }

  return bookingRepository.save(booking);
}
```

**Exception Handler:**
```java
@ExceptionHandler(OptimisticLockingFailureException.class)
public ResponseEntity<?> handleConflict(OptimisticLockingFailureException ex) {
  return ResponseEntity.status(409).body(new ErrorResponse(
      "Concurrent modification detected. Retry.",
      "CONFLICT",
      null
  ));
}
```

---

### Kafka Event Pattern (D4)

```java
@Transactional
public Booking confirmBooking(UUID bookingId) {
  Booking booking = repository.findById(bookingId).orElseThrow();
  booking.setStatus(BookingStatus.CONFIRMED);
  booking = repository.save(booking);

  // Publish AFTER transaction commits
  kafka.send("booking.confirmed", new BookingConfirmedEvent(
      booking.getId(), booking.getUserId(), booking.getEventId()));

  return booking;
}
```

**Consumer:**
```java
@KafkaListener(topics = "booking.confirmed", groupId = "notifications")
public void onBookingConfirmed(BookingConfirmedEvent event) {
  try {
    emailService.send(user.getEmail(), "Booking Confirmed", "...");
  } catch (Exception e) {
    log.error("Email failed", e);
  }
  notificationRepository.save(new PersistedNotification(...));
}
```

**Rules:** Publish after commit. Consumers must be idempotent. Always persist notifications for audit and replay.

---

### Payment Gateway Callback Pattern (D7)

```java
@PostMapping("/webhook")
public ResponseEntity<?> handleWebhook(
    @RequestBody String payload,
    @RequestHeader("X-Gateway-Signature") String signature) {

  // 1. Verify HMAC (never skip)
  if (!constantTimeEquals(signature, computeHmac(payload, GATEWAY_SECRET))) {
    return ResponseEntity.status(401).build();
  }

  // 2. Parse + check idempotency
  String ref = parseRef(payload);
  if (bookingService.hasProcessedRef(ref)) {
    return ResponseEntity.ok().build();
  }

  // 3. Process
  if (isPaid(payload)) {
    bookingService.finalizeBookingPayment(ref);
  } else {
    bookingService.markBookingFailed(ref, parseFailReason(payload));
  }

  return ResponseEntity.ok().build();
}
```

**Rules:** Always verify HMAC. Use constant-time comparison (prevent timing attacks). Check duplicate refs via UNIQUE constraint. Return 200 for all well-signed callbacks (retry-safe).

---

## Data Integrity

### Optimistic Locking (`@Version`)

| Entity | Column | Protects |
|---|---|---|
| `ticket_tiers` | `version` | Concurrent bookings (overbooking) |
| `bookings` | `version` | Concurrent payment callbacks (duplicate processing) |

### Unique Constraints

| Constraint | Purpose |
|---|---|
| `UNIQUE(user_id, event_id, role)` on `event_memberships` | Prevent duplicate memberships |
| `UNIQUE(tier_id, seat_number)` on `tickets` | No double-booking same seat |
| `UNIQUE(payment_gateway_ref)` on `bookings` | Prevent duplicate payment callbacks |

All enforced at DB layer AND service layer.

### Snapshot Fields

Never mutable after creation:
- `bookings.unit_price` — snapshotted at booking time (tier price changes don't affect existing orders)
- `tickets.qr_code`, `tickets.short_code` — check-in depends on immutability

---

## HTTP Status Codes

| Code | Use Case | Example |
|---|---|---|
| **200** | GET, PATCH success | Get event details |
| **201** | POST success (resource created) | Create booking |
| **400** | Validation failure | Invalid email, missing field |
| **401** | Missing/invalid JWT | Missing Authorization header |
| **403** | Valid JWT, insufficient role | USER updating ORGANIZER event |
| **404** | Resource not found | Event doesn't exist |
| **409** | Conflict (concurrent mutation, duplicate) | Overbooking, duplicate check-in |
| **422** | State violation | Cannot fund contract (not SIGNED yet) |
| **500** | Unexpected error | Kafka publish fails |

---

## Security

### Passwords
- Stored as BCrypt hashes (work factor ≥ 10)
- Never returned in API responses
- Never logged

### JWT
- Payload: `{ userId, email, role (platform-level only), iat, exp }`
- Signed with HS512
- Secret from environment variable (never hardcoded)
- **Never store event roles in JWT** — always fetch from DB at request time (D5)

### Check-In Tokens
- Format: `ckin_<24-char-nanoid>`
- SHA-256 hash stored in DB; raw token sent via email only
- Raw token cleared from `email_jobs` after successful delivery

### File Uploads
- MIME check + magic bytes validation
- Allowed: JPEG, PNG (images), CSV (exports)
- Max sizes: 5 MB (images), 10 MB (CSV)

### Webhook Verification
- HMAC-SHA512 (required, never skip)
- Constant-time string comparison (prevent timing attacks)
- UNIQUE constraint on `payment_gateway_ref` (prevent replay)

---

## Testing

### Integration Test Pattern (LOCKED)

```java
@SpringBootTest
@Import(IntegrationTestConfig.class)  // REQUIRED
class BookingServiceTest {
  // Uses isolated H2 in-memory DB per test
}
```

`IntegrationTestConfig` sets up the `mem:eventsnest` database. Without it, schema drops corrupt other tests. Every `@SpringBootTest` class must import it.

**Coverage targets:**
- Happy path + error cases for every service method
- All Kafka consumers tested for success, failure, idempotency
- All authorization checks tested (authorized + unauthorized)

---

## RFC Policy

**Changes requiring RFC (pull request with justification):**
1. Role model changes (platform or event roles)
2. Authorization scheme changes (membership checks, permission gates)
3. Kafka topics (new topics, schema changes)
4. Database schema (core entities)
5. Payment flow (gateway integration, escrow logic)
6. Caching strategy (TTL, invalidation)
7. Authentication (JWT changes, token expiry)

**Changes NOT requiring RFC:**
- New endpoints following existing patterns
- Bug fixes (no behavioral change)
- Refactoring (no API change)
- Test improvements
- Documentation

**Process:**
1. Open GitHub issue tagged `design-decision` or `rfc`
2. Describe what's changing, why, and impact
3. Get approval from code owner before merging
4. Include RFC issue number in commit message

---

## What Cannot Change

| Decision | Reason |
|---|---|
| Platform roles: USER, ADMIN only | One email problem solved |
| Event roles: per-request DB lookup | Real-time authorization, no stale permissions |
| Notifications via Kafka | Non-blocking requests, scalability |
| Optimistic locking on capacity | Prevent overbooking under load |
| Payment gateway: HMAC-verified | Replay protection, deduplication |
| Organizer gates vendor/manager | No admin friction, frictionless engagement |
| JWT has NO event roles | Stateless auth, real-time permission accuracy |
| Escrow protects both parties | Financial safety, dispute resolution |
| Monolith-first | Until load evidence demands extraction |
| Milestone sequencing M1→M2→M3→M4→M5→M6 | Sequential dependencies between milestones |

**Violating these will require a major rewrite.**

---

## Related Documents

| Document | Description |
|---|---|
| [`DATA_MODEL.md`](DATA_MODEL.md) | Database schema, entities, constraints, indexes |
| [`API_PATTERNS.md`](API_PATTERNS.md) | Reusable endpoint code patterns |
| [`DEPLOYMENT.md`](DEPLOYMENT.md) | AWS EC2 + RDS deployment guide |
| [`CALENDAR_INTEGRATION.md`](CALENDAR_INTEGRATION.md) | Google Calendar integration |

---

**Status:** LOCKED | **Version:** 5.0 | **Last Updated:** May 2026

⚠️ **This is the sole source of truth for EventsNest architecture. All code must comply.**
