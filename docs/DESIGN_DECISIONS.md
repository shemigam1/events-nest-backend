# EventsNest Design Decisions — Detailed Rationale

**Version:** 4.0 | **Reference:** CLAUDE.md (decisions D1-D9)

This document provides deep rationale for each architectural decision, the tradeoffs considered, and when each decision was locked.

---

## D1: Two Platform Roles Only (USER and ADMIN)

### Decision
The platform role on `users.role` is restricted to exactly two values: USER and ADMIN. **No VENDOR or EVENT_MANAGER platform roles exist.**

### The Problem It Solves

**The One Email Problem:**
A photographer named Alex has these identities:
- Attends industry conferences (ATTENDEE)
- Organizes photography workshops (ORGANIZER)
- Provides catering photography services as a vendor
- Manages logistics for a friend's event (MANAGER)

In a system with platform-level VENDOR and EVENT_MANAGER roles, Alex would need:
- Account 1: email alex@photography.com (VENDOR role)
- Account 2: email alex.events@photography.com (ORGANIZER role)
- Account 3: email alex.manager@photography.com (EVENT_MANAGER role)
- Account 4: email alex.attend@photography.com (USER role)

This creates:
- Email fragmentation (notifications sent to wrong accounts)
- Password sprawl (4 passwords to remember)
- Billing confusion (which account to pay? which to invoice?)
- UX nightmare (switch accounts to change roles)

### Our Solution: Event-Scoped Membership

Instead, Alex has **one account**. The database has:
```sql
-- users table (minimal)
id, email, password, platform_role (USER)

-- event_memberships table (per-event)
user_id, event_id, role (ATTENDEE, ORGANIZER, VENDOR, MANAGER, CHECKIN_STAFF)
```

At runtime, the authorization layer resolves: "For event X, user Alex has role ORGANIZER." For event Y, Alex has role VENDOR.

### Tradeoffs

**Upside:**
- One account, one password, one email
- Unified notifications
- Single billing relationship
- Simpler onboarding

**Downside:**
- Manager dashboard must query `event_memberships` rather than checking JWT
- Extra DB lookup per session start
- Caching becomes important (Redis)

**Why We Accept the Tradeoff:**
The one-email problem is a hard blocker for marketplace UX. One extra DB lookup is negligible (indexed, cached).

### When Locked
Locked in PRD v3. Never changing.

---

## D2: No Admin Gate for Vendor or Manager Roles

### Decision
Any user can be engaged as a VENDOR or MANAGER on any event **directly by the organizer**. No approval queue or admin review required.

### The Problem It Solves

**The Approval Bottleneck:**
Without this decision, the flow would be:
1. Organizer: "I'd like to hire photographer Alex as a vendor"
2. Admin: (reviews, may deny)
3. System: Photographer assigned (if approved)

**Time cost:** 1-2 days wait
**Human cost:** Admin must understand event context
**Business impact:** Organizers look for alternatives (email vendors directly, use competitor platform)

### Our Solution: Organizer-Driven Engagement

Flow becomes:
1. Organizer: POST /events/{id}/vendors with photographer's email
2. System: EventMembership(VENDOR) created immediately
3. Photographer: Receives notification, gains access

**Time cost:** Seconds
**Human cost:** None
**Business impact:** Frictionless vendor engagement

### Quality Control Without Approval

How do we prevent bad vendors?
1. **Ratings system** — Post-event, attendees rate vendors. Bad vendors accumulate low scores.
2. **Escrow protection** — Funds held until work confirmed. Vendor fraud is self-limiting.
3. **Organizer accountability** — Organizers are the decision-makers. Bad hires hurt their events.

### Tradeoffs

**Upside:**
- Zero friction vendor engagement
- Faster event setup
- Organizers have full control

**Downside:**
- Platform cannot prevent bad vendors
- Potential for fraud (mitigated by escrow)
- Potential for abuse (mitigated by ratings, event moderation)

