package group.moniepoint.eventsnestserver.calendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CalendarService Tests")
class CalendarServiceTest {

    private CalendarService calendarService;

    @BeforeEach
    void setUp() {
        calendarService = new CalendarService();
    }

    @Test
    @DisplayName("Should generate valid Google Calendar URL")
    void testGenerateGoogleCalendarUrl() {
        // Arrange
        CalendarEventData data = CalendarEventData.builder()
                .eventTitle("TechConf 2026 - VIP Ticket")
                .eventDescription("Your booking is confirmed")
                .startTime(LocalDateTime.of(2026, 6, 20, 10, 0, 0))
                .endTime(LocalDateTime.of(2026, 6, 20, 17, 0, 0))
                .location("Lekki Convention Centre, Lagos")
                .attendeeEmail("user@example.com")
                .attendeeName("John Doe")
                .bookingId("abc-123")
                .build();

        // Act
        String url = calendarService.generateGoogleCalendarUrl(data);

        // Assert
        assertTrue(url.contains("calendar.google.com"));
        assertTrue(url.contains("action=TEMPLATE"));
        assertTrue(url.contains("TechConf"));
        // Lagos is UTC+1, so 10:00 Lagos = 09:00 UTC, 17:00 Lagos = 16:00 UTC
        assertTrue(url.contains("20260620T090000Z"));
        assertTrue(url.contains("20260620T160000Z"));
    }

    @Test
    @DisplayName("Should generate valid iCalendar content")
    void testGenerateICalendarContent() {
        // Arrange
        CalendarEventData data = CalendarEventData.builder()
                .eventTitle("TechConf 2026 - VIP Ticket")
                .eventDescription("Your booking is confirmed")
                .startTime(LocalDateTime.of(2026, 6, 20, 10, 0, 0))
                .endTime(LocalDateTime.of(2026, 6, 20, 17, 0, 0))
                .location("Lekki Convention Centre")
                .attendeeEmail("user@example.com")
                .attendeeName("John Doe")
                .bookingId("abc-123")
                .build();

        // Act
        String ics = calendarService.generateICalendarContent(data);

        // Assert
        assertTrue(ics.contains("BEGIN:VCALENDAR"));
        assertTrue(ics.contains("END:VCALENDAR"));
        assertTrue(ics.contains("VERSION:2.0"));
        assertTrue(ics.contains("PRODID:-//EventsNest"));
        assertTrue(ics.contains("booking-abc-123@eventsnest.com"));
        assertTrue(ics.contains("SUMMARY:TechConf 2026 - VIP Ticket"));
        // Lagos is UTC+1, so 10:00 Lagos = 09:00 UTC, 17:00 Lagos = 16:00 UTC
        assertTrue(ics.contains("DTSTART:20260620T090000Z"));
        assertTrue(ics.contains("DTEND:20260620T160000Z"));
        assertTrue(ics.contains("BEGIN:VALARM"));  // Reminder alarm
        assertTrue(ics.contains("TRIGGER:-PT24H"));  // 24 hours before
    }

    @Test
    @DisplayName("Should escape special characters in iCalendar content")
    void testEscapeICalendarText() {
        // Arrange
        CalendarEventData data = CalendarEventData.builder()
                .eventTitle("Tech; Conference, 2026\\Advanced")
                .eventDescription("Line 1\nLine 2")
                .startTime(LocalDateTime.of(2026, 6, 20, 10, 0, 0))
                .endTime(LocalDateTime.of(2026, 6, 20, 17, 0, 0))
                .location("Location; Test")
                .attendeeEmail("user@example.com")
                .attendeeName("John Doe")
                .bookingId("abc-123")
                .build();

        // Act
        String ics = calendarService.generateICalendarContent(data);

        // Assert - special characters should be escaped
        assertTrue(ics.contains("Tech\\; Conference\\, 2026\\\\Advanced"));
        assertTrue(ics.contains("Line 1\\nLine 2"));
    }

    @Test
    @DisplayName("Should format date for email correctly")
    void testFormatDateForEmail() {
        // Arrange
        LocalDateTime dateTime = LocalDateTime.of(2026, 6, 20, 14, 30, 0);

        // Act
        String formatted = calendarService.formatDateForEmail(dateTime);

        // Assert
        assertTrue(formatted.contains("June"));
        assertTrue(formatted.contains("20"));
        assertTrue(formatted.contains("2026"));
        assertTrue(formatted.contains("2:30 PM"));
    }

    @Test
    @DisplayName("Should handle null values gracefully")
    void testHandleNullValues() {
        // Arrange
        CalendarEventData data = CalendarEventData.builder()
                .eventTitle("Test Event")
                .eventDescription(null)
                .startTime(LocalDateTime.of(2026, 6, 20, 10, 0, 0))
                .endTime(LocalDateTime.of(2026, 6, 20, 17, 0, 0))
                .location(null)
                .attendeeEmail("user@example.com")
                .attendeeName(null)
                .bookingId("abc-123")
                .build();

        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> calendarService.generateGoogleCalendarUrl(data));
        assertDoesNotThrow(() -> calendarService.generateICalendarContent(data));
    }

    @Test
    @DisplayName("Should generate unique UID for each booking")
    void testUniqueUIDPerBooking() {
        // Arrange
        CalendarEventData data1 = CalendarEventData.builder()
                .eventTitle("Event 1")
                .eventDescription("Description")
                .startTime(LocalDateTime.of(2026, 6, 20, 10, 0, 0))
                .endTime(LocalDateTime.of(2026, 6, 20, 17, 0, 0))
                .location("Location")
                .attendeeEmail("user@example.com")
                .attendeeName("User")
                .bookingId("booking-1")
                .build();

        CalendarEventData data2 = CalendarEventData.builder()
                .eventTitle("Event 2")
                .eventDescription("Description")
                .startTime(LocalDateTime.of(2026, 6, 21, 10, 0, 0))
                .endTime(LocalDateTime.of(2026, 6, 21, 17, 0, 0))
                .location("Location")
                .attendeeEmail("user@example.com")
                .attendeeName("User")
                .bookingId("booking-2")
                .build();

        // Act
        String ics1 = calendarService.generateICalendarContent(data1);
        String ics2 = calendarService.generateICalendarContent(data2);

        // Assert
        assertTrue(ics1.contains("booking-1@eventsnest.com"));
        assertTrue(ics2.contains("booking-2@eventsnest.com"));
        assertNotEquals(ics1, ics2);
    }
}
