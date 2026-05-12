# EventsNest — Design Decisions v2

This document supersedes `design-decisions.md`. It preserves every decision from v1
(updated where the codebase has since diverged), extends the architecture to cover
Milestones 3–6, and records the reasoning behind every new design choice.
It is a living reference — update it when a decision changes, not after the fact.

---

## System Vision

EventsNest is a full-lifecycle event management platform for the Nigerian market.
It serves four distinct actor types whose concerns must not bleed into each other:

| Actor | What they do |
|---|---|
| **Attendee** | Browse, book, attend, and review events |
| **Organizer** | Create, configure, and run events end-to-end |
| **Event Manager / Planner** | Hired by organizers to manage event operations; coordinate vendors and staff |
| **Vendor** | Service providers (caterers, AV, photographers, venues) discoverable in the marketplace |
| **Platform Admin** | Moderate content, approve events, manage platform health |

The full roadmap:

```
M1   ── Core Ticketing (done)
M2   ── Organizer Console, Guest List, Programme, Ratings, Analytics (done)
M3   ── Event Manager Panel (planned)
M4   ── Vendor + Venue Marketplaces (planned)
M5   ── Budget Tracker (planned)
M6   ── Intelligence Layer (planned)
```

---

## Part I — Decisions Carried Forward From v1 (Updated)

---

### 1. Overall Architecture — Level 1 Monolith

**Decision:** Single deployable Spring Boot JAR. All current modules live in the
same process.

**Status in v2:** Unchanged. The monolith is still the right call. As new modules
are added (chat, marketplace, budget), they join the monolith as feature packages.
Extraction happens only when load evidence demands it.

**Extraction readiness plan as the system grows:**

| Module | Extraction trigger |
|---|---|
| Check-in | Check-in burst load starves booking latency (already port-isolated) |
| Chat / Messaging | WebSocket connection count exceeds JVM thread budget |
| Marketplace search | Elasticsearch queries slow the operational DB |
| Intelligence / ML | Python-based inference; not feasible in the JVM |

---

### 2. Role Design — Now Four Distinct Roles

v1 had `USER` and `ADMIN`. v2 adds `EVENT_MANAGER` and `VENDOR` as first-class system
roles, not just event-scoped memberships.

| Role | Access |
|---|---|
| `USER` | Attendee portal — browse, book, review |
| `ORGANIZER` | Not a system role — any USER with an event gets EventRole.ORGANIZER |
| `EVENT_MANAGER` | Manager panel — assigned by an organizer to run event operations |
| `VENDOR` | Vendor portal — manage catalogue, receive inquiries, sign contracts |
| `ADMIN` | Platform moderation — cannot hold other roles simultaneously |

**Why `EVENT_MANAGER` is a system role (not just an EventRole):**
An event manager operates across multiple events for multiple organizers. Making it
a system role means they log in once and see all assigned events on a single dashboard.
An `EventRole.MANAGER` membership record links them to specific events.

**Why `VENDOR` is a system role:**
Vendors are not event attendees. Their portal (catalogue, bookings, contracts,
payments) is entirely separate from the attendee or organizer experience. A vendor
account can never book tickets as an attendee — create a second account if needed.

---

### 3. Admin Invitation Flow — Unchanged

Token-based, 7-day expiry, marks invitation used after completion. See v1 §3 for full detail.

---

### 4. Check-In Staff — Unchanged

Invitation tokens, not user accounts. `ckin_<24-char-nanoid>`, SHA-256 stored, raw token cleared post-delivery. See v1 §4.

---

### 5. Check-In Architecture — Unchanged

Port isolation, stateless, Kafka publish, Caffeine cache, optimistic UPDATE. See v1 §5.

---

### 6. Database — PostgreSQL (Migrated from MySQL in M2.0)

**Decision:** PostgreSQL 16 is the operational database. The switch from MySQL was
made at M2.0 and is now complete.

**PostgreSQL capabilities this project now exploits or will exploit:**

| Feature | Used by |
|---|---|
| Partial indexes | `WHERE status = 'VALID'` on tickets; `WHERE rsvp_token_hash IS NOT NULL` on guests |
| Native UUID type | All entity PKs |
| `ON CONFLICT DO NOTHING` | `TicketCheckInRepository.insertIfAbsent` |
| JSONB | `EventEditRequest.proposedChanges`; planned for vendor catalogue attributes |
| Full-text search (`tsvector`) | Planned for marketplace keyword search (M4) |
| Window functions | Planned for analytics dashboards (M6) |

