# EventsNest — Master Design Decisions & Architecture Guide

**Version:** 4.0 | **Last Updated:** May 2026 | **Status:** LOCKED

**Reference:** EventsNest PRD v4.0 (Sections 1-8)

---

## Overview

This is the authoritative document for EventsNest's architectural decisions. All code must comply. Deviations require an RFC (pull request with detailed justification).

**Tech Stack:** Spring Boot 3 | PostgreSQL 16 | Apache Kafka | Redis | React | Docker

**Related Documents:**
- [`DESIGN_DECISIONS.md`](DESIGN_DECISIONS.md) — Deep rationale for decisions D1-D9
- [`DATA_MODEL.md`](DATA_MODEL.md) — Database schema and entity relationships
- [`API_PATTERNS.md`](API_PATTERNS.md) — Reusable endpoint patterns and code templates

---

## Nine Core Design Decisions (D1-D9)

### D1: Two Platform Roles Only — USER and ADMIN (LOCKED)

**Decision:** The platform role on `users.role` is restricted to exactly two values: USER and ADMIN. No VENDOR or EVENT_MANAGER platform roles.

**Why:** Solves the "one email problem" — a photographer who is an attendee, organizer, vendor, and manager needs only one account, not four.

**Implementation:**
```java
// Platform role (JWT)
enum PlatformRole { USER, ADMIN }

// Event-scoped roles (database)
enum EventRole { ORGANIZER, ATTENDEE, MANAGER, VENDOR, CHECKIN_STAFF }
```

**Tradeoff:** Dashboard queries need extra DB lookup for event roles. Mitigated by Redis caching.

**See:** [`DESIGN_DECISIONS.md#D1`](DESIGN_DECISIONS.md) for full rationale.

---

### D2: No Admin Gate for Vendor or Manager Roles (LOCKED)

**Decision:** Organizers (not admins) directly engage vendors and managers. No approval queue.

**Why:** Admin approval creates friction (1-2 day delays). Organizers are accountable and qualified to decide.

**Quality Control:** Ratings (post-event), escrow (financial risk), and organizer accountability replace vetting.

**Implementation:**
```http
POST /organizer/events/{eventId}/managers
{
  "userEmail": "manager@example.com"
}
```
Creates EventMembership(role=MANAGER) immediately. No approval step.

**See:** [`DESIGN_DECISIONS.md#D2`](DESIGN_DECISIONS.md) for tradeoffs.

---

### D3: Escrow as the Financial Safety Mechanism (LOCKED)

**Decision:** Funds held in escrow after contract signing. Released milestone-by-milestone on organizer confirmation.

**Why:** Protects both vendor (funds confirmed to exist) and organizer (funds released only on delivery).

**Escrow Flow:**
```
Contract signed → Funds deposited in escrow
Vendor completes milestone → Organizer confirms
System releases that milestone's payment to vendor
...
All milestones complete → Contract COMPLETED, vendor fully paid
```

**Regulatory:** Monnify holds funds (M5). Post-capstone: full CBN escrow licensing required.

**See:** [`DESIGN_DECISIONS.md#D3`](DESIGN_DECISIONS.md) and [`DATA_MODEL.md#escrow_accounts`](DATA_MODEL.md).

---

### D4: Chat-First Vendor and Manager Discovery (LOCKED)

**Decision:** Vendor negotiation happens in-app via WebSocket chat before contracts drafted.

**Why:** Email negotiation is opaque, slow, unarchived. In-app chat creates:
- Full audit trail (attached to inquiry/contract)
- Real-time negotiation (faster than email)
- Training data for intelligence layer (M6)
- Dispute resolution evidence

**Kafka Event:** `chat.message.sent` fired after every message persisted.

**Implementation (M4):**
```
Organizer opens inquiry → VENDOR_INQUIRY conversation created
Vendor joins chat → Both parties negotiate in-app
Both agree on scope/price → Organizer drafts contract (can reference chat thread)
```

**See:** [`DESIGN_DECISIONS.md#D4`](DESIGN_DECISIONS.md) and [`API_PATTERNS.md#kafka`](API_PATTERNS.md).

---

### D5: Event-Scoped Membership Model (LOCKED)

**Decision:** All authorization is **per-event**. Roles fetched from `event_memberships` table at request time.

**Why:** Solves stale permission problem — if organizer revokes manager access, manager sees effect immediately (no JWT expiry window).

**Implementation:**
```java
// Every event endpoint must do this:
membershipService.ensureRole(userId, eventId, EventRole.ORGANIZER)
    .orElseThrow(() -> new ForbiddenException("Not authorized"));
```

