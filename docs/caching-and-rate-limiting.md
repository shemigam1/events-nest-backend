# Caching & Rate Limiting Strategy

## Infrastructure decision: Redis for both

Rather than running two separate systems, Redis serves both caching and rate limiting from a single instance.

**Why Redis over in-process alternatives:**

- **Caffeine** (current cache) and **Bucket4j in-process** (rate limiting) both live in the JVM heap. They are lost on every restart — a deploy clears the cache and resets all rate limit counters.
- Redis persists both independently of the app lifecycle. A rate limit counter set at 11:59 PM survives a midnight deploy.
- When the deployment eventually scales beyond one instance, both features continue to work without code changes. Bucket4j's Redis backend and Spring's `RedisCacheManager` are already distributed-aware — the in-process equivalents would require a full rewrite at that point.

**Why not an API Gateway for rate limiting:**

This is a single-deployable monolith. An API Gateway solves routing and cross-service concerns that don't exist here. Adding one solely for rate limiting would be premature infrastructure. Bucket4j as a Spring filter achieves the same result at a fraction of the operational overhead.

```
Redis
├── Cache store  →  RedisCacheManager (replaces CaffeineCacheManager)
└── Bucket store →  Bucket4j rate limiting (per-IP / per-user buckets)
```

---

## Caching

### Stack

- **Spring Cache abstraction** (`@Cacheable`, `@CacheEvict`, `@Caching`) — annotations stay the same regardless of backend
- **RedisCacheManager** configured in `CacheConfig` with per-cache TTLs
- QR → ticket lookup is already cached; the backend swap is transparent to `TicketLookupAdapter`

---

### Cached endpoints

#### `GET /api/v1/events` — `public-events`
**TTL:** 5 minutes  
**Key:** single entry (no parameters — the endpoint returns the full published list)  
**Eviction triggers:**
- Admin approves an event → `PUBLISHED` (event enters the list)
- Admin cancels a published event (event leaves the list)
- Admin approves a published-event update (title/venue etc. change)
- Organiser deletes a DRAFT or CANCELLED event (defensive, list is cheap to rebuild)

**Not evicted by:** `createEvent` (new events are always `DRAFT` and never appear in this list until admin-approved), `submitForApproval`, `withdrawSubmission`.

**Reason:** Every unauthenticated visitor browsing the platform hits this endpoint. The list changes only on admin actions — infrequent writes, very high read volume.

---

#### `GET /api/v1/events/{id}` — `event-detail`
**TTL:** 10 minutes  
**Key:** `eventId`  
**Safety rule:** `PRIVATE` events are **never** written to this cache (`unless` SpEL condition). Their detail is always fetched from DB so that the per-caller guest-list authorization check runs on every request.  
**Eviction triggers:**
- Admin approves, rejects, or cancels the event
- Admin approves a pending description edit (`approveEventUpdate`)
- Organiser updates event metadata (for DRAFT/PENDING events, the field values change)
- A booking is created or cancelled (tier `availableCapacity` changes)
- Organiser deletes the event

**Not evicted by:** `submitForApproval`, `withdrawSubmission` — non-published events are rejected inside `getEventById` before a cache entry can be written, so there is no entry to evict.

**Capacity staleness trade-off:**  
Correctness is enforced at the write layer, not the read layer. `TicketTier` carries optimistic-locking semantics — capacity is decremented inside a transaction and booking fails fast if the row was concurrently modified. The worst case is a user sees "24 seats left" when there are 22; they attempt a booking and either succeed or receive an accurate error. No overselling is possible regardless of cache staleness.

---

#### `GET /api/v1/events/{id}/config` — `event-config`
**TTL:** 15 minutes  
**Key:** `eventId`  
**Eviction triggers:**
- Organiser updates event config (any module toggle)
- Organiser deletes the event

**Note:** Config updates also evict `event-detail` (the config is embedded in the detail response) and `event-programme` (toggling `programmeEnabled` off must prevent the cached programme list from being served — `getItems` checks `assertProgrammeEnabled` inside the method body, so the check is bypassed on a cache hit without this eviction).

---

#### `GET /api/v1/events/{id}/programme` — `event-programme`
**TTL:** 15 minutes  
**Key:** `eventId`  
**Eviction triggers:**
- Organiser adds, updates, or deletes a programme item
- Organiser updates event config (catches `programmeEnabled` being toggled off — see above)
- Organiser deletes the event

**Reason:** Programme content is static once an event is published — speakers and schedule slots don't change between requests. Two DB queries (fetch items + check config) on every public page load is eliminated.

---

#### `GET /api/v1/vendors` — `vendor-marketplace`
**TTL:** 5 minutes  
**Key:** `serviceType.toLowerCase()` when a filter is provided; `"all"` when not  
**Eviction triggers:**
- Admin approves a vendor verification (vendor enters the marketplace)
- Admin rejects a vendor verification (vendor leaves the marketplace)
- Organiser rates a vendor (avg rating and rating count change)

**Reason:** The marketplace response aggregates two bulk queries — average rating + count per vendor, and completed-event count per vendor — across every verified vendor. Evicting on all three write paths keeps the displayed stats accurate without per-request aggregation.

---

#### `findByEmail` (internal) — `user-by-email`
**TTL:** 10 minutes  
**Key:** `email`  
**Eviction triggers:**
- Admin enables or disables a user account

**Reason:** `findByEmail` is called on every authenticated request (JWT filter → load principal). Caching it prevents a DB hit on every API call. The only time the cached `User` object becomes stale in a security-relevant way is when an admin changes `enabled` or `role` — eviction is targeted to exactly those writes.

---

