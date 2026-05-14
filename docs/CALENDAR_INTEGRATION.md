# Google Calendar Integration for Booking Confirmations

**Feature:** Add event to Google Calendar (or default calendar app) from booking confirmation email.

**Scope:** M3+ (Email notifications already in place via Kafka consumer)

---

## Overview

When a user books a ticket and receives a confirmation email, the email will include:
1. **Option 1:** Google Calendar quick-add link (one-click)
2. **Option 2:** iCalendar (.ics) file attachment (works with any calendar app)
3. **Option 3:** Both (recommended)

### User Experience

```
Booking confirmed → Email sent with calendar invite
User clicks "Add to Google Calendar" → Calendar app opens with event pre-filled
User confirms → Event added to their calendar
```

---

## Implementation Approach

### Option A: Google Calendar URL (Recommended - Simple)

**Pros:**
- No authentication needed
- One-click from email
- Works on mobile
- No attachment file size

**Cons:**
- Relies on user's browser having Google Calendar
- URL length limits (~2000 chars)

**URL Pattern:**
```
https://calendar.google.com/calendar/render?action=TEMPLATE&text={title}&dates={startDate}T{startTime}Z/{endDate}T{endTime}Z&details={description}&location={location}
```

**Example:**
```
https://calendar.google.com/calendar/render
  ?action=TEMPLATE
  &text=TechConf+2026+-+VIP+Ticket
  &dates=20260620T100000Z/20260620T170000Z
  &details=Booking+ID%3A+abc-123%0ATicket+Code%3A+EV-ABC123
  &location=Lekki+Convention+Centre%2C+Lagos
```

### Option B: iCalendar (.ics) File (Recommended - Universal)

**Pros:**
- Works with all calendar apps (Google, Outlook, Apple, etc.)
- Can include reminders, recurring events
- No URL length limits
- Professional standard

**Cons:**
- File attachment (slight overhead)
- Requires client to open/import file

**iCalendar Format:**
```ics
BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//EventsNest//EventsNest 1.0//EN
CALSCALE:GREGORIAN
METHOD:PUBLISH
BEGIN:VEVENT
UID:booking-abc-123@eventsnest.com
DTSTAMP:20260514T120000Z
DTSTART:20260620T100000Z
DTEND:20260620T170000Z
SUMMARY:TechConf 2026 - VIP Ticket
DESCRIPTION:Booking ID: abc-123\nTicket Code: EV-ABC123\nTicket Type: VIP\nQuantity: 2
LOCATION:Lekki Convention Centre, Lagos
ORGANIZER;CN=EventsNest:noreply@eventsnest.com
ATTENDEE;CN=User Name:user@example.com
BEGIN:VALARM
TRIGGER:-PT24H
ACTION:DISPLAY
DESCRIPTION:TechConf 2026 starting tomorrow
END:VALARM
END:VEVENT
END:VCALENDAR
```

### Option C: Both (Best UX)

- **Email body:** "Add to Calendar" button linking to Google Calendar URL
- **Email attachment:** .ics file for users with other calendar apps or offline import

---

## Implementation Steps

### Step 1: Create Calendar Event Data Class

```java
@Data
public class CalendarEventData {
    private String eventTitle;           // "TechConf 2026 - VIP Ticket"
    private String eventDescription;     // Booking details
    private LocalDateTime startTime;     // Event start
    private LocalDateTime endTime;       // Event end
    private String location;             // Venue address
    private String organizerEmail;       // organizer@eventsnest.com
    private String attendeeEmail;        // User's email
    private String attendeeName;         // User's name
    private String bookingId;            // For UID uniqueness
    private String ticketCode;           // Short ticket code
    
    // Build from Booking + Event entities
    public static CalendarEventData from(
            Booking booking, 
            Event event, 
            User user,
            List<Ticket> tickets) {
        
        CalendarEventData data = new CalendarEventData();
        data.setEventTitle(event.getTitle() + " - " + 
            booking.getTier().getName() + " Ticket");
        data.setStartTime(event.getStartDate());
        data.setEndTime(event.getEndDate());
        data.setLocation(event.getLocation());
        data.setOrganizerEmail("noreply@eventsnest.com");
        data.setAttendeeEmail(user.getEmail());
        data.setAttendeeName(user.getFirstName() + " " + user.getLastName());
        data.setBookingId(booking.getId().toString());
        data.setTicketCode(tickets.get(0).getShortCode());
        
        // Description with ticket details
        StringBuilder desc = new StringBuilder();
        desc.append("Booking ID: ").append(booking.getId()).append("\n");
        desc.append("Event: ").append(event.getTitle()).append("\n");
        desc.append("Ticket Tier: ").append(booking.getTier().getName()).append("\n");
        desc.append("Quantity: ").append(booking.getQuantity()).append("\n");
        desc.append("Total Paid: ₦").append(booking.getTotalPrice() / 100).append("\n");
        desc.append("\nTicket Codes:\n");
        for (Ticket ticket : tickets) {
            desc.append("- ").append(ticket.getShortCode()).append("\n");
        }
        data.setEventDescription(desc.toString());
        
        return data;
    }
}
```

