# EventsNest API Patterns & Conventions

**Version:** 4.0 | **Framework:** Spring Boot 3 | **Protocol:** REST + Kafka

This document defines reusable patterns for building endpoints that align with EventsNest's architectural decisions.

---

## Pattern 1: Event-Scoped Authorization (D5)

**Every endpoint that touches an event must check membership.**

### Template

```java
@RestController
@RequestMapping("/api/v1/organizer/events/{eventId}")
public class EventManagementController {

  @PatchMapping
  public ResponseEntity<EventDTO> updateEvent(
      @PathVariable UUID eventId,
      @RequestBody UpdateEventRequest request,
      @AuthenticationPrincipal UserDetails userDetails) {
    
    UUID userId = UUID.fromString(userDetails.getUsername());
    
    // REQUIRED: Check membership before any business logic
    membershipService.ensureRole(userId, eventId, EventRole.ORGANIZER)
        .orElseThrow(() -> new ForbiddenException(
            "Only organizers can update this event"));
    
    // Now safe to proceed
    Event updated = eventService.update(eventId, request);
    return ResponseEntity.ok(EventDTO.from(updated));
  }
}
```

### Rules

1. **Extract userId from JWT** — Always from `@AuthenticationPrincipal UserDetails`
2. **Call membershipService.ensureRole()** — BEFORE any business logic
3. **Throw ForbiddenException on failure** — Returns HTTP 403
4. **No @PreAuthorize with event roles** — These cannot resolve event context correctly

### Authorization for Different Roles

```java
// Organizer-only
membershipService.ensureRole(userId, eventId, EventRole.ORGANIZER);

// Manager-only
membershipService.ensureRole(userId, eventId, EventRole.MANAGER);

// Attendee (must have booked)
membershipService.ensureRole(userId, eventId, EventRole.ATTENDEE);

// Check-in staff
membershipService.ensureRole(userId, eventId, EventRole.CHECKIN_STAFF);

// Multiple roles (OR logic)
if (!membershipService.hasRole(userId, eventId, EventRole.ORGANIZER)
    && !membershipService.hasRole(userId, eventId, EventRole.MANAGER)) {
    throw new ForbiddenException("Insufficient permissions");
}
```

---

## Pattern 2: Optimistic Locking (D6)

**Use `@Version` on entities with concurrent mutations.**

### Template

```java
@Entity
@Table(name = "ticket_tiers")
public class TicketTier {
  
  @Id
  private UUID id;
  
  private Integer availableCapacity;
  
  @Version
  private Long version;  // Optimistic lock field
}

// Service layer
@Service
public class BookingService {
  
  @Transactional
  public Booking createBooking(CreateBookingRequest request) {
    TicketTier tier = tierRepository.findById(request.getTierId())
        .orElseThrow(() -> new NotFoundException("Tier not found"));
    
    // Check capacity before decrement
    if (tier.getAvailableCapacity() <= 0) {
      throw new ConflictException("No capacity available");
    }
    
    // Decrement (uses @Version)
    tier.setAvailableCapacity(tier.getAvailableCapacity() - request.getQuantity());
    
    // JPA compares version at flush time
    try {
      tierRepository.save(tier);  // May throw OptimisticLockingFailureException
    } catch (OptimisticLockingFailureException e) {
      // Another request modified this tier concurrently
      throw new ConflictException("Tier capacity changed, please retry", e);
    }
    
    // Rest of booking logic
    return bookingRepository.save(booking);
  }
}
```

### Exception Handling

```java
@RestControllerAdvice
public class ExceptionHandler {
  
  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<?> handleOptimisticLocking(
      OptimisticLockingFailureException ex) {
    return ResponseEntity.status(409).body(new ErrorResponse(
        "Concurrent modification detected. Please retry.",
        "CONFLICT",
        null
    ));
  }
}
```

### When to Use @Version

- **Ticket tier capacity** — Concurrent bookings
- **Bookings (payment deduplication)** — Concurrent Monnify webhooks
- **Any field mutated by multiple concurrent requests**

### When NOT to Use

- Read-only fields
- Historical/audit fields
- Fields that never conflict (e.g., event title)

---

