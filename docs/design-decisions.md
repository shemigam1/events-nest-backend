# Design Decisions

This document records the architectural and implementation decisions made during the build of EventsNest Server, along with the reasoning behind each one. It is meant to be a living reference for future contributors and for revisiting decisions when requirements change.

---

## 1. Overall Architecture — Level 1 Monolith

**Decision:** Single deployable Spring Boot JAR. All modules (auth, events, bookings, tickets, tiers, check-in, admin, notifications, email) live in the same process.

**Why not microservices:**
- Check-in load is bursty, not sustained — peaks at event start, idle most of the time
- Premature distribution adds operational complexity (service discovery, distributed tracing, eventual consistency) that doesn't pay off until 1000+ req/sec sustained
- Single instance with proper indexing and caching handles hundreds of req/sec comfortably
- Horizontal scaling (multiple instances of the same JAR) covers the next 10x without code changes

**Extraction readiness:** The check-in module specifically follows port-and-adapter constraints from day one (`TicketLookupPort` interface instead of importing `TicketRepository` directly) so it can be extracted into its own service without rewriting business logic.

**When to extract:** Move to a separate service only when booking endpoint latency starts climbing during events — i.e., check-in load is starving the rest of the app. Wait for evidence, not speculation.

---

## 2. Role Design — Mutually Exclusive USER and ADMIN

**Decision:** `Role.USER` and `Role.ADMIN` are mutually exclusive. Promoting a user to admin means they lose the user portal and only access the admin portal.

**Why:**
- Simplest model — one role per account, no permission accumulation complexity
- Admins are platform moderators, not platform users — they don't book tickets or create events
- If someone needs both (e.g. a developer testing), they create two accounts
- Avoids the frontend complexity of showing two portals to one user

**What "organiser" means:** Organiser is NOT a system role. Any `USER` becomes an organiser the moment they create an event (`EventRole.ORGANIZER` membership). The organiser console is available to all `USER` accounts — empty state is their onboarding.

---

## 3. Admin Invitation Flow — Token-Based, Not Promotion

**Decision:** New admins are created via a token-based invitation flow (`POST /api/v1/admin/invite`), not by promoting existing users.

**Why not promote existing users:**
- Promoting a `USER` to `ADMIN` would lock them out of the user portal immediately (see §2)
- Admins should be fresh accounts created specifically for moderation

**How it works:**
1. Existing admin sends invite to an email → `AdminInvitation` record created with a UUID token, 7-day expiry
2. Invitee receives email with a registration link containing the token
3. Invitee completes registration (`POST /api/v1/admin/invite/complete`) with name + password → new `User` with `Role.ADMIN` created
4. Invitation marked used — cannot be reused

---

## 4. Check-In Staff — Invitation Tokens, Not User Accounts

**Decision:** Check-in staff do not have `User` accounts. Authorization at the scanner is via per-event invitation tokens (`ckin_<24-char-nanoid>`).

**Why:**
- No password management or account sprawl for casual one-event staff
- Tokens are scoped to a single event — a leaked token grants only event-bound access
- Easy revocation: organiser deletes the invite row
- No login UX required: staff opens a link or enters the token
- Token expires automatically 24 hours after event ends

**Token security:**
- Raw token shown only once (at creation time) and included in the email
- SHA-256 hash stored in the database — raw token never persists
- Raw token cleared from the `email_jobs` table after successful email delivery

**Token format:** `ckin_<24-char-nanoid>` — prefix makes tokens grep-able in logs, NanoID provides sufficient entropy to prevent brute-force.

---

## 5. Check-In Architecture — Level 1 with Extraction-Ready Constraints

The check-in module follows specific constraints to make future extraction straightforward:

1. **Port isolation** — `CheckInServiceImpl` never imports `TicketRepository` directly. It goes through `TicketLookupPort`, which `TicketLookupAdapter` implements. Swapping to an HTTP call when extracting only requires a new adapter.
2. **Stateless services** — no in-memory state. Any instance can serve any scan.
3. **Kafka publish** — `ticket.checked_in` published after every successful scan so analytics consumers don't depend on direct calls.
4. **Caffeine cache** — QR → ticket lookup cached for 5 minutes. Evicted on successful check-in. Protects the DB during high-frequency scan bursts.
5. **Optimistic check-in** — `UPDATE tickets SET status = 'USED' WHERE id = ? AND status = 'VALID'`. Returns 0 if the ticket was already used, avoiding the read-modify-write race condition.