### Step 2: Create Calendar Service

```java
@Service
public class CalendarService {
    
    /**
     * Generate Google Calendar URL for quick-add
     */
    public String generateGoogleCalendarUrl(CalendarEventData data) {
        try {
            // Format dates: YYYYMMDDTHHMMSSZ
            String startDate = formatRFC5545(data.getStartTime());
            String endDate = formatRFC5545(data.getEndTime());
            
            // Build URL with parameters
            String baseUrl = "https://calendar.google.com/calendar/render";
            Map<String, String> params = new LinkedHashMap<>();
            params.put("action", "TEMPLATE");
            params.put("text", data.getEventTitle());
            params.put("dates", startDate + "/" + endDate);
            params.put("details", data.getEventDescription());
            params.put("location", data.getLocation());
            params.put("ctz", "Africa/Lagos");  // EventsNest timezone
            
            // Build query string
            String queryString = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), "UTF-8"))
                .collect(Collectors.joining("&"));
            
            return baseUrl + "?" + queryString;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Failed to generate calendar URL", e);
        }
    }
    
    /**
     * Generate iCalendar (.ics) file content
     */
    public String generateICalendarContent(CalendarEventData data) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        String timestamp = formatRFC5545(now);
        String startDate = formatRFC5545(data.getStartTime());
        String endDate = formatRFC5545(data.getEndTime());
        
        // Generate unique UID (prevents duplicates if imported multiple times)
        String uid = String.format("booking-%s@eventsnest.com", data.getBookingId());
        
        // Build iCalendar content
        StringBuilder ics = new StringBuilder();
        ics.append("BEGIN:VCALENDAR\r\n");
        ics.append("VERSION:2.0\r\n");
        ics.append("PRODID:-//EventsNest//EventsNest 1.0//EN\r\n");
        ics.append("CALSCALE:GREGORIAN\r\n");
        ics.append("METHOD:PUBLISH\r\n");
        ics.append("X-WR-TIMEZONE:Africa/Lagos\r\n");
        
        ics.append("BEGIN:VEVENT\r\n");
        ics.append(String.format("UID:%s\r\n", uid));
        ics.append(String.format("DTSTAMP:%s\r\n", timestamp));
        ics.append(String.format("DTSTART:%s\r\n", startDate));
        ics.append(String.format("DTEND:%s\r\n", endDate));
        ics.append(String.format("SUMMARY:%s\r\n", escapeICalendarText(data.getEventTitle())));
        ics.append(String.format("DESCRIPTION:%s\r\n", escapeICalendarText(data.getEventDescription())));
        ics.append(String.format("LOCATION:%s\r\n", escapeICalendarText(data.getLocation())));
        ics.append(String.format("ORGANIZER;CN=EventsNest:mailto:%s\r\n", data.getOrganizerEmail()));
        ics.append(String.format("ATTENDEE;CN=%s:mailto:%s\r\n", 
            data.getAttendeeName(), 
            data.getAttendeeEmail()));
        ics.append("SEQUENCE:0\r\n");
        ics.append("STATUS:CONFIRMED\r\n");
        
        // Add reminder: 24 hours before
        ics.append("BEGIN:VALARM\r\n");
        ics.append("TRIGGER:-PT24H\r\n");
        ics.append("ACTION:DISPLAY\r\n");
        ics.append(String.format("DESCRIPTION:%s starting tomorrow\r\n", 
            escapeICalendarText(data.getEventTitle())));
        ics.append("END:VALARM\r\n");
        
        ics.append("END:VEVENT\r\n");
        ics.append("END:VCALENDAR\r\n");
        
        return ics.toString();
    }
    
    /**
     * Format LocalDateTime to RFC5545 format (YYYYMMDDTHHMMSSZ)
     */
    private String formatRFC5545(LocalDateTime dateTime) {
        ZonedDateTime utc = dateTime.atZone(ZoneId.of("Africa/Lagos"))
            .withZoneSameInstant(ZoneId.of("UTC"));
        return utc.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
    }
    
    /**
     * Escape special characters for iCalendar format
     */
    private String escapeICalendarText(String text) {
        if (text == null) return "";
        return text
            .replace("\\", "\\\\")
            .replace(",", "\\,")
            .replace(";", "\\;")
            .replace("\n", "\\n");
    }
}
```