## Pattern 3: Kafka Event Publishing (D4)

**Fire domain events for async notifications.**

### Kafka Topics (LOCKED)

```
booking.confirmed     — Fired on booking confirmation
ticket.checked-in     — Fired on check-in
contract.signed       — Fired on contract signature
milestone.released    — Fired on escrow milestone release
```

### Template

```java
@Service
public class BookingService {
  
  @Autowired
  private KafkaTemplate<String, BookingConfirmedEvent> kafkaTemplate;
  
  @Transactional
  public Booking confirmBooking(UUID bookingId) {
    Booking booking = repository.findById(bookingId).orElseThrow();
    
    // 1. Mark booking CONFIRMED in database
    booking.setStatus(BookingStatus.CONFIRMED);
    booking = repository.save(booking);
    
    // 2. Publish event (fires notification consumer)
    BookingConfirmedEvent event = new BookingConfirmedEvent(
        bookingId,
        booking.getUserId(),
        booking.getEventId(),
        booking.getTotalPrice()
    );
    kafkaTemplate.send("booking.confirmed", event);
    
    return booking;
  }
}

// Event definition
@Data
public class BookingConfirmedEvent {
  public String bookingId;
  public String userId;
  public String eventId;
  public Long totalPrice;
}
```

### Consumer Template

```java
@Service
public class BookingConfirmedConsumer {
  
  @KafkaListener(topics = "booking.confirmed", groupId = "notifications")
  public void onBookingConfirmed(BookingConfirmedEvent event) {
    try {
      // 1. Load context (user, booking, event)
      User user = userService.getById(event.getUserId());
      Booking booking = bookingService.getById(event.getBookingId());
      Event eventObj = eventService.getById(event.getEventId());
      
      // 2. Send notification
      String subject = "Booking Confirmed: " + eventObj.getTitle();
      String body = "Your booking for " + eventObj.getTitle() + " has been confirmed.\n"
          + "Booking ID: " + booking.getId() + "\n"
          + "Total: ₦" + (event.getTotalPrice() / 100);
      
      emailService.send(user.getEmail(), subject, body);
      
      // 3. Persist notification for audit
      persistedNotificationRepository.save(new PersistedNotification(
          event.getEventId(),
          event.getUserId(),
          NotificationType.EMAIL,
          user.getEmail(),
          subject,
          body,
          PersistedNotificationStatus.SENT,
          LocalDateTime.now()
      ));
      
    } catch (Exception e) {
      log.error("Failed to send booking confirmation email", e);
      // Persist as FAILED (retry logic handles retries)
      persistedNotificationRepository.save(new PersistedNotification(
          event.getEventId(),
          event.getUserId(),
          NotificationType.EMAIL,
          user.getEmail(),
          "Booking Confirmed",
          "...",
          PersistedNotificationStatus.FAILED,
          null,
          e.getMessage()
      ));
    }
  }
}
```

### Rules

1. **Publish events AFTER transaction commits** — Use `@Transactional` (Spring ensures this)
2. **Events are immutable** — No state changes after publishing
3. **Consumers must be idempotent** — Handle duplicate messages
4. **Always persist notifications** — For audit and replay
5. **No synchronous email/SMS** — Always fire event, let consumer handle delivery

---

## Pattern 4: Monnify Webhook Verification (D7)

**HMAC-verify all incoming webhooks.**

### Template

