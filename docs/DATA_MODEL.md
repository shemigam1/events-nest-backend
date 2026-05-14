# EventsNest Data Model & Schema

**Version:** 4.0 | **Database:** PostgreSQL 16 | **ORM:** Spring Data JPA

This document defines all core entities, relationships, and schema constraints.

---

## Core Entities

### 1. users

**Platform-level user identity.**

```sql
CREATE TABLE users (
  id UUID PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(60) NOT NULL,  -- BCrypt
  first_name VARCHAR(100) NOT NULL,
  last_name VARCHAR(100) NOT NULL,
  role VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ADMIN')),  -- D1: Two roles only
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  deleted_at TIMESTAMP  -- Soft delete for compliance
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
```

**Fields:**
- `id` — UUID, auto-generated
- `email` — Unique user email (login credential)
- `password_hash` — BCrypt hash, never returned in API
- `role` — USER (default) or ADMIN (platform-wide)
- `created_at` — Account creation timestamp
- `updated_at` — Last modified timestamp
- `deleted_at` — Soft delete timestamp (user can request account deletion)

**Constraints:**
- Role must be USER or ADMIN (D1)
- Email must be unique
- Password never stored in plaintext

**Example:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "alex@photography.com",
  "password_hash": "$2b$12$...",  // BCrypt, not exposed
  "first_name": "Alex",
  "last_name": "Photographer",
  "role": "USER"
}
```

---

### 2. events

**Event master record. Status-driven lifecycle.**

```sql
CREATE TABLE events (
  id UUID PRIMARY KEY,
  organizer_id UUID NOT NULL REFERENCES users(id),
  title VARCHAR(255) NOT NULL,
  description TEXT,
  location VARCHAR(255),
  start_date TIMESTAMP NOT NULL,
  end_date TIMESTAMP NOT NULL,
  cover_image_url TEXT,
  status VARCHAR(30) NOT NULL 
    CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'PUBLISHED', 'CANCELLED'))
    DEFAULT 'DRAFT',
  visibility VARCHAR(20) NOT NULL 
    CHECK (visibility IN ('PUBLIC', 'PRIVATE'))
    DEFAULT 'PUBLIC',
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  deleted_at TIMESTAMP
);

CREATE INDEX idx_events_organizer_id ON events(organizer_id);
CREATE INDEX idx_events_status ON events(status);
CREATE INDEX idx_events_visibility ON events(visibility);
```

**Fields:**
- `id` — UUID
- `organizer_id` — FK to users (who created this event)
- `title` — Event name
- `description` — Event details (HTML allowed)
- `location` — Venue address
- `start_date`, `end_date` — Event window
- `cover_image_url` — S3 or local file URL
- `status` — Lifecycle: DRAFT → PENDING_APPROVAL → PUBLISHED → CANCELLED
- `visibility` — PUBLIC (listed on browse) or PRIVATE (invite-only)

**Lifecycle:**
```
DRAFT
  ↓ (organizer submits for approval)
PENDING_APPROVAL (admin reviews, approves or rejects)
  ↓
PUBLISHED (visible, bookable)
  ↓ (event ends, organizer cancels, etc.)
CANCELLED (archived, no new bookings)
```

**Constraints:**
- `end_date` must be ≥ `start_date`
- `cover_image_url` must be set before PENDING_APPROVAL
- PUBLISHED events are read-only (updates blocked)

---

### 3. event_memberships

**Event-scoped roles. Core to D5 (event-scoped authorization).**

```sql
CREATE TABLE event_memberships (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  event_id UUID NOT NULL REFERENCES events(id),
  role VARCHAR(30) NOT NULL 
    CHECK (role IN ('ORGANIZER', 'ATTENDEE', 'MANAGER', 'VENDOR', 'CHECKIN_STAFF')),
  permissions JSONB DEFAULT '{}',  -- For MANAGER: manage_guests, manage_vendors, etc.
  joined_at TIMESTAMP NOT NULL DEFAULT NOW(),
  left_at TIMESTAMP,  -- When membership ended (e.g., booking cancelled)
  
  CONSTRAINT unique_membership UNIQUE(user_id, event_id, role),
  FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE
);