**Database Query:**
```sql
SELECT * FROM event_memberships 
WHERE user_id=? AND event_id=? AND role=? AND left_at IS NULL
```

**Caching:** Redis caches membership lookups (TTL 2-3 min). Invalidated on role changes.

**See:** [`DESIGN_DECISIONS.md#D5`](DESIGN_DECISIONS.md), [`DATA_MODEL.md#event_memberships`](DATA_MODEL.md), and [`API_PATTERNS.md#auth`](API_PATTERNS.md).

---

### D6: Optimistic Locking for Capacity and Payment (LOCKED)

**Decision:** Use `@Version` field on concurrent mutations (ticket capacity, booking payment). On conflict, return HTTP 409, let client retry.

**Why:** Prevents overbooking under concurrent load without row locks. 10-100x faster than pessimistic locking.

**Implementation:**
```java
@Entity
public class TicketTier {
  @Version
  private Long version;  // Auto-managed by JPA
  
  private Integer availableCapacity;
}
```

**Flow:**
```
Request 1: SELECT available_capacity=1, version=5 WHERE tier_id=?
Request 2: SELECT available_capacity=1, version=5 WHERE tier_id=?
Request 1: UPDATE available_capacity=0, version=6 WHERE tier_id=? AND version=5 ✓
Request 2: UPDATE available_capacity=0, version=6 WHERE tier_id=? AND version=5 ✗ (no rows)
         → OptimisticLockingFailureException → HTTP 409 CONFLICT
         → Client retries, gets "capacity exhausted" error
```

**Payment Deduplication:** Same pattern prevents duplicate Monnify webhooks.

**See:** [`DESIGN_DECISIONS.md#D6`](DESIGN_DECISIONS.md) and [`API_PATTERNS.md#locking`](API_PATTERNS.md).

---

### D7: Monnify as the Payment Provider (LOCKED)

**Decision:** Monnify is the **only** payment provider for all money movement (bookings, escrow, payouts).

**Why:**
- CBN-licensed (regulatory compliance)
- Bank transfer support (USSD, dominant in Nigeria)
- HMAC-verified webhooks
- UNIQUE constraint on transaction_ref prevents double-processing

**Webhook Flow:**
```
POST /payments/monnify/webhook (HMAC-verified)
→ Check transaction_ref not already processed (UNIQUE constraint)
→ If PAID: Booking CONFIRMED, tickets issued, Kafka event fired
→ If FAILED: Booking FAILED, user can retry
```

**Fallback:** If webhook delayed, frontend polls `GET /payments/monnify/verify/{ref}`.

**See:** [`DESIGN_DECISIONS.md#D7`](DESIGN_DECISIONS.md) and [`API_PATTERNS.md#monnify`](API_PATTERNS.md).

---

### D8: Monolith-First, Extract on Evidence (LOCKED)

**Decision:** Single Spring Boot JAR. Extract only when load evidence demands it. Python FastAPI (M6 ML inference) is the only planned extraction.

**Why:** Microservices add complexity (latency, distributed transactions, ops overhead) without evidence of load.

**Ports & Adapters (from Day 1):**
```
domain/
  → Booking (core logic)
  → TicketLookupPort (interface)

adapter/
  → BookingController (REST)
  → BookingRepository (JPA)
  → CheckInAdapter (ticket lookup)  ← Can be swapped for microservice
  → MonnifyPaymentAdapter
```

**Extraction Decision:** If load data shows check-in needs independent scaling, create FastAPI `CheckInService`. Port interface lets you swap adapters.

**See:** [`DESIGN_DECISIONS.md#D8`](DESIGN_DECISIONS.md).

---

### D9: Milestone Ordering Rationale (LOCKED)

**Decision:** Product roadmap sequenced so each milestone unblocks the next.

**Sequence:**
```
M1 (Ticketing)
  ↓ Provides booking foundation
M2 (Organizer Console)
  ↓ Ratings for quality control (supports D2)
M3 (Manager Panel)
  ↓ Establishes MANAGER role model (vendor engagement reuses this)
M4 (Vendor Chat & Inquiry)
  ↓ Chat threads referenced in contracts
M5 (Contracts & Escrow)
  ↓ Historical data for ML
M6 (Intelligence Layer)
```

**Why Not Parallel?** M4 requires D2 (no admin gate). D2 relies on ratings (M2). Manager panel (M3) simpler than vendor marketplace.