---

## 6. Check-In Window — Organiser-Controlled

**Decision:** Each event has a `checkInStartTime`. Check-in cannot begin before this time. Default is 2 hours before `startTime`. Organisers can override it.

**Why:** Prevents staff from accidentally scanning tickets hours before an event opens, and gives organisers control over when doors open.

**Validation:** `checkInStartTime` must be before `startTime`. The field is nullable in the database (to allow `ddl-auto` migration on existing rows) but always set by the application on creation.

---

## 7. Short Codes for Manual Entry

**Problem:** UUIDs (36 chars) are impractical for manual entry — check-in staff and organisers should not have to type them.

**Decision:** Two short codes added:

**`Events.code`** (8-char NanoID, unique):
- Auto-generated on event creation
- Used by check-in staff to configure their scanner app
- Included in the staff invite email deep link
- Resolvable via `GET /api/v1/events/code/{code}` (public)

**`Ticket.shortCode`** (8-char NanoID, unique):
- Auto-generated at ticket issuance
- Fallback when QR code cannot be scanned (cracked screen, bad lighting)
- Displayed on the digital ticket alongside the QR code
- `POST /api/v1/events/{id}/checkin` accepts either `qrCode` (scan) or `shortCode` (manual)

**Why not use `seatNumber` as the manual fallback:** Seat numbers (`V1-1`, `G5-3`) are not secret — any bystander could memorise another person's seat and attempt to check in with it. `shortCode` is random and non-guessable.

---

## 8. Check-In Staff Invite Email — Deep Link with Token in URL

**Decision:** The staff invite email includes a deep link that pre-fills the event code and staff token:
```
{frontendUrl}/checkin?eventCode=AB12CD34&eventId=UUID&token=ckin_...
```

**Security rationale:**
- The token is already in the email body as plaintext — the URL does not increase the attack surface
- Token is scoped to one event and expires 24 hours after the event ends
- Can be revoked by the organiser at any time
- Frontend must call `history.replaceState` immediately after reading params to remove them from the address bar and browser history
- Pattern is identical to password reset links and magic login links — widely accepted

---

## 9. Published Event Editing — EventEditRequest Approval Workflow

**Decision:** Organisers can edit the description of a published event, but the change does not go live immediately. It is stored as an `EventEditRequest` (status: PENDING). The live event is untouched until an admin approves the request.

**Why a separate entity instead of pending columns on `Events`:**
- Adding `pending_*` columns for each editable field would pollute the `events` table with shadow columns — every new editable field would require a schema change
- `EventEditProposedChanges` is stored as JSON in one `TEXT` column — adding venue, dates, or any other field in future costs zero schema changes
- Edit history is queryable (admin can see all PENDING, APPROVED, REJECTED requests)

**Tier editing on published events:**
- Allowed only if `soldCount == 0` (no tickets purchased for that tier)
- Only `name` and `price` can change — capacity (`rowCount`, `seatsPerRow`) is fixed once the event is live, regardless of sales
- Applied directly without approval (no attendees are affected by an unsold tier edit)

**Locked fields on published events:** `title`, `venue`, `startTime`, `endTime`, `checkInStartTime` — returns `EventFieldLockedException` if included in an update request.

---

## 10. Ticket Overbooking Protection — Optimistic Locking

**Decision:** `TicketTier` carries a `@Version` column. Capacity decrement and ticket issuance happen in one `@Transactional` method.

**What this protects against:**
- Two concurrent booking requests both reading `availableCapacity = 1` and both attempting to decrement — the `@Version` check ensures only one succeeds
- The losing request receives `ObjectOptimisticLockingFailureException` which is handled by the global exception handler as `409 "seats are no longer available, please try again"`

**Seat uniqueness:** `@UniqueConstraint(columnNames = {"tier_id", "seat_number"})` on `Ticket` is a last-resort database-level guard against duplicate seat assignment.