CREATE INDEX idx_memberships_user_event ON event_memberships(user_id, event_id);
CREATE INDEX idx_memberships_role ON event_memberships(role);
```

**Fields:**
- `user_id`, `event_id`, `role` — Unique combination per user per event
- `permissions` — JSONB for MANAGER role:
  ```json
  {
    "manage_guests": true,
    "manage_programme": true,
    "manage_vendors": false,
    "manage_budget": true,
    "manage_checkin_staff": true
  }
  ```
- `joined_at` — When membership started
- `left_at` — When membership ended (NULL if active)

**Role Assignment Rules:**
- **ORGANIZER:** Auto-assigned on event creation (exactly 1 per event)
- **ATTENDEE:** Auto-assigned on booking confirmation, auto-removed on cancellation
- **MANAGER:** Assigned by ORGANIZER via POST /organizer/events/{id}/managers
- **VENDOR:** Assigned by system on contract signing
- **CHECKIN_STAFF:** Assigned by ORGANIZER via invitation token

**Authorization Pattern (D5):**
```java
// Every request that touches event X:
membershipService.hasRole(userId, eventId, requiredRole)
→ SELECT * FROM event_memberships 
    WHERE user_id=userId AND event_id=eventId 
    AND role=requiredRole AND left_at IS NULL
→ If found: authorized. If not: 403 Forbidden
```

---

### 4. ticket_tiers

**Seat categories (standard, VIP, early-bird). Capacity managed with optimistic locking (D6).**

```sql
CREATE TABLE ticket_tiers (
  id UUID PRIMARY KEY,
  event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  name VARCHAR(100) NOT NULL,
  price BIGINT NOT NULL,  -- in smallest unit (kobo for NGN)
  row_prefix VARCHAR(5) NOT NULL,  -- 'A', 'B', 'C' for seat maps
  row_count INT NOT NULL CHECK (row_count > 0),
  seats_per_row INT NOT NULL CHECK (seats_per_row > 0),
  total_capacity INT GENERATED ALWAYS AS (row_count * seats_per_row) STORED,
  available_capacity INT NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,  -- D6: Optimistic locking
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  
  CONSTRAINT price_non_negative CHECK (price >= 0),
  CONSTRAINT available_non_negative CHECK (available_capacity >= 0),
  CONSTRAINT available_le_total CHECK (available_capacity <= total_capacity)
);

CREATE INDEX idx_tiers_event ON ticket_tiers(event_id);
```

**Fields:**
- `id` — UUID
- `event_id` — Which event
- `name` — "Standard", "VIP", "Early Bird"
- `price` — In smallest unit (1 kobo = 1)
- `row_prefix` — For seat map display ('A', 'B', 'C')
- `row_count`, `seats_per_row` — Seat layout (e.g., 10 rows × 20 seats = 200 capacity)
- `total_capacity` — Computed (row_count × seats_per_row)
- `available_capacity` — Remaining bookable seats
- `version` — Optimistic lock counter (D6)

**Constraints:**
- Price ≥ 0
- Available capacity ≥ 0 (never negative)
- Available capacity ≤ total capacity

**Optimistic Locking Example:**
```java
// Two concurrent requests, both decrement capacity
Booking request 1:
  SELECT available_capacity, version FROM ticket_tiers WHERE id=? FOR UPDATE
  Result: available_capacity=1, version=10

Booking request 2:
  SELECT available_capacity, version FROM ticket_tiers WHERE id=? FOR UPDATE
  Result: available_capacity=1, version=10

Booking request 1 commits:
  UPDATE ticket_tiers SET available_capacity=0, version=11 
    WHERE id=? AND version=10

Booking request 2 attempts:
  UPDATE ticket_tiers SET available_capacity=0, version=11 
    WHERE id=? AND version=10
  → No rows affected (version mismatch)
  → OptimisticLockingFailureException
  → Return HTTP 409 CONFLICT, client retries