#### QR → ticket lookup *(pre-existing)*
**Cache:** `tickets-by-qr:{qrCode}`  
**TTL:** 5 minutes  
**Eviction trigger:** after successful check-in (`markCheckedIn`)  
**Reason:** A single ticket may be scanned multiple times in quick succession at a busy check-in desk. The optimistic `UPDATE WHERE status = VALID` at the write layer guarantees correctness even if the cached view is slightly stale.

---

### Endpoints NOT cached

| Endpoint | Reason |
|---|---|
| `GET /api/v1/events/{id}` (PRIVATE) | Per-caller guest-list authorization must run on every request. Caching would let an evicted guest receive event detail from a prior cache entry. |
| `GET /api/v1/events/{id}/tiers` | `availableCapacity` changes with every booking. A buyer who sees the wrong number before checkout has a poor experience. |
| `GET /api/v1/organizer/events` | Organisers create, edit, and submit events and expect to see changes immediately. |
| `GET /api/v1/me/bookings` | Personal data that changes on every booking and cancellation. |
| `GET /api/v1/me/tickets` | Personal, changes after check-in. A ticket showing `VALID` when it is `USED` is a trust issue. |
| `GET /api/v1/admin/analytics` | Admin analytics aggregate live data across multiple tables. A short TTL would give a false sense of freshness while a long one would be useless for operational decisions. Left for a future dedicated reporting pipeline. |
| `GET /api/v1/organizer/stats` | Stat cards change with every booking. Per-user keying adds complexity; TTL-only expiry leaves revenue figures stale. Deferred until organiser dashboard usage justifies the overhead. |
| All admin moderation lists | Admins make approval decisions from this data. Stale lists risk double-approvals or missed submissions. |
| All write endpoints (POST / PATCH / DELETE) | Write operations are never cached. |

---

## Rate Limiting

### Stack

- **Bucket4j** with a Redis backend (`bucket4j-redis` integration)
- Implemented as a Spring `OncePerRequestFilter` — applied before controllers, after Spring Security
- Buckets keyed by **IP address** for unauthenticated endpoints, **user ID** for authenticated ones

---

### Critical — must have

#### `POST /api/v1/auth/login`
**Limit:** 10 attempts / 15 minutes per IP  
**Reason:** The primary brute-force and credential-stuffing vector. An attacker cycling through known passwords against a target email needs to be slowed to the point where the attack is not economically viable. 10 attempts per 15 minutes stops automated tools while allowing a legitimate user who misremembers their password multiple tries.

---

#### `POST /api/v1/auth/register`
**Limit:** 5 registrations / hour per IP  
**Reason:** Unlimited registration allows bulk fake account creation — used to hoard tickets, spam events, or inflate user counts. 5 per hour is generous for any legitimate use case (a household sharing an IP, a developer testing) while making bulk creation impractical.

---

#### `POST /api/v1/auth/refresh`
**Limit:** 30 requests / 10 minutes per IP  
**Reason:** Token refresh should be automatic and infrequent in a well-behaved client. A high refresh rate is a signal of a token replay attack or a misbehaving client flooding the auth service. 30 per 10 minutes accommodates normal SPA behaviour (multiple tabs, background refresh) without allowing abuse.

---

#### `POST /api/v1/admin/invite/complete`
**Limit:** 5 attempts / hour per IP  
**Reason:** This is a public endpoint (no JWT required) that accepts an invite token. Without rate limiting, an attacker can enumerate tokens by brute force. 5 attempts per hour per IP makes token guessing computationally infeasible given the token space (`ckin_<24-char-nanoid>`).

---

#### `POST /api/v1/events/{id}/checkin`
**Limit:** 60 scans / minute per IP  
**Reason:** This is the only unauthenticated write endpoint in the system. Staff at a check-in desk scanning QR codes should comfortably fit within 60 per minute (1/second). An automated attack probing random QR codes to find valid tickets would need to be throttled at the network layer. Keying by IP rather than staff token means a single staff member's terminal can't be used as a scanning bot.

---

### High — should have

#### `POST /api/v1/admin/invite`
**Limit:** 20 invites / hour per authenticated user  
**Reason:** Each call triggers an email send via Brevo. Without a limit, a compromised admin account could send thousands of phishing-style emails with EventsNest branding. 20 per hour covers any legitimate admin onboarding scenario while containing the blast radius of a compromised account.

---

#### `POST /api/v1/events/{id}/bookings`
**Limit:** 10 bookings / minute per authenticated user  
**Reason:** Ticket scalping bots book large numbers of seats across multiple events in rapid succession. Rate limiting per user ID (not IP, since authenticated users are identified individually) prevents one account from draining event capacity. Legitimate users booking for a group will never approach 10 per minute.

---

#### `POST /api/v1/events/{id}/checkin/invites`
**Limit:** 20 invites / hour per authenticated user  
**Reason:** Each invite generates a token and optionally triggers an email. The same email-bombing concern as the admin invite applies here. 20 per hour accommodates large event staffing without enabling abuse.

---

### Medium — good to have

#### `GET /api/v1/events`, `GET /api/v1/events/{id}`, `GET /api/v1/events/{id}/tiers`
**Limit:** 120 requests / minute per IP  
**Reason:** Public unauthenticated endpoints are the easiest to scrape. 120 per minute (2/second) is imperceptible to a human user and a legitimate frontend, but caps an automated scraper at a rate that makes bulk data extraction slow and expensive. These endpoints are also the primary caching candidates — a cached response returned in <1ms offloads most of the DB pressure regardless.

---

#### `PATCH /api/v1/events/{id}/submit`
**Limit:** 10 submissions / hour per authenticated user  
**Reason:** Each submission creates work for the admin moderation queue. An organiser who can submit the same draft event 100 times per hour could spam the review queue and bury legitimate submissions from other organisers. 10 per hour is more than enough for any genuine re-submission after edits.