```java
@RestController
@RequestMapping("/api/v1/payments/monnify")
public class MonnifyWebhookController {
  
  @Autowired
  private MonnifyService monnifyService;
  
  @Autowired
  private BookingService bookingService;
  
  @PostMapping("/webhook")
  public ResponseEntity<?> handleWebhook(
      @RequestBody String payload,
      @RequestHeader("Monnify-Signature") String signature) {
    
    // 1. Verify HMAC
    String computedSignature = HmacUtils.sha512Hex(payload, MONNIFY_SECRET);
    if (!constantTimeEquals(signature, computedSignature)) {
      log.warn("Webhook signature mismatch. Rejecting.");
      return ResponseEntity.status(401).build();
    }
    
    // 2. Parse webhook
    MonnifyWebhookPayload webhook = objectMapper.readValue(
        payload,
        MonnifyWebhookPayload.class
    );
    
    // 3. Check if already processed (idempotency)
    String txRef = webhook.getTransactionReference();
    if (bookingService.hasProcessedMonnifyRef(txRef)) {
      log.debug("Webhook already processed: {}", txRef);
      return ResponseEntity.ok().build();  // Idempotent
    }
    
    // 4. Process payment result
    if (webhook.getStatus().equals("SUCCESSFUL")) {
      Booking booking = bookingService.getByMonnifyRef(txRef);
      booking.setStatus(BookingStatus.CONFIRMED);
      bookingService.save(booking);
      
      // Fire Kafka event (notification consumer will send email)
      bookingService.publishBookingConfirmed(booking);
    } else if (webhook.getStatus().equals("FAILED")) {
      Booking booking = bookingService.getByMonnifyRef(txRef);
      booking.setStatus(BookingStatus.FAILED);
      bookingService.save(booking);
    }
    
    return ResponseEntity.ok().build();
  }
  
  // Constant-time string comparison (prevent timing attacks)
  private boolean constantTimeEquals(String a, String b) {
    byte[] aBytes = a.getBytes();
    byte[] bBytes = b.getBytes();
    if (aBytes.length != bBytes.length) return false;
    
    int result = 0;
    for (int i = 0; i < aBytes.length; i++) {
      result |= aBytes[i] ^ bBytes[i];
    }
    return result == 0;
  }
}
```

### Rules

1. **Always verify signature** — Never skip, even in tests
2. **Use constant-time comparison** — Prevents timing attacks
3. **Check for duplicate refs** — UNIQUE constraint + query check
4. **Return 200 for all webhooks** — Even failures (Monnify will retry)
5. **Log everything** — For debugging webhook issues

---

## Pattern 5: Error Response Format

**All errors return consistent JSON.**

### Template

```java
// Exception classes
public class EventsNestException extends RuntimeException {
  private String code;
  private Map<String, String> fieldErrors;
  
  public EventsNestException(String message, String code) {
    super(message);
    this.code = code;
  }
}

public class ValidationException extends EventsNestException {
  private Map<String, String> fieldErrors;
  
  public ValidationException(Map<String, String> fieldErrors) {
    super("Validation failed", "VALIDATION_ERROR");
    this.fieldErrors = fieldErrors;
  }
}

public class ForbiddenException extends EventsNestException {
  public ForbiddenException(String message) {
    super(message, "FORBIDDEN");
  }
}

public class ConflictException extends EventsNestException {
  public ConflictException(String message) {
    super(message, "CONFLICT");
  }
}

// Global exception handler
@RestControllerAdvice
public class GlobalExceptionHandler {
  
  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<?> handleValidation(ValidationException ex) {
    return ResponseEntity.status(400).body(new ErrorResponse(
        ex.getMessage(),
        ex.getCode(),
        ex.getFieldErrors()
    ));
  }
  
  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<?> handleForbidden(ForbiddenException ex) {
    return ResponseEntity.status(403).body(new ErrorResponse(
        ex.getMessage(),
        ex.getCode(),
        null
    ));
  }
  
  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<?> handleConflict(ConflictException ex) {
    return ResponseEntity.status(409).body(new ErrorResponse(
        ex.getMessage(),
        ex.getCode(),
        null
    ));
  }
  
  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> handleGeneric(Exception ex) {
    log.error("Unhandled exception", ex);
    return ResponseEntity.status(500).body(new ErrorResponse(
        "Internal server error",
        "INTERNAL_ERROR",
        null
    ));
  }
}

// Response DTO
@Data
public class ErrorResponse {
  private String error;
  private String code;
  private LocalDateTime timestamp;
  private String path;
  private String traceId;  // Correlation ID
  private Map<String, String> fieldErrors;
  
  public ErrorResponse(String error, String code, Map<String, String> fieldErrors) {
    this.error = error;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.timestamp = LocalDateTime.now(ZoneId.of("UTC"));
  }
}
```

### Error Code Reference