**No reservation/hold system:** The booking flow is entirely atomic — there is no intermediate state where a seat is "held". Booking is either fully confirmed or fully rolled back in one transaction. This is possible because there is no payment gateway integration; payment is simulated.

---

## 11. Exception Naming — Domain-Specific Over Generic

**Decision:** All exceptions are named after the domain concept they represent, not the HTTP status they produce.

**Why:**
- `EventNotFoundException` is immediately clear in a stack trace; `ResourceNotFoundException` with a string message is not
- Named exceptions allow the handler to map different domain concepts to different HTTP statuses without reading message strings
- Each exception extends its parent so HTTP status inheritance works automatically without registering every subclass in the handler

**Hierarchy:**
```
EventsNestException (400)
├── InvalidEventStateException (409)
│   ├── EventNotSubmittableException
│   ├── EventNotDeletableException
│   ├── EventAlreadyCancelledException
│   ├── EventNotPendingApprovalException
│   ├── BookingNotCancellableException
│   ├── InsufficientTierCapacityException
│   ├── TierNotEditableException
│   └── PublishedEventNotEditableException
└── UnauthorizedException (403)
    ├── NotEventOrganizerException
    └── BookingCancellationForbiddenException

ResourceNotFoundException (404)
├── EventNotFoundException
├── EventNotPublishedException
├── TicketNotFoundException
├── TicketTierNotFoundException
├── BookingNotFoundException
├── UserNotFoundException
└── CheckInInviteNotFoundException
```

---

## 12. Email Delivery — Outbox Pattern with Brevo HTTP API

**Decision:** Emails are delivered asynchronously via a database-backed job queue (outbox pattern), not sent synchronously in the request thread.

**Why outbox over synchronous:**
- SMTP/HTTP calls can take seconds — holding a DB connection and blocking the request thread for that duration risks connection pool exhaustion
- If the mail provider is down, synchronous delivery means the operation fails entirely
- With outbox, the request returns immediately; the `EmailJobPoller` retries on a 30-second schedule

**Why Brevo HTTP API over SMTP:**
- Port 587 (SMTP) is commonly blocked by firewalls and ISPs
- Brevo HTTP API runs over HTTPS (port 443) which is never blocked
- No SMTP connection management, no `JavaMailSender` configuration complexity

**Dual-provider support:** `@ConditionalOnProperty(name = "mail.provider")` — set `MAIL_PROVIDER=gmail` in env to switch to Gmail SMTP without code changes. Both providers share the same HTML templates via `AbstractEmailService`.

**Raw token security:** The `email_jobs.raw_token` column is cleared to null after successful delivery. The token never lingers in the database longer than necessary.

---

## 13. Kafka — Event-Driven Audit and Notifications

**Decision:** Key domain events are published to Kafka topics. Consumers drive email notifications and analytics.

| Topic | Publisher | Consumer |
|---|---|---|
| `booking.confirmed` | `BookingServiceImpl` | `NotificationKafkaConsumer` → email outbox |
| `ticket.checked-in` | `CheckInServiceImpl` | Analytics |
| `event.approved` | `AdminServiceImpl` | `NotificationKafkaConsumer` → email outbox |
| `event.rejected` | `AdminServiceImpl` | `NotificationKafkaConsumer` → email outbox |

**Why Kafka over direct service calls:**
- Booking service does not need to know about email — publishing an event decouples the two concerns
- Consumers can be added or removed without touching the publisher
- If the notification consumer is slow or down, it catches up via `auto-offset-reset=earliest`

**Current deployment:** No Kafka broker in the Docker setup yet. Publishers use `.whenComplete()` and log failures silently — the app runs fine without Kafka, events just don't deliver. This is acceptable during development.

---

## 14. Caching Strategy — Caffeine Now, Redis Later

**Current:** Caffeine (in-process) for QR → ticket lookup during check-in.

**Planned (Redis):** When Redis is introduced, it will serve both caching and rate limiting from one instance.

**Endpoints to cache:**