---

### 7. Short Codes — Unchanged

`Events.code` (8-char NanoID) and `Ticket.shortCode` (8-char NanoID). See v1 §7.

---

### 8. Email Delivery — Outbox Pattern (Expanded)

The outbox pattern is unchanged. `EmailJobType` has grown:

| Type | Trigger |
|---|---|
| `BOOKING_CONFIRMED` | Booking service |
| `EVENT_APPROVED` | Admin service |
| `EVENT_REJECTED` | Admin service |
| `STAFF_INVITE` | CheckIn invite service |
| `GUEST_RSVP_INVITE` | Guest service (M2.2) |
| `RATING_REQUEST` | Rating scheduler (M2.3) |
| `VENDOR_INQUIRY` | Vendor marketplace (M4, planned) |
| `CONTRACT_SIGNED` | Contract service (M3/M4, planned) |
| `PAYMENT_CONFIRMATION` | Monnify webhook handler (M3+, planned) |
| `BUDGET_ALERT` | Budget tracker (M5, planned) |

---

### 9. Kafka Topics (Expanded)

| Topic | Publisher | Consumer |
|---|---|---|
| `booking.confirmed` | BookingServiceImpl | Email outbox, analytics |
| `ticket.checked-in` | CheckInServiceImpl | Analytics |
| `event.approved` | AdminServiceImpl | Email outbox |
| `event.rejected` | AdminServiceImpl | Email outbox |
| `chat.message.sent` | ChatServiceImpl (planned M3) | Push notification |
| `vendor.inquiry.created` | MarketplaceServiceImpl (planned M4) | Email outbox |
| `contract.signed` | ContractServiceImpl (planned M4) | Email outbox, budget tracker |
| `payment.received` | MonnifyWebhookHandler (planned M3) | Booking/invoice service |

---

### 10. Ticket Overbooking Protection — Unchanged

`@Version` on `TicketTier`, optimistic locking, `409` on contention. See v1 §10.

---

### 11. Exception Hierarchy (Expanded)

The hierarchy from v1 is extended for new modules:

```
EventsNestException (400)
├── InvalidEventStateException (409)
│   ├── ... (all v1 exceptions)
│   ├── GuestListNotEnabledException
│   ├── ProgrammeNotEnabledException
│   └── RatingsNotEnabledException
└── UnauthorizedException (403)
    ├── NotEventOrganizerException
    └── BookingCancellationForbiddenException

ResourceNotFoundException (404)
├── ... (all v1 exceptions)
├── GuestNotFoundException
├── ProgrammeItemNotFoundException
├── RatingFormNotFoundException
├── VendorNotFoundException          (M4)
├── VenueNotFoundException           (M4)
├── ContractNotFoundException        (M4)
└── BudgetNotFoundException          (M5)
```

---

### 12. Caching Strategy — Caffeine Now, Redis Planned

Unchanged. Redis introduction tracked in §30.

---

### 13. Schema Management — Flyway

Unchanged. `ddl-auto=validate`, versioned migrations, `baseline-on-migrate=true`.

Current migration state:
- V1 — baseline schema (auth, events, tiers, bookings, tickets, memberships)
- V2 — check-in invites
- V3 — event days
- V4 — programme enabled flag on event_config
- V5 — ratings enabled flag on event_config
- V6 — ticket_check_ins (day-aware)
- V7 — guests (guest list + RSVP)
- V8 — programme_items
- V9 — rating_forms, rating_questions, rating_responses, rating_answers
- V10+ — reserved for M3 (chat, manager panel, Monnify)

---

### 14. Observability — Micrometer + Prometheus

Unchanged. Booking counters, liveness/readiness probes, `/actuator/prometheus`. See v1 §34.

---

### 15. NanoID for User IDs — Unchanged

12-char NanoID, `VARCHAR(12)`, `@PrePersist`. All other entity IDs remain UUID. See v1 §19.

---

### 16. `@UuidGenerator` — Unchanged