```
VALIDATION_ERROR   → 400
NOT_FOUND          → 404
UNAUTHORIZED       → 401
FORBIDDEN          → 403
CONFLICT           → 409 (overbooking, duplicate check-in, etc.)
UNPROCESSABLE      → 422 (state violation)
INTERNAL_ERROR     → 500
```

---

## Pattern 6: DTOs & Mapping

**Separate DTOs from entities. Never expose JPA entities directly.**

### Template

```java
// Entity (database)
@Entity
@Table(name = "events")
public class Event {
  @Id
  private UUID id;
  private String title;
  private LocalDateTime startDate;
  @JsonIgnore
  private String passwordHash;  // NEVER expose
}

// DTO (API response)
@Data
public class EventDTO {
  public UUID id;
  public String title;
  public LocalDateTime startDate;
  // No passwordHash field
  
  public static EventDTO from(Event entity) {
    EventDTO dto = new EventDTO();
    dto.id = entity.getId();
    dto.title = entity.getTitle();
    dto.startDate = entity.getStartDate();
    return dto;
  }
}

// Usage
@GetMapping("/events/{id}")
public ResponseEntity<EventDTO> getEvent(@PathVariable UUID id) {
  Event event = eventService.getById(id);
  return ResponseEntity.ok(EventDTO.from(event));  // Never return Event directly
}
```

### Rules

1. **Always use DTOs** — Never return @Entity from controller
2. **Map explicitly** — Use `.from()` static factory or MapStruct
3. **Don't expose sensitive fields** — passwords, API keys, internal IDs
4. **DTO for input too** — `@RequestBody CreateEventRequest` (not `Event`)

---

## Pattern 7: Transaction Boundaries

**Use `@Transactional` for atomic operations.**

### Template

```java
@Service
public class BookingService {
  
  // Read-only query
  @Transactional(readOnly = true)
  public BookingDTO getById(UUID id) {
    return bookingRepository.findById(id)
        .map(BookingDTO::from)
        .orElseThrow(() -> new NotFoundException("Booking not found"));
  }
  
  // Write operation (with Kafka event)
  @Transactional
  public Booking confirmBooking(UUID bookingId, MonnifyPaymentDetails details) {
    Booking booking = bookingRepository.findById(bookingId).orElseThrow();
    
    // 1. Update booking (within transaction)
    booking.setStatus(BookingStatus.CONFIRMED);
    booking.setMonnifyRef(details.getTransactionRef());
    bookingRepository.save(booking);
    
    // 2. Issue tickets (within transaction)
    for (int i = 0; i < booking.getQuantity(); i++) {
      Ticket ticket = new Ticket(booking, generateQrCode(), generateShortCode());
      ticketRepository.save(ticket);
    }
    
    // 3. Fire Kafka event (AFTER transaction commits)
    kafkaTemplate.send("booking.confirmed", new BookingConfirmedEvent(
        booking.getId(), booking.getEventId(), booking.getUserId()
    ));
    
    return booking;
  }
}
```

### Rules

1. **Use `@Transactional`** on service methods (not controllers)
2. **`readOnly=true`** for queries (can optimize connection pool)
3. **Kafka events published AFTER transaction** — Spring ensures this
4. **Catch OptimisticLockingFailureException** — Handle concurrency conflicts

---

## Summary: Pattern Checklist

When building a new endpoint:

- [ ] **Authorization** — Check membership with `ensureRole()`
- [ ] **Validation** — Validate input in service layer, throw `ValidationException`
- [ ] **Optimistic Locking** — Use `@Version` if concurrent mutations possible
- [ ] **Idempotency** — Check for duplicates (e.g., monnify_transaction_ref)
- [ ] **Kafka Events** — Publish domain events for notifications
- [ ] **Error Handling** — Throw specific exceptions (not generic `Exception`)
- [ ] **DTOs** — Map entities to DTOs, never return raw entities
- [ ] **Transactions** — Use `@Transactional` on service methods
- [ ] **Logging** — Include correlationId in logs
- [ ] **Testing** — Write integration tests with `@SpringBootTest @Import(IntegrationTestConfig.class)`

---

**Patterns Version:** 4.0 | **Last Updated:** May 2026 | **Status:** LOCKED

All new code must follow these patterns. Deviations require RFC.