| Endpoint | TTL | Eviction |
|---|---|---|
| `GET /api/v1/events` | 2–5 min | On event approval/cancellation |
| `GET /api/v1/events/{id}` | 2–5 min | On approval/cancel/edit approved |
| `GET /api/v1/admin/analytics` | 5–10 min | TTL only |
| `GET /api/v1/organizer/stats` | 2–5 min | On new booking for organiser's event |
| QR → ticket lookup | 5 min | On successful check-in |

**Capacity staleness trade-off (`GET /api/v1/events/{id}`):** The response includes `availableCapacity` which changes with every booking. A short TTL (2–5 min) is acceptable because correctness is enforced at the write layer via `@Version` optimistic locking, not at the read layer.

---

## 15. Rate Limiting Strategy — Bucket4j Planned

**Planned stack:** Bucket4j with Redis backend (shares the Redis instance from §14).

**Priority endpoints:**

| Endpoint | Limit | Key |
|---|---|---|
| `POST /api/v1/auth/login` | 10 / 15 min | Per IP |
| `POST /api/v1/auth/register` | 5 / hour | Per IP |
| `POST /api/v1/auth/refresh` | 30 / 10 min | Per IP |
| `POST /api/v1/admin/invite/complete` | 5 / hour | Per IP |
| `POST /api/v1/events/{id}/checkin` | 60 / min | Per IP |
| `POST /api/v1/events/{id}/bookings` | 10 / min | Per user |
| `POST /api/v1/admin/invite` | 20 / hour | Per user |

See `docs/caching-and-rate-limiting.md` for full rationale per endpoint.

---

## 16. Draft Event Visibility — Hidden From Public

**Decision:** `GET /api/v1/events/{id}` returns `404` for any event that is not `PUBLISHED`. This applies to `DRAFT`, `PENDING_APPROVAL`, and `CANCELLED` events.

**Why 404 instead of 403:** A 403 would confirm the event exists. 404 prevents enumeration — an attacker cannot determine whether an ID is a draft event or simply doesn't exist.

**Organiser access:** `GET /api/v1/organizer/events/{id}` bypasses this restriction but verifies the caller is the event's organiser. Admins use `GET /api/v1/admin/events/{id}`.

---

## 17. Organiser Self-Booking Prevention

**Decision:** An event organiser cannot book tickets to their own event. `BookingServiceImpl` checks for `EventRole.ORGANIZER` membership before processing the booking and throws `UnauthorizedException` if found.

**Why:** An organiser booking their own event would skew capacity data, distort revenue figures, and potentially be used to hold seats from real attendees.

---

## 18. Local Development Seeding

**Decision:** `DevDataSeeder` runs on startup under `@Profile("local")` (activated by `APP_PROFILE=local`). It seeds 10 users and 50 events (5 per user) with realistic Nigerian titles, venues, and themed tier pricing.

**Idempotency:** Checks for the existence of `akin@devmail.com` before seeding. Safe to restart the server multiple times.

**Free vs paid events:** ~30% of seeded events are free (single General tier at ₦0). The rest have General + VIP paid tiers. Reflects real-world event platform distribution.

**Never runs in production:** `@Profile("local")` combined with `APP_PROFILE=prod` in the production environment ensures the seeder never executes outside local development.

---

## 19. User ID Format — NanoID (12 chars), not UUID

**Decision:** `users.id` is `VARCHAR(12)` storing a NanoID generated in a `@PrePersist` hook on the entity.

**Why not UUID:**
- UUIDs are 36 characters — bulky in URLs and API responses
- NanoID at 12 characters with a 62-char alphabet gives ~72 bits of entropy, safe up to ~1 billion users (birthday paradox)
- Less guessable than auto-increment integers — prevents user enumeration

**Alternatives considered:**
- **Auto-increment Long** — smaller indexes, but exposes user count in URLs
- **8-char alphanumeric** — collision risk emerges around 1M users; rejected as too tight
- **NanoID 21 chars** — UUID-equivalent entropy but loses the brevity benefit

**PRD divergence:** PRD §4.2 specifies UUID for `users.id`. Documented and accepted.

**All other entity IDs (`events`, `bookings`, `tickets`, `ticket_tiers`, `event_memberships`) remain UUID** — only user identity switches to NanoID.