**See:** [`DESIGN_DECISIONS.md#D9`](DESIGN_DECISIONS.md).

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
  
  // Now safe
  return ResponseEntity.ok(eventService.update(eventId, req));
}
```

**Rules:**
1. Extract userId from JWT
2. Call `ensureRole()` BEFORE business logic
3. Throw `ForbiddenException` on failure (HTTP 403)
4. Never use `@PreAuthorize` with event roles

**See:** [`API_PATTERNS.md#pattern1`](API_PATTERNS.md).

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
    tierRepository.save(tier);  // May throw OptimisticLockingFailureException
  } catch (OptimisticLockingFailureException e) {
    throw new ConflictException("Tier capacity changed, retry", e);
  }
  
  return bookingRepository.save(booking);
}
```

**Exception Handling:**
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

**See:** [`API_PATTERNS.md#pattern2`](API_PATTERNS.md).

---

### Kafka Event Pattern (D4)

```java
@Service
public class BookingService {
  @Autowired
  private KafkaTemplate<String, BookingConfirmedEvent> kafka;
  
  @Transactional
  public Booking confirmBooking(UUID bookingId) {
    Booking booking = repository.findById(bookingId).orElseThrow();
    
    // 1. Update DB
    booking.setStatus(BookingStatus.CONFIRMED);
    booking = repository.save(booking);
    
    // 2. Publish event (AFTER transaction commits)
    kafka.send("booking.confirmed", new BookingConfirmedEvent(
        booking.getId(),
        booking.getUserId(),
        booking.getEventId()
    ));
    
    return booking;
  }
}
```

**Consumer:**
```java
@Service
public class BookingConfirmedConsumer {
  @KafkaListener(topics = "booking.confirmed", groupId = "notifications")
  public void onBookingConfirmed(BookingConfirmedEvent event) {
    // 1. Load context
    User user = userService.getById(event.getUserId());
    
    // 2. Send notification
    try {
      emailService.send(user.getEmail(), "Booking Confirmed", "...");
    } catch (Exception e) {
      log.error("Email failed", e);
    }
    
    // 3. Persist for audit
    notificationRepository.save(new PersistedNotification(...));
  }
}
```

**Rules:**
- Publish AFTER transaction commits (Spring ensures this with `@Transactional`)
- Consumers must be idempotent (handle duplicate messages)
- Always persist notifications (for audit and replay)

**Kafka Topics (LOCKED):**
- `booking.confirmed` — On booking confirmation
- `ticket.checked-in` — On check-in scan
- `contract.signed` — On contract signature (M5)
- `milestone.released` — On escrow release (M5)

**See:** [`API_PATTERNS.md#pattern3`](API_PATTERNS.md).

---

### Monnify Webhook Pattern (D7)

```java
@PostMapping("/monnify/webhook")
public ResponseEntity<?> handleWebhook(
    @RequestBody String payload,
    @RequestHeader("Monnify-Signature") String signature) {
  
  // 1. Verify HMAC
  String computed = HmacUtils.sha512Hex(payload, MONNIFY_SECRET);
  if (!constantTimeEquals(signature, computed)) {
    return ResponseEntity.status(401).build();
  }
  
  // 2. Parse
  MonnifyWebhookPayload webhook = objectMapper.readValue(payload, ...);
  
  // 3. Check idempotency
  if (bookingService.hasProcessedRef(webhook.getTransactionReference())) {
    return ResponseEntity.ok().build();  // Already processed
  }
  
  // 4. Process
  if (webhook.getStatus().equals("SUCCESSFUL")) {
    Booking booking = bookingService.getByMonnifyRef(webhook.getTransactionReference());
    booking.setStatus(BookingStatus.CONFIRMED);
    bookingService.save(booking);
    bookingService.publishBookingConfirmed(booking);  // Kafka event
  }
  
  return ResponseEntity.ok().build();
}
```

**Rules:**
- Always verify HMAC (never skip)
- Use constant-time comparison (prevent timing attacks)
- Check for duplicate refs (UNIQUE constraint)
- Return 200 for all webhooks (Monnify will retry if needed)

**See:** [`API_PATTERNS.md#pattern4`](API_PATTERNS.md).

---

## Data Integrity

### Optimistic Locking (@Version)

Use on:
- `ticket_tiers.available_capacity` — Concurrent bookings
- `bookings` — Concurrent Monnify webhooks

Prevents: Overbooking, duplicate payment processing

---

### Unique Constraints

| Constraint | Purpose |
|-----------|---------|
| `UNIQUE(user_id, event_id, role)` on event_memberships | Prevent duplicate memberships |
| `UNIQUE(tier_id, seat_number)` on tickets | No double-booking same seat |
| `UNIQUE(monnify_transaction_ref)` on bookings | Prevent duplicate payments |

