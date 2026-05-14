# EventsNest Documentation

Complete architectural and design documentation for EventsNest, the full-lifecycle event management platform.

---

## 📋 Core Design Documents

### [`DESIGN_DECISIONS_MASTER.md`](DESIGN_DECISIONS_MASTER.md)
**START HERE.** Overview of all nine architectural decisions (D1-D9) with implementation patterns and rules.

- Decision summaries (locked, immutable)
- Implementation code patterns
- Data integrity constraints
- HTTP status codes
- Security guidelines
- Performance tuning
- Testing requirements
- RFC policy

**Read this first.** ~15 min read.

---

### [`DESIGN_DECISIONS.md`](DESIGN_DECISIONS.md)
**Deep dive rationale** for each of nine decisions (D1-D9). Why we made them, tradeoffs considered, when they were locked.

**Sections:**
- D1: Two platform roles only (USER, ADMIN)
- D2: No admin gate for vendor/manager engagement
- D3: Escrow as financial safety mechanism
- D4: Chat-first vendor negotiation
- D5: Event-scoped membership model
- D6: Optimistic locking for concurrency
- D7: Monnify as payment provider
- D8: Monolith-first architecture
- D9: Milestone ordering rationale

**Read when:** You need to understand *why* a decision was made, not just what it is.

**Time:** ~30 min read.

---

### [`DATA_MODEL.md`](DATA_MODEL.md)
**Complete database schema** and entity relationships.

**Sections:**
- Core entities (11 tables)
  - `users` — Platform identity
  - `events` — Event master record
  - `event_memberships` — Event-scoped roles
  - `ticket_tiers` — Seat categories
  - `bookings` — User bookings
  - `tickets` — Individual seats
  - `admin_invitations` — Admin onboarding
  - `persisted_notifications` — Audit trail
  - `contracts` — Vendor contracts (M5)
  - `contract_milestones` — Escrow milestones (M5)
  - `escrow_accounts` — Escrow management (M5)
- Indexes (performance critical)
- Unique constraints (data integrity)
- Snapshot fields (immutable data)

**Read when:** You need to understand table relationships, constraints, or design a new entity.

**Time:** ~20 min read.

---

### [`API_PATTERNS.md`](API_PATTERNS.md)
**Reusable code patterns and templates** for building endpoints.

**Patterns:**
1. Event-scoped authorization (D5)
2. Optimistic locking (D6)
3. Kafka event publishing (D4)
4. Monnify webhook verification (D7)
5. Error response format
6. DTOs & mapping
7. Transaction boundaries

**Each pattern includes:**
- Problem statement
- Solution code
- Rules/constraints
- Exception handling

**Read when:** You're writing a new endpoint and need to follow established patterns.

**Time:** ~25 min read.

---

## 📚 Supporting Documents

### PRD (Product Requirements Document)
**Not in this folder.** Reference: `EventsNest_PRD_v4.0.docx` (external)

- Product vision and problem statement
- Actor roles and responsibilities
- Functional requirements (all endpoints)
- Milestone roadmap (M1-M6)
- Non-functional requirements
- Data model overview

**Use for:** Product context, feature scope, business requirements.

---

## 🔍 How to Use This Documentation

### "I'm new to EventsNest. Where do I start?"
1. Read [`DESIGN_DECISIONS_MASTER.md`](DESIGN_DECISIONS_MASTER.md) (15 min) — Overview
2. Read [`DATA_MODEL.md`](DATA_MODEL.md) (20 min) — Database schema
3. Read [`API_PATTERNS.md`](API_PATTERNS.md) (25 min) — Code patterns

**Total:** ~60 min to understand the architecture.

### "I need to build a new endpoint."
1. Check [`API_PATTERNS.md`](API_PATTERNS.md) for the pattern (authorization, error handling, etc.)
2. Reference [`DATA_MODEL.md`](DATA_MODEL.md) for entity relationships
3. Verify your endpoint follows the rules in [`DESIGN_DECISIONS_MASTER.md`](DESIGN_DECISIONS_MASTER.md)

### "I want to understand why we made decision X."
→ Go to [`DESIGN_DECISIONS.md`](DESIGN_DECISIONS.md) and find the section (D1-D9).

### "I need to change something architectural."
1. Read the relevant section in [`DESIGN_DECISIONS_MASTER.md`](DESIGN_DECISIONS_MASTER.md)
2. Check the RFC policy section
3. Open a GitHub issue labeled `design-decision` with your proposal

### "I'm debugging a data integrity issue."
→ Go to [`DATA_MODEL.md`](DATA_MODEL.md), find the table, check constraints and indexes.

### "I'm implementing a Kafka consumer."
→ See Pattern 3 in [`API_PATTERNS.md`](API_PATTERNS.md) (Kafka Event Publishing).

---

## 🔐 What's Locked (Immutable)

These decisions cannot be changed without an RFC:

| Item | Reason |
|------|--------|
| Two platform roles (USER, ADMIN only) | Marketplace UX (one email problem) |
| Event-scoped membership model | Real-time authorization |
| Kafka-based notifications | Non-blocking requests |
| Optimistic locking on capacity | Prevent overbooking |
| Monnify payment provider | CBN licensing, USSD support |
| Organizer-gated vendor/manager engagement | No admin friction |
| Escrow-protected contracts | Financial safety |
| Monolith-first architecture | Until load evidence |
| Milestone sequencing (M3 → M4 → M5) | Dependency chain |

---

## 🚀 Quick Reference

### Authorization (D5)
```java
membershipService.ensureRole(userId, eventId, EventRole.ORGANIZER);
```
**Every event endpoint requires this.**

### Concurrent Mutations (D6)
```java
@Entity
public class TicketTier {
  @Version
  private Long version;  // Optimistic locking
}
```
**Use on fields mutated by concurrent requests.**

### Notifications (D4)
```java
kafkaTemplate.send("booking.confirmed", event);  // After @Transactional
```
**Never send email/SMS synchronously. Always via Kafka.**

### Payment (D7)
```java
if (!constantTimeEquals(signature, computedHmac)) {
  return 401 Unauthorized;
}
```
**Always verify Monnify webhooks.**

---

## 📊 Architecture Diagram

```
┌─────────────────────────────────────────┐
│         React Frontend (M3+)             │
└──────────────────┬──────────────────────┘
                   │ REST + WebSocket
┌──────────────────▼──────────────────────┐
│   Spring Boot Monolith (D8)              │
│  ┌──────────────────────────────────┐   │
│  │  REST Controllers                │   │
│  │  • Event Management              │   │
│  │  • Booking & Payment (Monnify)   │   │
│  │  • Check-in                      │   │
│  │  • Manager Panel (M3)            │   │
│  │  • Vendor Engagement (M4)        │   │
│  └──────────────────────────────────┘   │
│  ┌──────────────────────────────────┐   │
│  │  Service Layer                   │   │
│  │  • EventService                  │   │
│  │  • BookingService                │   │
│  │  • MembershipService (D5)        │   │
│  │  • MonnifyPaymentService (D7)    │   │
│  └──────────────────────────────────┘   │
└────┬──────────────────────────────────┬─┘
     │ JDBC/JPA                         │
     │                                  │ STOMP/WebSocket
┌────▼──────────────────┐     ┌────────▼──────┐
│  PostgreSQL 16        │     │  Apache Kafka │
│  ┌──────────────────┐ │     │  ┌──────────┐ │
│  │ events           │ │     │  │ booking. │ │
│  │ event_membership │ │     │  │ confirmed│ │
│  │ bookings         │ │     │  └──────────┘ │
│  │ tickets          │ │     │  ┌──────────┐ │
│  │ contracts (M5)   │ │     │  │ ticket.  │ │
│  │ escrow (M5)      │ │     │  │ checked_ │ │
│  │                  │ │     │  │ in       │ │
│  └──────────────────┘ │     │  └──────────┘ │
└────────────────────────┘     └─────────────────┘
                   │                    │
                   │ Redis Cache        │ Kafka Consumers
                   │ (M3+)              │ (Email, SMS, Push)
                   │                    │
            ┌──────▼──────┐      ┌──────▼──────────┐
            │   Redis      │      │ Notification    │
            │ (membership, │      │ Service         │
            │  capacity,   │      │ (async)         │
            │  qr codes)   │      │                 │
            └──────────────┘      └─────────────────┘
```

**Data Flow:**
1. REST request → Spring Controller
2. Controller checks authorization (MembershipService, D5)
3. Service layer handles business logic
4. JPA persists to PostgreSQL
5. Domain event published to Kafka (D4)
6. Kafka consumers handle notifications asynchronously
7. Cache invalidation on mutations

---

## 📞 Questions?

**For architectural questions:** Open GitHub issue labeled `design-decision`

**For implementation questions:** 
1. Check [`API_PATTERNS.md`](API_PATTERNS.md)
2. Search for similar code in the codebase
3. Ask in PR review if unclear

**For product/feature questions:** Refer to PRD v4.0

---

## 📝 Document Status

| Document | Version | Status | Last Updated |
|----------|---------|--------|--------------|
| DESIGN_DECISIONS_MASTER.md | 4.0 | LOCKED | May 2026 |
| DESIGN_DECISIONS.md | 4.0 | LOCKED | May 2026 |
| DATA_MODEL.md | 4.0 | LOCKED | May 2026 |
| API_PATTERNS.md | 4.0 | LOCKED | May 2026 |

**Locked:** Changes require RFC. Will reassess end of M5.

---

**EventsNest Built for:** Moniepoint DreamDev Bootcamp Capstone  
**Stack:** Spring Boot 3 | PostgreSQL 16 | Apache Kafka | Redis | React | Docker