```

---

### 5. bookings

**User booking a ticket. Payment state machine (D7).**

```sql
CREATE TABLE bookings (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  tier_id UUID NOT NULL REFERENCES ticket_tiers(id),
  quantity INT NOT NULL CHECK (quantity > 0),
  unit_price BIGINT NOT NULL,  -- Snapshotted at booking time
  total_price BIGINT GENERATED ALWAYS AS (quantity * unit_price) STORED,
  status VARCHAR(20) NOT NULL 
    CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'FAILED', 'CANCELLED'))
    DEFAULT 'PENDING_PAYMENT',
  payment_gateway_ref VARCHAR(255) UNIQUE,  -- D7: Payment deduplication
  version BIGINT NOT NULL DEFAULT 0,  -- D6: Optimistic locking for payment idempotency
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  
  FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
  FOREIGN KEY (tier_id) REFERENCES ticket_tiers(id) ON DELETE CASCADE
);

CREATE INDEX idx_bookings_user_event ON bookings(user_id, event_id);
CREATE INDEX idx_bookings_status ON bookings(status);
CREATE INDEX idx_bookings_payment_gateway_ref ON bookings(payment_gateway_ref);
```

**Fields:**
- `id` — UUID
- `user_id` — Who booked
- `event_id` — Which event
- `tier_id` — Which tier (standard, VIP, etc.)
- `quantity` — Number of tickets
- `unit_price` — Snapshotted price at booking time (not tier's current price)
- `total_price` — Computed (quantity × unit_price)
- `status` — PENDING_PAYMENT → CONFIRMED → (or FAILED / CANCELLED)
- `payment_gateway_ref` — payment gateway transaction ID (unique, prevents double-processing)
- `version` — Optimistic lock for payment webhook deduplication

**State Machine:**
```
PENDING_PAYMENT (booking initiated, waiting for payment)
  ↓ (payment gateway callback: PAID)
CONFIRMED (payment successful, tickets issued)
  
  OR
  
  ↓ (payment gateway callback: FAILED)
FAILED (payment declined, user can retry)
  
  OR
  
  ↓ (User cancels before confirmation)
CANCELLED (booking cancelled, refund issued)
```

**Snapshot Pattern:**
- `unit_price` is snapshotted when booking created
- If tier price changes, existing bookings unaffected
- Enables accurate revenue reporting

---

### 6. tickets

**Individual ticket (1:1 with each seat in a booking).**

```sql
CREATE TABLE tickets (
  id UUID PRIMARY KEY,
  booking_id UUID NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
  tier_id UUID NOT NULL REFERENCES ticket_tiers(id),
  event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  seat_number INT NOT NULL,
  qr_code VARCHAR(255) NOT NULL UNIQUE,
  short_code VARCHAR(20) NOT NULL UNIQUE,  -- e.g., "EV-ABC123"
  status VARCHAR(20) NOT NULL 
    CHECK (status IN ('VALID', 'USED'))
    DEFAULT 'VALID',
  checked_in_at TIMESTAMP,
  checked_in_by_id UUID REFERENCES users(id),  -- Check-in staff who scanned
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  
  CONSTRAINT unique_seat_per_tier UNIQUE(tier_id, seat_number),
  FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE
);