**Why We Accept the Tradeoff:**
Organizers are incentivized to hire good vendors (their event's success depends on it). Escrow mitigates financial risk. Ratings provide post-hoc accountability.

### When Locked
Locked in PRD v3. Core to marketplace framing.

---

## D3: Escrow as the Financial Safety Mechanism

### Decision
Funds for vendor contracts are held in a **platform-managed escrow account** after contract signing. Funds are released **milestone-by-milestone** only after organizer confirmation.

### The Problem It Solves

**The Trust Problem:**
Three payment models, all unsafe:

1. **Pay upfront:**
   - ✓ Vendor knows funds exist
   - ✗ Organizer at risk if vendor disappears

2. **Pay after delivery:**
   - ✓ Organizer only pays if vendor delivers
   - ✗ Vendor at risk if organizer refuses to pay

3. **Pay per milestone (escrow):**
   - ✓ Vendor knows funds exist (in escrow)
   - ✓ Organizer only releases on confirmed delivery
   - ✓ Platform arbitrates disputes

### Our Solution: Milestone Escrow

```
Contract signed → Monnify holds full amount in escrow
Vendor completes milestone 1 → Organizer marks complete
System releases milestone 1 payment to vendor
Vendor completes milestone 2 → Organizer marks complete
System releases milestone 2 payment to vendor
...
All milestones complete → Contract COMPLETED, vendor paid in full
```

### Regulatory Implications

**Why Escrow Matters:**
- Escrow is a fiduciary relationship (must be licensed)
- CBN (Central Bank of Nigeria) oversees fintechs holding third-party funds
- Current implementation: Monnify holds funds as an intermediary
- Post-capstone: Full escrow licensing required for production

### Tradeoffs

**Upside:**
- Both parties protected
- Disputes have clear resolution path
- Organizers can plan with confidence

**Downside:**
- Regulatory overhead (CBN licensing)
- Monnify dependency
- Delayed payout for vendors (until milestone confirmed)

**Why We Accept the Tradeoff:**
This is non-negotiable for a trusted marketplace. Without escrow, organizers won't engage vendors, and vendors won't deliver.

### When Locked
Locked in PRD v4 (M5 planning). Foundational for vendor marketplace (M4-M5).

---

## D4: Chat-First Vendor and Manager Discovery

### Decision
Vendor negotiation happens via **in-app WebSocket chat** before any contract is drafted. Chat conversation is attached to the inquiry and contract.

### The Problem It Solves

**The Opaque Negotiation Problem:**
Without chat:
- Organizer emails vendor: "We need AV services for 500 guests on June 15"
- Vendor replies via personal email: "We can do it for $10,000 + rental fee"
- Back-and-forth emails over 2 weeks
- Emails deleted, lost, mixed with other correspondence
- If dispute later: "What exactly did we agree to?" No record.

### Our Solution: In-App Negotiation

```
Organizer opens inquiry → System creates VENDOR_INQUIRY conversation
Vendor joins chat (receives notification) → Both parties chat in-app
Vendor proposes: "We can do 500-guest AV for $8k + rental"
Organizer: "Do you have crew available June 15? What's the rental cost?"
Vendor: "Yes, crew available. Rental is $2k for equipment + 1 tech."
System: Full thread archived, timestamped, never lost
Organizer drafts contract → References chat thread in contract: "See negotiation in chat #inv-123"
```

### Intelligence Layer Training Data

The full negotiation history becomes:
- Training data for M6 ML models (vendor matching, pricing intelligence)
- Audit trail for disputes
- Market research (what do vendors charge for different services?)

### Tradeoffs

**Upside:**
- Full audit trail
- Faster negotiation (real-time vs. email)
- Training data for intelligence layer
- Easier dispute resolution

**Downside:**
- Requires WebSocket infrastructure (engineering cost)
- Doesn't exist yet (M4 planned)

**Why We Accept the Tradeoff:**
Chat solves real vendor friction. The infrastructure cost is justified by the intelligence layer payoff (M6).

### When Locked
Locked in PRD v4. Unlocks M4 vendor marketplace.

---

## D5: Event-Scoped Membership Model

### Decision
All meaningful authorization is **per-event** via the `event_memberships` table. Roles are resolved **at request time** from the database, never cached in the JWT.

### The Problem It Solves

**The Stale Permission Problem:**
If roles were in JWT:
```
1. Organizer assigns Alex as MANAGER
2. Alex logs in → JWT issued with role=MANAGER
3. Organizer revokes Alex's manager access (assigns someone else)
4. But Alex's JWT is still valid (expires in 15 minutes)
5. Alex can still modify the event for 15 minutes after being revoked
```

### Our Solution: Runtime Resolution

```java
// Every request:
GET /organizer/events/{eventId}/guests
Authorization: Bearer {JWT with userId but NO event roles}
→ Controller: membershipService.hasRole(userId, eventId, MANAGER)
→ Query event_memberships(user_id=userId, event_id=eventId)
→ Return result from database (current state)
→ If no membership found: 403 Forbidden
```

**Permission changes take effect immediately.** No JWT expiry window.

### Tradeoffs

**Upside:**
- Real-time permission accuracy
- Can revoke access mid-session
- Simple, stateless JWT design
- Clear authorization logic (always DB source of truth)

**Downside:**
- Extra DB query per request (indexed, minimal cost)
- Cannot resolve permissions offline
- Redis caching becomes important

**Why We Accept the Tradeoff:**
For a financial platform with vendor payments, stale permissions are unacceptable. The DB query is negligible cost.

### When Locked
Locked in PRD v3. Core authorization pattern.

---

## D6: Optimistic Locking for Capacity and Payment

### Decision
Use `@Version` field on `ticket_tiers` and `bookings` to detect concurrent modifications. On conflict, return HTTP 409 and let the client retry.

### The Problem It Solves

**The Overbooking Problem:**
Event has 1 ticket remaining. Two users book simultaneously:

**With pessimistic locking (row lock):**
```
Time 1: User A: SELECT available_capacity FROM ticket_tiers WHERE id=1 FOR UPDATE (locks row)
Time 2: User B: SELECT available_capacity FROM ticket_tiers WHERE id=1 FOR UPDATE (waits)
Time 3: User A: decrements to 0, releases lock
Time 4: User B: acquires lock, sees 0, books fails
✓ Prevents overbooking
✗ Slow under load (lock contention, network latency)
```

**With optimistic locking (@Version):**
```
Time 1: User A: SELECT available_capacity, version=5 WHERE id=1
Time 1: User B: SELECT available_capacity, version=5 WHERE id=1
Time 2: User A: UPDATE available_capacity=0, version=6 WHERE id=1 AND version=5 ✓
Time 3: User B: UPDATE available_capacity=0, version=6 WHERE id=1 AND version=5 ✗ (version mismatch!)
        → OptimisticLockingFailureException
        → HTTP 409 CONFLICT
        → Client retries, gets booking-full error
```

**Speed:** Optimistic is 10-100x faster under concurrent load (no lock contention).

### Payment Deduplication

Same pattern prevents duplicate Monnify webhook processing:
```
Webhook 1: Marks booking CONFIRMED, increments @Version
Webhook 2 (retry): Attempts same update
        → Version mismatch detected
        → Idempotent (safe to skip)
```

### Tradeoffs

**Upside:**
- No lock contention (scales to 1000s of concurrent requests)
- Simple implementation (@Version annotation)
- Handles payment deduplication elegantly

**Downside:**
- Clients must handle 409 CONFLICT and retry
- Not all operations can use optimistic locking
- Requires idempotent retry logic

**Why We Accept the Tradeoff:**
For check-in at scale, lock contention is unacceptable. Optimistic locking is the standard pattern for high-concurrency systems.

### When Locked
Locked in PRD v1 (M1). Non-negotiable for capacity management.

---

## D7: Monnify as the Payment Provider

### Decision
**Monnify is the only payment provider.** All money movement (bookings, escrow, payouts) goes through Monnify.

### Why Monnify?

**1. CBN-Licensed Gateway**
- Operating legally in Nigeria
- Compliant with regulatory requirements
- Subject to CBN oversight (important for escrow)

**2. Bank Transfer Support (USSD, bank-to-bank)**
- Dominant payment method in Nigeria
- ~90% of Nigerians use mobile banking or USSD
- Credit card penetration low

**3. Strong Webhook & Verification**
- HMAC-verified webhooks (SHA-512)
- Replay protection (transaction_ref UNIQUE)
- Reliable delivery (Monnify handles retries)

**4. Merchant Verification**
- Business KYC built-in
- Account verification flows

### Monnify Webhook Flow

```
1. Client books ticket, payment initiated
2. Client navigates to Monnify checkout
3. Monnify processes payment
4. Monnify calls POST /payments/monnify/webhook (HMAC-verified)
5. Server marks booking CONFIRMED, issues tickets, fires Kafka event
6. (If webhook delayed) Client polls GET /payments/monnify/verify/{ref}
```

### Tradeoffs

**Upside:**
- Battle-tested gateway (used by 1000s of Nigerian fintechs)
- Regulatory compliance
- USSD support

**Downside:**
- Monnify dependency (if down, bookings blocked)
- Integration lock-in (switching providers requires API rewrite)
- Fees (~2.5% + fixed) for each transaction

**Why We Accept the Tradeoff:**
No viable alternative in Nigeria. CBN licensing is non-negotiable for escrow (D3). USSD support is table-stakes.

### When Locked
Locked in PRD v1 (M1). Integrated in M3.

---

## D8: Monolith-First, Extract on Evidence

### Decision
Start with a **single Spring Boot monolith**. Extract only when load evidence demands it. **Python FastAPI is the only planned extraction** (M6, for ML inference).

### The Problem It Solves

**The Premature Extraction Problem:**
Microservices add complexity:
- Network latency (inter-service calls)
- Distributed transactions (eventual consistency)
- Operational overhead (deployment, monitoring, debugging)

Without load evidence, this is waste.

### Our Approach: Ports & Adapters

From day one (M1), the codebase follows port-and-adapter architecture:
```
domain/
  → Booking (core logic, no framework dependencies)
  → BookingCreated (event, immutable)

application/
  → BookingService (orchestrates ports)
  → bookings/
    → CreateBookingUseCase (input port)

adapter/
  → rest/
    → BookingController (REST adapter)
  → persistence/
    → BookingRepository (persistence adapter)
  → kafka/
    → BookingEventPublisher (event publishing adapter)
  → payment/
    → MonnifyPaymentAdapter (payment adapter)
    → (future) StripePaymentAdapter (could swap)
```

**Benefit:** If load evidence shows check-in needs to scale independently, create `CheckInService` as a separate FastAPI microservice. The `TicketLookupPort` interface lets you swap adapters.

### When Extraction Happens (Evidence-Based)

**Example:** If profiling shows 90% of requests are check-in scans, and 10% are booking/admin:

**Before:** Monolith saturated at 500 req/s (all operations compete for resources)
**After:** Check-in service scaled to 2000 req/s, monolith at 200 req/s (separated concerns)

### Tradeoffs

**Upside:**
- Simpler deployment (one JAR)
- Single database (ACID transactions)
- Easier debugging (full stack trace)
- Faster time to market (no distributed system overhead)

**Downside:**
- Scaling limited by monolith bottlenecks
- Cannot choose tech per service (stuck with Spring Boot)
- Hard to extract later (must refactor first)

**Why We Accept the Tradeoff:**
Monolith is appropriate for product-market fit phase. If it proves successful, extraction is worth the effort. If it fails, we saved months of ops complexity.

### When Locked
Locked in PRD v4 (M3+). Will reassess end of M5.

---

## D9: Milestone Ordering Rationale (M1-M6)

### Decision
The product roadmap is sequenced such that each milestone unblocks the next.

### The Sequence

| Milestone | Core | Why It Must Come Before Next |
|-----------|------|-----|
| **M1** | Ticketing | All other features depend on booking foundation. Check-in, ratings, analytics all require tickets. |
| **M2** | Organizer Console, Ratings | Organizers need tools before marketplace. Ratings give quality signal before D2 (no admin gate). |
| **M3** | Manager Panel, Redis Cache | Managers are simpler than vendors (no contracts). Establishes MANAGER role and permission model that vendors reuse. |
| **M4** | Vendor Marketplace, Chat | Chat is the negotiation surface. Contracts (M5) reference chat threads. Cannot draft contracts without chat. |
| **M5** | Contracts, Escrow, Budget Tracker | Contracts must exist before escrow (cannot fund without signed contract). Budget tracker tied to contract signing (auto-create lines). |
| **M6** | Intelligence Layer (ML) | ML requires historical data (bookings, contracts, attendance, ratings). Data only exists after M1-M5. |

### Sequential Dependencies Visualized

```
M1 (Ticketing)
  ↓ (provides booking data, check-in flow)
M2 (Organizer Console)
  ↓ (ratings needed before frictionless vendor engagement)
M3 (Manager Panel)
  ↓ (establishes MANAGER role model)
M4 (Vendor Chat & Inquiry)
  ↓ (chat threads referenced in contracts)
M5 (Contracts & Escrow)
  ↓ (historical data for ML training)
M6 (Intelligence Layer)
```

### Why Not Parallel?

Some might argue: "Can't we do vendor marketplace (M4) in parallel with manager panel (M3)?"

**Answer: No.** 
- M4 requires D2 (no admin gate). 
- D2 quality assurance relies on ratings (M2).
- Manager panel (M3) is simpler to execute than vendor marketplace.
- Organizers need operational help (M3) before they need a marketplace (M4).

### When Locked
Locked in PRD v4. Ratified by team. Changes require explicit RFC.

---

## Summary: Why These Decisions Matter

These nine decisions define EventsNest. They solve real problems:

1. **D1-D2:** Enable one account to hold multiple roles (UX win, marketplace viability)
2. **D3-D4:** Create trust between organizers and vendors (financial safety, negotiation trail)
3. **D5-D6:** Scale authorization and bookings under concurrent load (technical foundation)
4. **D7:** Pick a legal payment provider (regulatory compliance)
5. **D8:** Avoid complexity until it's needed (shipping speed)
6. **D9:** Sequence features to unblock each other (product pacing)

**Violating any of these will require an RFC and likely a major rewrite.**

---

**Document Version:** 4.0 | **Last Updated:** May 2026 | **Status:** LOCKED