### Step 3: Update Email Service to Include Calendar

```java
@Service
public class EmailService {
    
    @Autowired
    private CalendarService calendarService;
    
    @Autowired
    private JavaMailSender mailSender;
    
    /**
     * Send booking confirmation email with calendar invite
     */
    public void sendBookingConfirmation(
            User user,
            Booking booking,
            Event event,
            List<Ticket> tickets) {
        
        try {
            // Build calendar event data
            CalendarEventData calendarData = CalendarEventData.from(
                booking, event, user, tickets);
            
            // Generate calendar assets
            String googleCalendarUrl = calendarService.generateGoogleCalendarUrl(calendarData);
            String iCalendarContent = calendarService.generateICalendarContent(calendarData);
            
            // Build email
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setTo(user.getEmail());
            helper.setFrom("noreply@eventsnest.com", "EventsNest");
            helper.setSubject("✓ Booking Confirmed: " + event.getTitle());
            
            // Build HTML email body with calendar button
            String htmlBody = buildBookingConfirmationHtml(
                user, booking, event, tickets, googleCalendarUrl);
            helper.setText(htmlBody, true);
            
            // Attach .ics file
            helper.addAttachment(
                "calendar-event.ics",
                new ByteArrayResource(iCalendarContent.getBytes(StandardCharsets.UTF_8)),
                "text/calendar;charset=UTF-8"
            );
            
            // Send email
            mailSender.send(message);
            
        } catch (MessagingException e) {
            log.error("Failed to send booking confirmation email", e);
            throw new RuntimeException("Email send failed", e);
        }
    }
    
    private String buildBookingConfirmationHtml(
            User user,
            Booking booking,
            Event event,
            List<Ticket> tickets,
            String googleCalendarUrl) {
        
        return "<html><body style=\"font-family: Arial, sans-serif;\">" +
            "<h2>✓ Booking Confirmed!</h2>" +
            "<p>Hi " + user.getFirstName() + ",</p>" +
            "<p>Your booking for <strong>" + event.getTitle() + "</strong> has been confirmed.</p>" +
            
            "<h3>Booking Details</h3>" +
            "<table style=\"border-collapse: collapse; width: 100%; margin: 20px 0;\">" +
            "<tr><td style=\"border: 1px solid #ddd; padding: 10px;\"><strong>Event:</strong></td>" +
            "<td style=\"border: 1px solid #ddd; padding: 10px;\">" + event.getTitle() + "</td></tr>" +
            "<tr><td style=\"border: 1px solid #ddd; padding: 10px;\"><strong>Date:</strong></td>" +
            "<td style=\"border: 1px solid #ddd; padding: 10px;\">" + 
                formatDate(event.getStartDate()) + "</td></tr>" +
            "<tr><td style=\"border: 1px solid #ddd; padding: 10px;\"><strong>Location:</strong></td>" +
            "<td style=\"border: 1px solid #ddd; padding: 10px;\">" + event.getLocation() + "</td></tr>" +
            "<tr><td style=\"border: 1px solid #ddd; padding: 10px;\"><strong>Ticket Tier:</strong></td>" +
            "<td style=\"border: 1px solid #ddd; padding: 10px;\">" + 
                booking.getTier().getName() + "</td></tr>" +
            "<tr><td style=\"border: 1px solid #ddd; padding: 10px;\"><strong>Total Paid:</strong></td>" +
            "<td style=\"border: 1px solid #ddd; padding: 10px;\">₦" + 
                (booking.getTotalPrice() / 100) + "</td></tr>" +
            "</table>" +
            
            "<h3>Your Tickets</h3>" +
            "<ul>";
        
        for (Ticket ticket : tickets) {
            html += "<li><strong>" + ticket.getShortCode() + "</strong> " +
                    "(QR: " + ticket.getQrCode().substring(0, 10) + "...)</li>";
        }
        
        html += "</ul>" +
            
            "<h3>Add to Your Calendar</h3>" +
            "<p>" +
            "  <a href=\"" + googleCalendarUrl + "\" " +
            "     style=\"display: inline-block; padding: 12px 24px; " +
            "            background-color: #4285F4; color: white; " +
            "            text-decoration: none; border-radius: 4px; " +
            "            font-weight: bold;\">" +
            "    📅 Add to Google Calendar" +
            "  </a>" +
            "</p>" +
            "<p style=\"color: #666; font-size: 12px;\">" +
            "Alternatively, a calendar file (calendar-event.ics) is attached. " +
            "You can import it into Outlook, Apple Calendar, or any calendar app." +
            "</p>" +
            
            "<h3>Next Steps</h3>" +
            "<ol>" +
            "<li>Save your ticket codes above</li>" +
            "<li>Download and present QR code at check-in</li>" +
            "<li>Arrive 30 minutes early for the event</li>" +
            "</ol>" +
            
            "<p style=\"color: #999; font-size: 12px; margin-top: 40px;\">" +
            "Questions? Reply to this email or visit eventsnest.com/support" +
            "</p>" +
            "</body></html>";
        
        return html;
    }
    
    private String formatDate(LocalDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a"));
    }
}
```