**All enforced at DB layer AND service layer.**

---

### Snapshot Fields

**Never mutable after creation:**
- `bookings.unit_price` — Snapshotted at booking time (tier price changes don't affect existing orders)
- `tickets.qr_code`, `tickets.short_code` — Check-in depends on immutability

---

## HTTP Status Codes

| Code | Use Case | Example |
|------|----------|---------|
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

**Never store event roles in JWT.** Always fetch from DB at request time (D5).

### Check-In Tokens

- Format: `ckin_<24-char-nanoid>`
- SHA-256 hash stored in DB
- Raw token sent via email only

### File Uploads

- MIME check + magic bytes validation
- Allowed: JPEG, PNG (images), CSV (exports)
- Max sizes: 5 MB (images), 10 MB (CSV)

### Webhook Verification

- HMAC-SHA512 verification (required, never skip)
- Constant-time string comparison (prevent timing attacks)
- UNIQUE constraint on transaction_ref (prevent replay)

---

## Performance

### Connection Pool (HikariCP)

```
maximumPoolSize: 20        // ~200 concurrent check-in scans
minimumIdle: 5             // Keep connections warm
maxLifetime: 25 minutes    // DB idle timeout is 30 min
idleTimeout: 15 minutes    // Release unused connections
```

### Caching (Redis, M3+)

**Cache Patterns:**

| Key | TTL | Invalidation |
|-----|-----|--------------|
| `event:{eventId}` | 5 min | Event updated → DEL |
| `tier:{tierId}:capacity` | 1 min | Booking confirmed → DEL |
| `membership:{userId}:{eventId}` | 2 min | Manager assigned → DEL |
| `qr:{qrCode}` | 5 min | Check-in completed → DEL |

**Never cache:**
- Authorization checks (security)
- Capacity checks (uses optimistic locking)
- Payment records (financial data)

**Why Redis over Caffeine:**
- Caffeine (in-process) lost on restart
- Redis shared across instances (horizontal scaling)

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

**Why:** IntegrationTestConfig sets up `mem:eventsnest` database. Without it, schema drops corrupt other tests.

**Coverage:**
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
5. Payment flow (Monnify integration, escrow logic)
6. Caching strategy (TTL, invalidation)
7. Authentication (JWT changes, token expiry)

**Changes NOT requiring RFC:**
- New endpoints following patterns
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

## What Cannot Change (Immutable)

| Decision | Reason |
|----------|--------|
| Platform roles: USER, ADMIN only | One email problem solved |
| Event roles: per-request DB lookup | Real-time authorization, no stale permissions |
| Notifications via Kafka | Non-blocking requests, scalability |
| Optimistic locking on capacity | Prevent overbooking under load |
| Monnify only | CBN licensing, USSD support, contractual |
| Organizer gates vendor/manager | No admin friction, frictionless engagement |
| JWT has NO event roles | Stateless auth, real-time permission accuracy |
| Escrow protects both parties | Financial safety, dispute resolution |
| Monolith-first | Until load evidence demands extraction |
| Milestone sequencing | M3 → M4 → M5 → M6 dependencies |

**Violating these will require a major rewrite.**

---

## Quick Links

- **Deep Rationale:** See [`DESIGN_DECISIONS.md`](DESIGN_DECISIONS.md) (D1-D9 full reasoning)
- **Database Schema:** See [`DATA_MODEL.md`](DATA_MODEL.md) (entities, constraints, indexes)
- **Code Patterns:** See [`API_PATTERNS.md`](API_PATTERNS.md) (reusable templates)
- **PRD:** EventsNest PRD v4.0 (product vision, functional requirements)

---

## Questions & Escalation

**For questions about:**
- **Why a decision was made:** Read [`DESIGN_DECISIONS.md`](DESIGN_DECISIONS.md)
- **How to implement it:** Read [`API_PATTERNS.md`](API_PATTERNS.md)
- **Database implications:** Read [`DATA_MODEL.md`](DATA_MODEL.md)
- **Product requirements:** Refer to PRD v4.0

**For RFC submissions:**
1. Open GitHub issue with `design-decision` label
2. Describe change, rationale, and impact
3. Wait for approval from code owner
4. Merge with RFC issue number in commit

---

**Status:** LOCKED | **Version:** 4.0 | **Last Updated:** May 2026

⚠️ **This is the source of truth for EventsNest architecture. All code must comply.**