Hibernate's `@UuidGenerator` over `GenerationType.UUID` for in-memory UUID population. See v1 §20.

---

### 17. `saveAndFlush()` — Unchanged

Used on endpoints that return `@CreationTimestamp`/`@UpdateTimestamp` fields. See v1 §21.

---

### 18. Response Envelope Philosophy — Unchanged

Mutations → `EventsNestResponse<T>`. Pure queries returning lists → bare list.

---

### 19. Draft Event Visibility — Unchanged

Non-PUBLISHED events return `404` from public endpoints to prevent enumeration. See v1 §16.

---

### 20. Payment Simulation → Monnify Integration (Planned M3)

**Current state:** Bookings set `paymentStatus = PAID` immediately with `paymentReference = "SIMULATED-<uuid>"`.

**Target state (M3):**

**Why Monnify over Paystack/Flutterwave:**
Monnify is a CBN-licensed payment gateway with strong bank transfer (USSD/bank-to-bank) support, which is the dominant payment pattern in Nigeria. Paystack and Flutterwave are also viable but Monnify's bank transfer (pay-with-bank) option covers users without cards.

**Integration design:**

```
POST /api/v1/events/{id}/bookings
  → BookingServiceImpl creates booking with status PENDING_PAYMENT
  → Calls MonnifyService.initiateTransaction(amount, email, reference)
  → Returns { bookingId, paymentUrl, paymentReference }

Frontend redirects user to paymentUrl

Monnify POSTs to POST /api/v1/payments/monnify/webhook
  → MonnifyWebhookHandler verifies HMAC signature
  → On PAID: publishes booking.confirmed event
  → BookingService confirms booking, issues tickets, fires Kafka

POST /api/v1/payments/monnify/verify/{reference}
  → Fallback: frontend polls this if webhook was delayed
```

**Schema changes (V10):**
- Add `payment_status ENUM('PENDING_PAYMENT','PAID','FAILED','REFUNDED')` to `bookings`
- Add `payment_url TEXT` to `bookings` (Monnify checkout URL, cleared post-payment)
- Add `monnify_transaction_ref VARCHAR(100)` to `bookings`

**Security:**
- Webhook endpoint is public but HMAC-verified (Monnify signs with a shared secret)
- Shared secret stored in env var, never in code
- Replay protection: `monnify_transaction_ref` unique constraint prevents double-processing

**Refund flow:**
- Cancellation calls `MonnifyService.initiateRefund(transactionRef, amount)`
- Monnify refunds asynchronously; webhook updates `payment_status = REFUNDED`

---

## Part II — New Decisions for M2.2 Onwards

---

### 21. Event Images — Mandatory, Cloud-Stored

**Decision:** Every event must have a cover image. Organizers upload JPEG or PNG at event creation or before first submission. Events without a cover image cannot be submitted for approval.

**Why mandatory:**
Discoverability on the public browse page depends entirely on visual appeal. A text-only event card performs significantly worse in engagement. Making it mandatory at the submission gate (not at creation) gives organizers time to prepare but enforces the requirement before going live.

**Storage strategy:**
Images are stored in cloud object storage (AWS S3 or Cloudinary), not in the database or on the app server disk. The `events` table stores only the `cover_image_url` (S3/Cloudinary public URL).

**Upload flow:**

```
POST /api/v1/events/{id}/cover-image   (multipart/form-data)
  → Server validates: content-type = image/jpeg or image/png
  → Server validates: file size ≤ 5 MB
  → Server generates: key = events/{eventId}/cover.{ext}
  → Uploads to S3 via presigned PUT or Cloudinary SDK
  → Saves URL to events.cover_image_url
  → Returns { coverImageUrl }
```

**Accepted formats:** `image/jpeg`, `image/png` only. No GIF, WebP, HEIC. Simple MIME check on the controller layer plus file signature check (magic bytes) to prevent content-type spoofing.

**Size limit:** 5 MB. Cloudinary (if used) applies automatic compression and serves WebP to supporting browsers via its CDN URL transforms.

**Schema change (V10 or V11):**
```sql
ALTER TABLE events ADD COLUMN cover_image_url TEXT;
```

Nullable for existing rows; `EventServiceImpl.submitForApproval` throws `EventImageRequiredException` (extends `InvalidEventStateException`) if `cover_image_url IS NULL`.