---

## 20. UUID Generation — `@UuidGenerator` over `GenerationType.UUID`

**Decision:** Entities with UUID PKs use Hibernate's own `@UuidGenerator` annotation alongside `@GeneratedValue` (no JPA strategy specified).

**Why:**  
`GenerationType.UUID` is the JPA 3.1 standard but Hibernate 7 + MySQL behaves inconsistently — the UUID value is sometimes not populated on the in-memory entity after `save()`, leaving `getId()` null and causing NPEs in response builders. `@UuidGenerator` explicitly generates the UUID in Java before the INSERT reaches the database.

---

## 21. `saveAndFlush()` for Endpoints That Return DB-Generated Values

**Decision:** Service methods that build a response from a saved entity use `saveAndFlush()`, not `save()`.

**Why:**  
`@CreationTimestamp` and `@UpdateTimestamp` are populated when Hibernate flushes to the database. Plain `save()` queues the write until transaction commit, so the in-memory entity still has `null` timestamps when the response is assembled. `saveAndFlush()` sends the INSERT/UPDATE immediately and returns the entity with all DB-generated values populated.

**Applied to:** `EventServiceImpl.createEvent / updateEvent / submitForApproval`, `TicketTierServiceImpl.createTier / updateTier`, `BookingServiceImpl.createBooking`.

**Not applied to:** Internal writes where the timestamp doesn't appear in a response (capacity decrement, membership saves, etc.) — `save()` is fine there.

---

## 22. N+1 Prevention — `@EntityGraph` on Repository Methods

**Decision:** Repository methods that will access lazy-loaded associations are annotated with `@EntityGraph(attributePaths = ...)` to fetch the relationship in a single SQL JOIN.

**Why:**  
Without this, accessing `event.getCreatedBy()` after `findById` triggers a second SELECT. `@EntityGraph` collapses both tables into one query. Each repository declares only the JOIN it actually needs — nothing is fetched eagerly on the entity class itself.

**Applied to:**
- `EventRespository.findById` → JOIN `createdBy`
- `TicketTierRepository.findAllByEventId` → JOIN `event`
- `BookingRepository.findById` → JOIN `event`, `tier`, `attendee`
- `TicketRepository.findAllByAttendeeId` → JOIN `tier`, `booking`
- `TicketRepository.findByQrCode / findByShortCode` → JOIN `tier`, `tier.event`, `attendee`

---

## 23. Schema Management — Flyway Replaces `ddl-auto=update`

**Decision:** Hibernate `ddl-auto` is set to `validate`. Flyway owns all schema changes via versioned migration scripts under `src/main/resources/db/migration`.

**Why not `ddl-auto=update`:**
- `update` silently ignores destructive changes — renaming a column or changing nullability has no effect, so the mismatch only surfaces at runtime
- No migration history — impossible to know what the production schema looks like without querying the DB directly
- Breaks in team environments: two developers running different versions of the app will produce different schemas from the same codebase

**Why `validate`:**  
Hibernate checks that every entity field maps to a real column on startup and refuses to boot if there is a mismatch. This turns silent runtime failures into loud startup failures that are caught before they reach any user.

**`baseline-on-migrate=true`:** Allows existing databases (local dev machines, staging) that already have the schema to adopt Flyway without re-executing V1. Fresh databases run V1 from scratch.

---

## 24. Cross-Field Validation — `HasStartEndTime` Interface

**Decision:** `@EndAfterStart` is a class-level constraint. Both `CreateEventRequest` and `UpdateEventRequest` implement the `HasStartEndTime` interface, and `EndAfterStartValidator` works against the interface, not against a specific DTO class.

**Why an interface over duplicating the validator:**
- A validator bound to `CreateEventRequest` would need to be duplicated for `UpdateEventRequest`
- The interface makes the constraint reusable by any future DTO that carries start/end times

**Global vs field errors:**  
Class-level constraints produce `getGlobalErrors()` entries, not `getFieldErrors()`. The `MethodArgumentNotValidException` handler was updated to collect both to avoid silently dropping the validation message.

---

## 25. Manual DTO Mapping Helpers — `toEventResponse()` Pattern

