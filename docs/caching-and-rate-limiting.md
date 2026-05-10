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

- **Spring Cache abstraction** (`@Cacheable`, `@CacheEvict`) — annotations stay the same regardless of backend
- **RedisCacheManager** replaces `CaffeineCacheManager` in `CacheConfig`
- QR → ticket lookup is already cached; the backend swap is transparent to `TicketLookupAdapter`

---

### Endpoints to cache

#### `GET /api/v1/events`
**Cache:** yes — `published-events` key  
**TTL:** 2–5 minutes  
**Eviction trigger:** when an event is approved (`PUBLISHED`), cancelled, or force-cancelled by admin  
**Reason:** Every visitor browsing the platform hits this endpoint. The list changes only when an admin approves or cancels an event — infrequent writes, very high read volume. Serving from cache removes repeated full-table scans on the `events` table.

---

#### `GET /api/v1/events/{id}`
**Cache:** yes — `event-by-id:{eventId}` key  
**TTL:** 2–5 minutes  
**Eviction trigger:** on approval, rejection, cancellation, or when a pending description edit is approved  
**Reason:** Public event detail pages are read far more often than they are updated. The response includes tier capacity (`availableCapacity`) which changes with every booking.

**Capacity staleness trade-off:**  
A short TTL (2–5 min) is acceptable because correctness is enforced at the write layer, not the read layer. `TicketTier` carries a `@Version` column — the optimistic lock on `UPDATE WHERE status = VALID` (check-in) and the capacity decrement during booking both fail gracefully if the data was stale. The worst case is a user sees "24 seats left" when there are actually 22 — they attempt a booking and either succeed or receive an accurate "insufficient capacity" error. No overselling is possible.

Splitting the response (caching event details separately from capacity) is the correct long-term approach but adds complexity. Revisit when booking frequency makes the 2-minute lag visible in production data.

---

#### `GET /api/v1/admin/analytics`
**Cache:** yes — `admin-analytics` key  
**TTL:** 5–10 minutes  
**Eviction trigger:** none (TTL-only expiry)  
**Reason:** This endpoint runs multiple aggregation queries across `bookings`, `tickets`, and `events` simultaneously. The numbers (total revenue, check-in rate, event counts by status) do not need to be real-time — an admin reviewing platform health is well-served by data that is a few minutes old. Caching eliminates repeated full-aggregate scans on every dashboard refresh.

---

#### `GET /api/v1/organizer/stats`
**Cache:** yes — `organizer-stats:{userId}` key (per-user)  
**TTL:** 2–5 minutes  
**Eviction trigger:** when a booking is confirmed for one of the organiser's events  
**Reason:** The stat cards at the top of the Organiser Console (total events, published count, tickets sold, total revenue) are aggregated from `bookings` and `events`. Per-user caching ensures one organiser's cache does not affect another's. TTL-based expiry covers edge cases (cancellations, admin actions) without needing exhaustive eviction logic.

---

#### QR → ticket lookup *(already cached)*
**Cache:** `tickets-by-qr:{qrCode}` key  
**TTL:** 5 minutes (current)  
**Eviction trigger:** after successful check-in (`markCheckedIn`)  
**Reason:** Introduced to absorb repeat scans during event check-in bursts. A single ticket may be scanned multiple times in quick succession (retry, duplicate scan). Hitting the database on every scan at scale would saturate the connection pool. The optimistic `UPDATE WHERE status = VALID` at the write layer guarantees correctness even if the cached view is slightly stale.

---

### Endpoints NOT to cache

| Endpoint | Reason |
|---|---|
| `GET /api/v1/events/{id}/tiers` | `availableCapacity` changes with every booking. A dedicated tier endpoint must reflect current capacity — a buyer who sees the wrong number before checkout has a poor experience. |
| `GET /api/v1/organizer/events` | Organisers create, edit, and submit events and expect to see changes immediately. Caching this would create the appearance of a broken UI. |
| `GET /api/v1/me/bookings` | Personal data that changes on every booking and cancellation. Staleness here directly misleads the user about their own purchases. |
| `GET /api/v1/me/tickets` | Personal, changes after check-in. A ticket showing `VALID` when it is `USED` is a significant UX and trust issue. |
| All admin moderation lists | Admins make approval decisions based on this data. Stale lists could cause double-approvals or missed submissions. |
| All write endpoints (POST / PATCH / DELETE) | Not applicable — write operations should never be cached. |

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