### Step 4: Update Kafka Consumer

```java
@Service
public class BookingConfirmedConsumer {
    
    @Autowired
    private BookingService bookingService;
    
    @Autowired
    private EventService eventService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private TicketService ticketService;
    
    @Autowired
    private EmailService emailService;
    
    @KafkaListener(topics = "booking.confirmed", groupId = "notifications")
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        try {
            // Load entities
            Booking booking = bookingService.getById(event.getBookingId());
            Event eventObj = eventService.getById(event.getEventId());
            User user = userService.getById(event.getUserId());
            List<Ticket> tickets = ticketService.getByBookingId(booking.getId());
            
            // Send email WITH calendar invite
            emailService.sendBookingConfirmation(user, booking, eventObj, tickets);
            
            // Persist notification
            persistedNotificationRepository.save(new PersistedNotification(
                event.getEventId(),
                event.getUserId(),
                NotificationType.EMAIL,
                user.getEmail(),
                "Booking Confirmed: " + eventObj.getTitle(),
                "Calendar invite attached",
                PersistedNotificationStatus.SENT,
                LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Failed to send booking confirmation with calendar", e);
            // Persist as FAILED for retry
            persistedNotificationRepository.save(new PersistedNotification(
                event.getEventId(),
                event.getUserId(),
                NotificationType.EMAIL,
                null,
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

### Step 5: Dependencies (pom.xml)

```xml
<!-- Already included in Spring Boot -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>

<!-- For JavaMail MIME support -->
<dependency>
    <groupId>javax.mail</groupId>
    <artifactId>javax.mail-api</artifactId>
</dependency>

<dependency>
    <groupId>com.sun.mail</groupId>
    <artifactId>javax.mail</artifactId>
</dependency>
```

---

## Email Template (HTML)

The email will look like:

```
┌─────────────────────────────────────────┐
│  ✓ Booking Confirmed!                   │
│                                         │
│  Hi John,                               │
│  Your booking for TechConf 2026 has    │
│  been confirmed.                        │
│                                         │
│  📋 BOOKING DETAILS                     │
│  ┌─────────────────────────────────┐   │
│  │ Event: TechConf 2026            │   │
│  │ Date: Friday, June 20, 2026     │   │
│  │ Location: Lekki Conv. Centre    │   │
│  │ Ticket: VIP                     │   │
│  │ Total: ₦50,000                  │   │
│  └─────────────────────────────────┘   │
│                                         │
│  🎟️ YOUR TICKETS                        │
│  • EV-ABC123 (QR: 5a7b9c...)           │
│  • EV-DEF456 (QR: 3k2m9x...)           │
│                                         │
│  ╔═════════════════════════════════╗   │
│  ║ 📅 Add to Google Calendar        ║   │
│  ╚═════════════════════════════════╝   │
│                                         │
│  Or import calendar-event.ics into     │
│  Outlook, Apple Calendar, etc.        │
│                                         │
│  ✓ Save your ticket codes              │
│  ✓ Download QR code                    │
│  ✓ Arrive 30 minutes early             │
│                                         │
└─────────────────────────────────────────┘
```

---

## Testing

### Unit Test: Calendar Service

```java
@Test
void testGenerateGoogleCalendarUrl() {
    CalendarEventData data = new CalendarEventData();
    data.setEventTitle("TechConf 2026 - VIP");
    data.setStartTime(LocalDateTime.of(2026, 6, 20, 10, 0, 0));
    data.setEndTime(LocalDateTime.of(2026, 6, 20, 17, 0, 0));
    data.setLocation("Lekki Convention Centre");
    
    String url = calendarService.generateGoogleCalendarUrl(data);
    
    assertTrue(url.contains("action=TEMPLATE"));
    assertTrue(url.contains("text=TechConf"));
    assertTrue(url.contains("20260620T100000Z"));
}