**Why not store in DB:** Binary blobs in PostgreSQL bloat the table, slow backups, and cannot be CDN-served without an intermediate layer. A URL is the industry-standard approach.

**Local dev:** An `ImageUploadService` interface with two implementations:
- `S3ImageUploadService` (prod) — uses AWS SDK
- `LocalImageUploadService` (local profile) — saves to `./uploads/` and returns `http://localhost:8080/files/{filename}`

---

### 22. Private / Invite-Only Events

**Decision:** Events have a `visibility` field with two values: `PUBLIC` (default) and `PRIVATE`. Private events are not listed on the public browse endpoint and cannot be found by search. They are accessible only to guests who hold a valid invite link.

**Why a field over a separate model:**
`visibility` is a single boolean-equivalent property on `Events`. A separate model adds no semantic value — the event is the same entity regardless of visibility.

**Enum:** `EventVisibility { PUBLIC, PRIVATE }`

**Schema change:**
```sql
ALTER TABLE events ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC';
```

**Access rules by endpoint:**

| Endpoint | PUBLIC event | PRIVATE event |
|---|---|---|
| `GET /api/v1/events` (browse) | Listed | Not listed |
| `GET /api/v1/events/{id}` | Returns event | Returns 404 unless caller holds a guest invite or is organizer |
| `GET /api/v1/events/code/{code}` | Returns event | Returns event (the code IS the invite link) |
| `POST /api/v1/events/{id}/bookings` | Allowed | Requires caller to have an accepted RSVP |

**Private event flow:**
1. Organizer creates event with `visibility: PRIVATE`
2. Organizer adds guests via guest list (M2.2) and sends RSVP invites
3. Guest receives email with RSVP link containing the event code
4. Guest accepts RSVP → gains access to `GET /api/v1/events/{id}` and booking
5. No RSVP = 404 on the event page, even with the correct UUID

**Why the event code is the invite link:**
The code is already 8-char NanoID (non-guessable), already in the staff invite email deep link, and already has a public resolver endpoint. Reusing it for private-event deep links avoids a separate invite token system.

---

### 23. Messaging / Chat System

**Decision:** A WebSocket-based in-app chat enables real-time communication between Organizers, Event Managers, and Vendors within the context of a specific event or a vendor inquiry/contract.

**Why in-app chat over email-only:**
Negotiations between organizers and vendors (pricing, logistics, contract terms) involve many back-and-forth messages. Email threads fragment context; a dedicated chat keeps the full conversation anchored to the event and the vendor booking record. Organizer ↔ Manager communication benefits from the same structured context.

**Conversation scopes:**

| Scope | Participants | Trigger |
|---|---|---|
| `EVENT_STAFF` | Organizer + all EventManagers assigned to the event | Event creation |
| `VENDOR_INQUIRY` | Organizer + Vendor (or Manager + Vendor) | Vendor inquiry created |
| `EVENT_MANAGER_DIRECT` | Organizer + Event Manager | Manager assigned to event |

**Data model:**

```
conversations
  id UUID PK
  scope ENUM('EVENT_STAFF', 'VENDOR_INQUIRY', 'EVENT_MANAGER_DIRECT')
  event_id UUID REFERENCES events (nullable for platform-level support)
  vendor_inquiry_id UUID REFERENCES vendor_inquiries (nullable)
  created_at TIMESTAMP

conversation_participants
  conversation_id UUID REFERENCES conversations
  user_id VARCHAR(12) REFERENCES users
  joined_at TIMESTAMP
  last_read_at TIMESTAMP
  PRIMARY KEY (conversation_id, user_id)

messages
  id UUID PK
  conversation_id UUID REFERENCES conversations
  sender_id VARCHAR(12) REFERENCES users
  body TEXT NOT NULL
  sent_at TIMESTAMP NOT NULL
  edited_at TIMESTAMP
  deleted_at TIMESTAMP   -- soft delete; body replaced with "[deleted]" on read
```

**WebSocket transport:** Spring WebSocket with STOMP over SockJS.

```
SUBSCRIBE /user/queue/messages         → personal message queue (new messages)
SUBSCRIBE /topic/conversation/{id}     → broadcast to all participants
SEND      /app/chat.send               → sends a message to a conversation
SEND      /app/chat.read               → marks conversation as read
```