CREATE INDEX idx_tickets_event_status ON tickets(event_id, status);
CREATE INDEX idx_tickets_qr_code ON tickets(qr_code);
```

**Fields:**
- `id` — UUID
- `booking_id` — Which booking
- `tier_id`, `event_id` — Which tier/event
- `seat_number` — Seat within tier (1-200)
- `qr_code` — Full QR code string (used for verification)
- `short_code` — Human-readable code (fallback for manual entry)
- `status` — VALID (not checked in) or USED (checked in)
- `checked_in_at` — When ticket was scanned
- `checked_in_by_id` — Check-in staff member

**Immutable After Issuance:**
- QR code cannot change (verification would break)
- Short code cannot change
- Seat number cannot change

**Check-In Flow:**
```
1. Check-in staff scans QR code
2. System: SELECT ticket WHERE qr_code=? AND status='VALID'
3. If found: UPDATE status='USED', checked_in_at=NOW(), checked_in_by_id=?
4. If not found or status='USED': Error (already checked in or invalid code)
5. Fire Kafka event: ticket.checked-in
```

---

### 7. admin_invitations

**One-time invitation tokens for new admins.**

```sql
CREATE TABLE admin_invitations (
  id UUID PRIMARY KEY,
  token VARCHAR(255) NOT NULL UNIQUE,
  inviter_id UUID NOT NULL REFERENCES users(id),
  invitee_email VARCHAR(255) NOT NULL,
  status VARCHAR(20) NOT NULL 
    CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED'))
    DEFAULT 'PENDING',
  expires_at TIMESTAMP NOT NULL,
  accepted_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  
  CONSTRAINT unique_pending_invitee 
    UNIQUE(invitee_email) WHERE status='PENDING'
);

CREATE INDEX idx_invitations_token ON admin_invitations(token);
```

**Invite Flow:**
```
1. Admin calls POST /admin/invite with invitee_email
2. System generates UUID token, stores with expiry (7 days)
3. Email sent: "You're invited to be an EventsNest admin. Use token: {token}"
4. Invitee calls POST /admin/invite/complete with token + password
5. System: UPDATE status='ACCEPTED', create User with role=ADMIN
```

---

### 8. persisted_notifications

**Audit trail for all outbound notifications (email, SMS, push). D4 pattern.**

```sql
CREATE TABLE persisted_notifications (
  id UUID PRIMARY KEY,
  event_id UUID REFERENCES events(id) ON DELETE SET NULL,
  user_id UUID NOT NULL REFERENCES users(id),
  type VARCHAR(20) NOT NULL 
    CHECK (type IN ('EMAIL', 'SMS', 'PUSH')),
  channel VARCHAR(255) NOT NULL,  -- email address, phone, or device token
  subject VARCHAR(255),  -- For EMAIL
  body TEXT NOT NULL,
  status VARCHAR(20) NOT NULL 
    CHECK (status IN ('QUEUED', 'SENT', 'FAILED', 'DELIVERED'))
    DEFAULT 'QUEUED',
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  sent_at TIMESTAMP,
  error_message TEXT,
  
  FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE SET NULL
);