@Test
void testGenerateICalendarContent() {
    CalendarEventData data = new CalendarEventData();
    data.setEventTitle("TechConf 2026");
    data.setStartTime(LocalDateTime.of(2026, 6, 20, 10, 0, 0));
    data.setEndTime(LocalDateTime.of(2026, 6, 20, 17, 0, 0));
    data.setLocation("Lekki");
    data.setBookingId("abc-123");
    
    String ics = calendarService.generateICalendarContent(data);
    
    assertTrue(ics.contains("BEGIN:VCALENDAR"));
    assertTrue(ics.contains("booking-abc-123@eventsnest.com"));
    assertTrue(ics.contains("SUMMARY:TechConf 2026"));
    assertTrue(ics.contains("BEGIN:VALARM"));  // Reminder
}
```

### Integration Test: Email with Calendar

```java
@SpringBootTest
@Import(IntegrationTestConfig.class)
class BookingConfirmedConsumerTest {
    
    @MockBean
    private JavaMailSender mailSender;
    
    @Test
    void testBookingConfirmedEmailIncludesCalendar() {
        // Setup
        User user = new User(...);
        Booking booking = new Booking(...);
        Event event = new Event(...);
        List<Ticket> tickets = List.of(new Ticket(...));
        
        // Send consumer event
        BookingConfirmedEvent event = new BookingConfirmedEvent(...);
        consumer.onBookingConfirmed(event);
        
        // Verify email sent with attachment
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        
        MimeMessage message = captor.getValue();
        MimeMultipart content = (MimeMultipart) message.getContent();
        
        // Verify .ics attachment exists
        assertTrue(hasAttachment(content, "calendar-event.ics"));
        
        // Verify HTML body has calendar button
        String body = getBodyText(content);
        assertTrue(body.contains("Add to Google Calendar"));
    }
}
```

---

## Configuration (application.yml)

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
            required: true

eventsnest:
  mail:
    from: noreply@eventsnest.com
    from-name: EventsNest
  calendar:
    timezone: Africa/Lagos
    organizer-email: noreply@eventsnest.com
```

---

## Rollout Plan

### Phase 1: Booking Confirmation (Current)
- [x] Add calendar to booking.confirmed emails
- [ ] Test with real bookings
- [ ] Monitor email delivery

### Phase 2: Manager Events (M3)
- Add calendar to manager panel notifications

### Phase 3: Contract Signing (M5)
- Add calendar to contract signing confirmations
- Milestone release reminders

---

## Alternatives Considered

| Approach | Pros | Cons |
|----------|------|------|
| **Google Calendar URL** (Current) | Simple, one-click | Requires Google Calendar |
| **iCalendar file** (Current) | Universal, offline | File import required |
| **Both** (Recommended) | Best UX | Slight email size increase |
| **Email invite header** | Professional | Limited provider support |
| **SMS link** | Mobile-first | Long URLs, harder to scan |

---

## References

- [Google Calendar Intent-based URLs](https://developers.google.com/calendar/web-publish)
- [RFC 5545 iCalendar Format](https://tools.ietf.org/html/rfc5545)
- [Spring Mail Documentation](https://spring.io/guides/gs/sending-email/)

---

**Status:** Design Phase | **Estimated Effort:** 3-4 days (implementation + testing) | **Dependencies:** Email service (already in place)