**Authentication on WebSocket handshake:**
JWT token passed as a query parameter on the handshake URL (`?token=...`). The `WebSocketSecurityConfig` extracts the token during the `CONNECT` frame and populates the security context. No cookie-based auth — the mobile client must be supported.

**Why STOMP over raw WebSocket:**
STOMP provides subscription routing (`/topic/`, `/user/queue/`) out of the box. Raw WebSocket would require implementing pub-sub routing manually.

**Persistence:** All messages are persisted. There is no ephemeral chat. Participants can scroll history. The `last_read_at` column per participant drives unread badge counts.

**Offline delivery:** If a participant is not connected via WebSocket, the message is persisted and delivered on next connection. Push notifications (FCM/APNs for mobile, browser push for web) are a M6 enhancement.

**Kafka topic:** `chat.message.sent` — published after every persisted message. Consumers can drive push notifications, unread counts, or audit logging without coupling to the chat service.

**File attachments in chat:** Not in initial scope. Attachment support (PDF contracts, images) added in a later iteration using the same S3 upload pattern as event images.

---

### 24. Vendor Contracts — Structured, Signable

**Decision:** A `Contract` entity represents a formal agreement between an Organizer (or Event Manager acting on behalf) and a Vendor for a specific event. Contracts are created from conversation context and have a defined signing lifecycle.

**Why contracts as a first-class entity:**
Verbal agreements in chat are not binding and not auditable. A structured contract record with a signed/unsigned status, monetary value, and service scope feeds directly into the Budget Tracker (M5) and provides legal cover for both parties.

**States:** `DRAFT → SENT → SIGNED → COMPLETED | CANCELLED`

**Schema (V12, planned M4):**

```sql
CREATE TABLE contracts (
  id UUID PRIMARY KEY,
  event_id UUID REFERENCES events ON DELETE CASCADE,
  organizer_id VARCHAR(12) REFERENCES users,
  vendor_id VARCHAR(12) REFERENCES users,
  conversation_id UUID REFERENCES conversations,
  title VARCHAR(255) NOT NULL,
  description TEXT,
  amount NUMERIC(15,2) NOT NULL,
  currency VARCHAR(10) NOT NULL DEFAULT 'NGN',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  organizer_signed_at TIMESTAMP,
  vendor_signed_at TIMESTAMP,
  due_date DATE,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);
```