CREATE INDEX idx_notifications_user_status ON persisted_notifications(user_id, status);
CREATE INDEX idx_notifications_event ON persisted_notifications(event_id);
```

**Kafka Consumer Writes:**
Every Kafka consumer that sends a notification inserts a record:
```java
bookingConfirmedConsumer.onMessage(event) {
  PersisedNotification record = new PersistedNotification(
    event.getEventId(),
    event.getBooking().getUserId(),
    NotificationType.EMAIL,
    user.getEmail(),
    subject="Booking Confirmed",
    body="Your booking for " + event.getTitle() + " is confirmed.",
    status=QUEUED
  );
  repository.save(record);
  
  try {
    emailService.send(...);
    record.setStatus(SENT);
    record.setSentAt(NOW());
  } catch (Exception e) {
    record.setStatus(FAILED);
    record.setErrorMessage(e.getMessage());
  }
  repository.save(record);
}
```

---

## Additional Entities (M3+)

### 9. contracts (M5, Planned)

```sql
CREATE TABLE contracts (
  id UUID PRIMARY KEY,
  event_id UUID NOT NULL REFERENCES events(id),
  vendor_id UUID NOT NULL REFERENCES users(id),
  organizer_id UUID NOT NULL REFERENCES users(id),
  title VARCHAR(255) NOT NULL,
  description TEXT,
  total_amount BIGINT NOT NULL CHECK (total_amount > 0),
  status VARCHAR(30) NOT NULL 
    CHECK (status IN ('DRAFT', 'SIGNED', 'FUNDED', 'ACTIVE', 'COMPLETED', 'DISPUTED'))
    DEFAULT 'DRAFT',
  organizer_signed_at TIMESTAMP,
  vendor_signed_at TIMESTAMP,
  funded_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

**Status Lifecycle:**
```
DRAFT (both parties drafting)
  ↓ (both sign)
SIGNED (both signed, awaiting escrow funding)
  ↓ (organizer deposits funds via payment gateway)
FUNDED (funds in escrow, ready for work)
  ↓
ACTIVE (vendor delivering milestones)
  ↓ (all milestones completed and released)
COMPLETED (contract fulfilled)
  
  OR
  
DISPUTED (disagreement on milestone completion)
```

### 10. contract_milestones (M5, Planned)

```sql
CREATE TABLE contract_milestones (
  id UUID PRIMARY KEY,
  contract_id UUID NOT NULL REFERENCES contracts(id),
  milestone_number INT NOT NULL,
  description TEXT NOT NULL,
  amount BIGINT NOT NULL,
  due_date DATE NOT NULL,
  status VARCHAR(20) NOT NULL 
    CHECK (status IN ('PENDING', 'SUBMITTED', 'APPROVED', 'RELEASED'))
    DEFAULT 'PENDING',
  released_at TIMESTAMP,
  
  CONSTRAINT unique_milestone_per_contract 
    UNIQUE(contract_id, milestone_number)
);
```

### 11. escrow_accounts (M5, Planned)

```sql
CREATE TABLE escrow_accounts (
  id UUID PRIMARY KEY,
  contract_id UUID NOT NULL UNIQUE REFERENCES contracts(id),
  total_amount BIGINT NOT NULL CHECK (total_amount > 0),
  released_amount BIGINT NOT NULL DEFAULT 0 CHECK (released_amount >= 0),
  payment_gateway_reserve_id VARCHAR(255),  -- payment gateway reserve account ID
  status VARCHAR(20) NOT NULL 
    CHECK (status IN ('CREATED', 'FUNDED', 'RELEASED', 'COMPLETED'))
    DEFAULT 'CREATED',
  
  CONSTRAINT released_le_total CHECK (released_amount <= total_amount)
);
```

---

## Indexes Summary

**Required for performance:**

```sql
-- Event-scoped authorization (D5)
CREATE INDEX idx_event_memberships_user_event_role 
  ON event_memberships(user_id, event_id, role);

-- Booking queries
CREATE INDEX idx_bookings_user_event ON bookings(user_id, event_id);
CREATE INDEX idx_bookings_status ON bookings(status);

-- Ticket verification (check-in)
CREATE INDEX idx_tickets_qr_code ON tickets(qr_code);
CREATE INDEX idx_tickets_event_status ON tickets(event_id, status);

-- Capacity checks
CREATE INDEX idx_tiers_event ON ticket_tiers(event_id);

-- Admin queries
CREATE INDEX idx_events_status ON events(status);
CREATE INDEX idx_events_visibility ON events(visibility);
```

---

## Data Integrity Constraints

All constraints enforced at both database and service layer:

| Constraint | Layer | Purpose |
|-----------|-------|---------|
| Role enum validation | DB CHECK, Java enum | D1: Only USER/ADMIN at platform level |
| Event-membership uniqueness | DB UNIQUE(user_id, event_id, role) | D5: Prevent duplicate memberships |
| Seat uniqueness | DB UNIQUE(tier_id, seat_number) | No double-booking same seat |
| Available capacity ≥ 0 | DB CHECK, Service layer | Prevent negative capacity |
| Price ≥ 0 | DB CHECK | No negative prices |
| payment gateway ref uniqueness | DB UNIQUE | D7: Prevent double-processing webhooks |
| Optimistic locking version | JPA @Version | D6: Concurrent mutation detection |

---

**Schema Version:** 4.0 | **Last Updated:** May 2026 | **Status:** LOCKED

Entity design reflects D1-D9. Changes to core entities require RFC.