**Decision:** Where ModelMapper cannot auto-map a field (because entity type differs from DTO type), the mapping is done manually in a private `toXxxResponse()` helper method. ModelMapper is configured to **skip** those fields, and the helper sets them explicitly after the auto-map.

**The specific case:**  
`Events.createdBy` is a `User` entity. `EventResponse.createdBy` is a `String` (the user's NanoID). ModelMapper's converter chain fails trying to convert `User → String`. Configuring ModelMapper to skip the field and then calling `response.setCreatedBy(event.getCreatedBy().getId())` is explicit and safe.

**Same pattern applied to:** `TicketTierServiceImpl.toTierResponse`, `BookingServiceImpl.toBookingResponse`, `TicketServiceImpl.toTicketResponse`.

**Tradeoff:** More boilerplate per DTO; the benefit is that mapping failures are compile-time errors rather than runtime surprises.

---

## 26. Response Envelope Philosophy

**Decision:** Mutating endpoints (`POST`, `PATCH`, `DELETE` that return data) wrap the response in `EventsNestResponse<T>` with `success`, `message`, `data`, `errors`. Pure query endpoints (`GET` returning lists) return the bare list.

**Why:**  
Wrapping every list in `{ "success": true, "data": [...] }` adds noise for no value — the HTTP status code already communicates success. The envelope is useful on mutations because `message` gives human-readable context ("Event created successfully", "Booking confirmed") alongside the new resource state.

---

## 27. URL Versioning — `/api/v1/` Prefix

**Decision:** All routes are under `/api/v1/`.

**PRD divergence:** PRD uses `/api/` (no version). The decision was made to version from day one so backward-incompatible changes can be introduced on `/api/v2/` without breaking existing clients.

---

## 28. Auto-Organiser on Event Creation

**Decision:** `EventServiceImpl.createEvent` inserts an `EventMembership` row with `role = ORGANIZER` for the creating user, in the same `@Transactional` boundary as the event save.

**Why atomically:**  
If the membership insert is a separate transaction and fails, the user would own an event they cannot manage. Both writes succeed or both roll back.

---

## 29. Idempotent ATTENDEE Membership Insertion

**Decision:** `BookingServiceImpl.createBooking` calls `existsByEventsIdAndUserIdAndRole(..., ATTENDEE)` before inserting the ATTENDEE membership. A second booking by the same user for the same event does not create a duplicate row.

**Why:**  
The `UNIQUE(user_id, event_id, role)` constraint on `event_memberships` would reject the duplicate insert anyway, but the idempotency check prevents an unnecessary exception and transaction rollback on what is expected behaviour.

**Known limitation:** Cancellation calls `deleteByEventsIdAndUserIdAndRole(..., ATTENDEE)`. If the user still has other confirmed bookings for the same event, this incorrectly removes their ATTENDEE access. Fix: check for remaining confirmed bookings before deleting. Tracked in §10 of the deferred work list.

---

## 30. Seat Assignment Algorithm

**Decision:** Per PRD §3.4, seat labels are derived algorithmically at booking time rather than pre-generated as rows:

```
index    = countOfNonRefundedTickets + offset
row      = floor(index / seatsPerRow)
position = (index % seatsPerRow) + 1
label    = rowPrefix + (row + 1) + "-" + position
```

**Why algorithmic over pre-generation:**  
Pre-generating seats would INSERT `rowCount × seatsPerRow` rows at event creation time — 1,500 rows for a 3-tier 500-seat event before a single booking exists. Three integers per tier plus one COUNT query at booking time is the full cost.

**Known edge case:** A cancelled ticket's seat number is consumed permanently (the `UNIQUE(tier_id, seat_number)` constraint prevents reuse). `availableCapacity` is correctly restored on cancellation, but seat labels advance monotonically. This could exhaust seat labels before capacity in a heavily-cancelled event. Accepted for v1.

---

## 31. Payment Simulation

**Decision:** Bookings set `paymentStatus = PAID` immediately and store `paymentReference = "SIMULATED-<uuid>"`.

**Schema is gateway-ready:** `payment_reference` is the future webhook landing pad. `paymentStatus` enum already supports `REFUNDED` for cancellation flows. When a real gateway (Paystack, Flutterwave) is integrated, the booking flow changes to: create a payment intent → set status `PENDING` → gateway webhook confirms → set `PAID` and fire Kafka event.

---

## 32. MySQL Chosen; PostgreSQL Would Have Been the Better Fit

**Decision:** MySQL 8 is the primary operational database.

**PRD divergence:** PRD §1 specifies PostgreSQL. MySQL was chosen for familiarity.

**Where PostgreSQL would have served this workload better:**

| Feature | Why it matters here |
|---|---|
| **MVCC without read locks** | InnoDB blocks reads during `SELECT FOR UPDATE`; Postgres MVCC means check-in reads never contend with booking writes |
| **Partial indexes** | `CREATE INDEX ON tickets (qr_code) WHERE status = 'VALID'` — index only checkable tickets, not refunded ones |
| **Native UUID type** | MySQL stores as `BINARY(16)`; Postgres has a true UUID type |
| **`ALTER TYPE ... ADD VALUE`** | Adding enum values in MySQL caused "Data truncated" failures; Postgres handles this gracefully |
| **First-class window functions + CTEs** | Admin analytics queries are significantly cleaner |

**Why migration is not justified now:** Correctness is not affected. At capstone scale, both databases are sub-millisecond. Migration cost (dialect quirks, Docker changes, retesting) outweighs the gain.

**Planned complementary stores (regardless of primary DB):**

| Store | Purpose | Trigger |
|---|---|---|
| **Redis** | QR cache (replaces Caffeine), distributed locks, rate limiting | When running more than one instance |
| **Read replica** | Public browse/list endpoints | When browse traffic competes with write latency |
| **ClickHouse** | Admin analytics dashboard | When admin queries slow the operational DB |
| **Elasticsearch** | Free-text event search | When search becomes a primary feature |

---

## 33. Tiers Created Inline With Event

**Decision:** `CreateEventRequest` accepts an optional `tiers` array. When present, ticket tiers are created in the same transaction as the event.

**Why:**  
A common organiser workflow is "create the event and define tiers at the same time". Requiring a separate `POST /tiers` call for every tier before publishing is unnecessary friction. The separate tier endpoint still exists for post-creation changes.

---

## 34. Observability — Micrometer + Prometheus

**Decision:** Booking confirmations increment Micrometer counters (`eventsnest.bookings.confirmed`, `eventsnest.bookings.revenue`). Actuator exposes `/actuator/prometheus` for Prometheus scraping.

**Why counters at the booking layer:**  
The counter is incremented only after the Kafka event is published — any exception before that point aborts and does not count. This ensures the metric counts fully-processed bookings, not partial writes.

**Actuator health probes:** `/actuator/health/liveness` and `/actuator/health/readiness` are split so Docker/k8s can distinguish "is the process alive" from "is it ready to serve traffic". Mail health indicator is disabled because the app uses Brevo's HTTP API (not SMTP), which the auto-configured mail indicator cannot probe.

---

## 35. HikariCP Pool Tuning

**Decision:** Connection pool configured with `maximum-pool-size=20`, `minimum-idle=5`, `connection-timeout=20000ms`.

**Why 20 max:**  
Check-in spikes are the busiest moment — each concurrent scan holds a connection for the duration of the `markAsCheckedIn` UPDATE. 20 connections comfortably handle ~200 concurrent scans (each <100ms), which exceeds expected load for a single app instance.

**Why 5 minimum idle:**  
Keeps connections warm between low-traffic periods so the first post-idle request doesn't pay connection establishment latency.

---

## 36. Correlation ID Propagation Through Kafka

**Decision:** A `CorrelationIdProducerInterceptor` stamps every outbound Kafka record with the current MDC `correlationId`. The downstream consumer reads this header via `CorrelationIdRecordInterceptor` and restores it to MDC before processing.

**Why:**  
Without this, a booking request and its downstream notification email are not linkable in logs — you see the booking confirmed, and separately you see an email sent, but you cannot correlate them to the same user action. The correlation ID thread links these across the async boundary.