**Signing flow:**
1. Organizer creates contract (DRAFT) from within a vendor inquiry conversation
2. Organizer sends contract to vendor (status → SENT); vendor receives email
3. Vendor reviews and signs in-app (status → SIGNED if both have signed; single-party sign triggers the other party's email)
4. Once both parties have signed, status → SIGNED; amount debited from event budget
5. On event completion, organizer marks contract COMPLETED, triggering Monnify payout (M5)

**Why not PDF e-signature (DocuSign etc.) now:**
Integration complexity. The in-app signing (timestamped DB record with user_id) is legally acceptable for informal agreements. PDF signing can be layered on later by generating a PDF from the contract record and sending it through a signing API.

---

## Part III — Milestone 3: Event Manager Panel

---

### 25. Event Manager Role and Panel

**What it is:** An Event Manager / Planner is a professional hired by an Organizer to run the operational side of an event. They do not own the event (the Organizer does) but they have full operational authority.

**Capabilities of an Event Manager:**

| Capability | Notes |
|---|---|
| View and edit event details | Within permissions granted by Organizer |
| Manage guest list | Add, remove, update RSVP status |
| Manage programme | Add/edit/delete programme items |
| Manage check-in staff | Send and revoke staff invites |
| Access event analytics | View dashboard for their assigned events |
| Communicate with vendors | Initiate and manage vendor inquiries |
| Manage contracts | Create, send, and sign contracts on behalf of organizer |
| Manage budget | View and update budget tracker |

**What an Event Manager cannot do:**
- Create a new event (that belongs to the Organizer)
- Change event pricing or tiers after publish (Organizer only)
- Cancel or delete the event (Organizer only)
- Access events they have not been assigned to

**Assignment flow:**
```
POST /api/v1/organizer/events/{eventId}/managers
  body: { email, permissions[] }
  → Looks up User with Role.EVENT_MANAGER by email
  → Creates EventMembership(role=MANAGER, eventId, userId)
  → Sends assignment email to manager
```

**Permission model:**
Rather than a flat `MANAGER` role, managers have a `permissions` bitmask or JSON array on their `EventMembership` row. This lets an organizer assign a manager who can edit the guest list but not the budget, for example.

```sql
ALTER TABLE event_memberships ADD COLUMN permissions JSONB;
-- Example: ["GUEST_LIST", "PROGRAMME", "ANALYTICS", "VENDORS", "BUDGET"]
```

**Manager portal URLs:**
`GET /api/v1/manager/events` — list all events they manage
`GET /api/v1/manager/events/{id}` — full event detail with manager capabilities

---

### 26. Monnify Integration — Manager Payment Flows

Event Managers often handle vendor payments on behalf of organizers. The Monnify integration (§20) must support:

- **Vendor payout initiation** — Manager marks a contract COMPLETED; Monnify transfer-to-account API sends funds to vendor's registered bank account
- **Receipt upload** — If manual bank transfer is used instead, Manager uploads a payment receipt (PDF/image) attached to the contract record

---

## Part IV — Milestone 4: Vendor + Venue Marketplaces

---

### 27. Vendor and Venue Marketplace

**What it is:** A searchable directory of service vendors (caterers, AV, décor, photography, MC, security) and venues. Organizers and Managers discover and inquire with vendors directly within the platform.

**Vendor profile:**

```
vendors
  id UUID PK
  user_id VARCHAR(12) REFERENCES users (Role.VENDOR account)
  business_name VARCHAR(255) NOT NULL
  category VARCHAR(100)           -- CATERING, AV, PHOTOGRAPHY, etc.
  description TEXT
  location VARCHAR(255)
  state VARCHAR(100)              -- Nigerian state
  cover_image_url TEXT
  portfolio_images JSONB          -- array of S3 URLs
  starting_price NUMERIC(15,2)
  currency VARCHAR(10) DEFAULT 'NGN'
  verified BOOLEAN DEFAULT FALSE  -- Admin-verified vendor
  rating_avg NUMERIC(3,2)         -- computed from completed contracts
  rating_count INT
  created_at TIMESTAMP
```

**Venue profile:** Same structure as vendors but with additional fields:
`capacity INT`, `address TEXT`, `lat NUMERIC`, `lng NUMERIC`, `amenities JSONB`.

**Discovery:**
```
GET /api/v1/vendors?category=CATERING&state=Lagos&minRating=4.0&page=0&size=20
GET /api/v1/venues?capacity=500&state=Abuja&amenities=AC,PARKING
```

**Vendor inquiry → conversation → contract flow:**
```
POST /api/v1/vendors/{vendorId}/inquiries
  body: { eventId, message, eventDate, estimatedBudget }
  → Creates VendorInquiry record
  → Creates Conversation(scope=VENDOR_INQUIRY, vendorInquiryId)
  → Adds organizer + vendor as participants
  → Publishes vendor.inquiry.created Kafka event → email to vendor
  → Returns { inquiryId, conversationId }
```

**Vendor verification:**
Platform Admin marks vendors as `verified=true` after reviewing business registration documents (CAC number). Verified vendors get a badge on their profile. Unverified vendors can still list but display a "Not yet verified" label.

**Search strategy (phased):**
- M4 launch: PostgreSQL `ILIKE` + category/state/rating filters
- M6: Elasticsearch for full-text description search ("wedding catering Lagos Island")

**Why not a third-party marketplace (Eventbrite vendor directory etc.):**
This platform's competitive advantage is keeping the entire organizer workflow — discovery, communication, contracts, budgeting, payments — in one place. Routing vendors to an external directory breaks that loop.

---

### 28. Vendor Ratings — Post-Contract

Vendor ratings are submitted by Organizers after a contract is marked COMPLETED.

**Design:** Reuse the existing ratings infrastructure (M2.3) with a `vendor_id` scope instead of `event_id`. A `VendorRatingForm` is auto-created when the first contract for a vendor completes.

**Anti-abuse rule:** A rating can only be submitted if the organizer has a COMPLETED contract with that vendor. No contract = no rating. This prevents fake reviews.

---

## Part V — Milestone 5: Budget Tracker

---

### 29. Budget Tracker

**What it is:** A financial control panel for an event, tracking planned budget vs. actual spend, with categories aligned to vendor types.

**Core model:**

```sql
CREATE TABLE event_budgets (
  id UUID PRIMARY KEY,
  event_id UUID UNIQUE REFERENCES events ON DELETE CASCADE,
  total_budget NUMERIC(15,2) NOT NULL,
  currency VARCHAR(10) NOT NULL DEFAULT 'NGN',
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

CREATE TABLE budget_line_items (
  id UUID PRIMARY KEY,
  budget_id UUID REFERENCES event_budgets ON DELETE CASCADE,
  category VARCHAR(100) NOT NULL,    -- VENUE, CATERING, AV, DECOR, SECURITY, etc.
  description VARCHAR(255),
  planned_amount NUMERIC(15,2) NOT NULL,
  actual_amount NUMERIC(15,2),       -- populated when contract is signed/paid
  contract_id UUID REFERENCES contracts,  -- nullable; links to the actual spend
  status VARCHAR(20) DEFAULT 'PLANNED',   -- PLANNED, COMMITTED, PAID
  created_at TIMESTAMP
);
```

**Automatic line item creation:**
When a contract is signed, a budget line item is auto-created (or updated) with:
- `category` = vendor's category
- `planned_amount` = contract amount
- `status` = COMMITTED

When a contract is marked COMPLETED (payment sent via Monnify):
- `actual_amount` = amount paid
- `status` = PAID

**Budget alerts:**
`@Scheduled` job checks daily for events where:
- `SUM(planned_amount) > total_budget` (over-committed)
- `SUM(actual_amount) > total_budget * 0.9` (90% of budget spent)

Triggers `EmailJobType.BUDGET_ALERT` email to the organizer and any managers with `BUDGET` permission.

**Income tracking:**
Ticket revenue from Monnify is also reflected in the budget tracker as income, giving organizers a single P&L view per event:
- Revenue: ticket sales (confirmed bookings × ticket price)
- Expenses: vendor contracts

**Why not a third-party accounting tool:**
Event budgets are small, single-event P&Ls. Connecting to QuickBooks or Wave is overkill and breaks the platform's unified UX promise.

---

## Part VI — Milestone 6: Intelligence Layer

---

### 30. Intelligence Layer — Recommendations and Insights

**What it is:** ML-assisted features that make the platform smarter for organizers, managers, and attendees.

**Planned capabilities:**

| Feature | Data source | Output |
|---|---|---|
| **Smart vendor matching** | Event type, location, budget, past contracts | Ranked vendor suggestions when starting an inquiry |
| **Optimal event timing** | Historical attendance, check-in density, external calendar data | "Similar events on this date saw 30% lower turnout" |
| **Budget forecasting** | Past event budgets for similar event types and sizes | "Similar events of this size in Lagos average ₦2.4M for catering" |
| **Attendee churn prediction** | RSVP-to-checkin conversion, booking-to-attendance rate | Flag when a confirmed attendee has low check-in probability |
| **Rating sentiment analysis** | Free-text rating responses | Auto-generated themes ("food quality", "sound system") on the ratings dashboard |

**Architecture — hybrid (Spring Boot + Python microservice):**

Java is not the right runtime for ML inference. A Python FastAPI microservice hosts the models. The Spring Boot monolith calls it over HTTP for predictions. This is the first and only justified extraction from the monolith — Python is a hard constraint for ML, not a scalability concern.

```
Spring Boot (monolith)
  ↓ HTTP (internal)
Python FastAPI (intelligence-service)
  ↓ reads from
PostgreSQL read replica  +  ClickHouse (analytics events)
```

**Data pipeline:**
- Kafka consumer in the Python service ingests domain events (`booking.confirmed`, `ticket.checked-in`, `contract.signed`, `rating.submitted`) into ClickHouse
- ClickHouse is the analytics store for the intelligence layer — it handles the aggregation queries (time-series, percentiles) that would be expensive on PostgreSQL

**Why ClickHouse over BigQuery / Redshift:**
ClickHouse is open-source, self-hostable (Docker), and extremely fast for the column-oriented aggregation queries that analytics features need. No egress costs, no per-query billing.

**Privacy:**
- Vendor matching and budget forecasting use anonymized aggregate data — no individual organizer's data is exposed to another
- Attendee churn prediction uses only the requesting organizer's own attendee data

---

## Part VII — Cross-Cutting Decisions

---

### 31. Redis Introduction — When, Not If

Redis is introduced when the first of these conditions is met:
1. Deploying more than one app instance (rate limiters and caches must be shared)
2. WebSocket chat requires a pub-sub broker to route messages across instances (Redis pub-sub)
3. Caffeine cache hit rate drops below 60% (indicating cache thrash from restart frequency)

**Redis will serve three roles:**
```
Redis
├── Cache store      → RedisCacheManager replaces CaffeineCacheManager
├── Rate limiting    → Bucket4j Redis backend
└── Pub-sub broker   → Spring WebSocket STOMP message broker (chat)
```

---

### 32. File Storage — S3-Compatible, Abstracted Behind Interface

All binary uploads (event images, vendor portfolio images, contract PDF receipts) go through a `FileStorageService` interface:

```java
public interface FileStorageService {
    String upload(InputStream data, String key, String contentType, long sizeBytes);
    void delete(String key);
    String getPublicUrl(String key);
}
```

Implementations:
- `S3FileStorageService` — AWS SDK, production
- `LocalFileStorageService` — saves to `./uploads/`, serves via `/files/**` static handler, local profile only

This ensures zero production code changes if the storage backend is swapped (e.g. Cloudinary for image-specific transforms).

---

### 33. Rate Limiting — Bucket4j (Planned with Redis)

Unchanged from v1 §15. Added limits for new endpoints:

| Endpoint | Limit | Key |
|---|---|---|
| `POST /api/v1/events/{id}/cover-image` | 10 uploads / hour | Per user |
| `POST /api/v1/vendors/{id}/inquiries` | 20 / hour | Per user |
| `SEND /app/chat.send` (WebSocket) | 60 messages / min | Per user |
| `POST /api/v1/payments/monnify/webhook` | 200 / min | Per IP (Monnify IPs only) |

---

### 34. Correlation ID — Unchanged

MDC correlation ID propagated through Kafka via producer/consumer interceptors. See v1 §36.

---

### 35. HikariCP Tuning — Review at M3

Current: `maximum-pool-size=20`, `minimum-idle=5`. When WebSocket connections are added (M3 chat), the connection-per-request model changes. WebSocket handlers hold threads differently from HTTP handlers. Revisit pool sizing when chat is implemented.

---

### 36. Deferred Work Tracker

Items carried forward from v1 plus new ones:

| # | Item | Milestone |
|---|---|---|
| 1 | ATTENDEE membership cleanup on booking cancellation (orphan check before delete) | M2 hotfix |
| 2 | Seat label exhaustion on heavily-cancelled events (monotonic seat advance) | M2 / accepted for now |
| 3 | Redis: replace Caffeine, add rate limiting, WebSocket broker | M3 |
| 4 | Monnify payment integration (replace simulation) | M3 |
| 5 | Event image upload (S3 + mandatory on submission) | M3 |
| 6 | Private event access control (RSVP-gated booking) | M3 |
| 7 | WebSocket chat (conversations, messages, STOMP) | M3 |
| 8 | Event Manager role + panel + permission model | M3 |
| 9 | Vendor + Venue marketplace (catalogue, search, inquiry) | M4 |
| 10 | Vendor contracts (lifecycle, signing, Monnify payout) | M4 |
| 11 | Vendor ratings (post-contract, anti-abuse) | M4 |
| 12 | Budget Tracker (line items, contract linking, alerts) | M5 |
| 13 | Python intelligence microservice skeleton | M6 |
| 14 | ClickHouse analytics pipeline | M6 |
| 15 | DEPLOYMENT.md rewrite for PostgreSQL | Ongoing |
| 16 | Bucket4j rate limiting (login, register, check-in, chat) | M3 |

---

*Last updated: May 2026 — reflects codebase state at end of Milestone 2.3*
*Next review: when M3 (Event Manager Panel) implementation begins*
