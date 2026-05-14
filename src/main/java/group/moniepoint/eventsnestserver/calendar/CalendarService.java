package group.moniepoint.eventsnestserver.calendar;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service to generate calendar event data for Google Calendar and iCalendar (.ics) formats.
 *
 * Provides two integration options:
 * 1. Google Calendar URL — One-click add to Google Calendar
 * 2. iCalendar content — Universal format for any calendar app (Outlook, Apple, etc.)
 */
@Service
@Slf4j
public class CalendarService {

    public static final String EVENTSNEST_TIMEZONE = "Africa/Lagos";
    public static final String ORGANIZER_EMAIL = "noreply@eventsnest.com";

    /**
     * Generate a Google Calendar URL for quick-add.
     * Opens Google Calendar with event details pre-filled.
     *
     * @param data Calendar event data
     * @return Google Calendar URL
     */
    public String generateGoogleCalendarUrl(CalendarEventData data) {
        try {
            // Format dates: YYYYMMDDTHHMMSSZ (RFC5545 format)
            String startDate = formatRFC5545(data.getStartTime());
            String endDate = formatRFC5545(data.getEndTime());

            // Build URL parameters
            String baseUrl = "https://calendar.google.com/calendar/render";
            Map<String, String> params = new LinkedHashMap<>();
            params.put("action", "TEMPLATE");
            params.put("text", data.getEventTitle() != null ? data.getEventTitle() : "");
            params.put("dates", startDate + "/" + endDate);
            params.put("details", data.getEventDescription() != null ? data.getEventDescription() : "");
            params.put("location", data.getLocation() != null ? data.getLocation() : "");
            params.put("ctz", EVENTSNEST_TIMEZONE);

            // Build query string
            String queryString = params.entrySet().stream()
                    .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));

            return baseUrl + "?" + queryString;

        } catch (Exception e) {
            log.error("Failed to generate Google Calendar URL", e);
            throw new RuntimeException("Failed to generate calendar URL", e);
        }
    }

    /**
     * Generate iCalendar (.ics) file content.
     * Can be attached to emails or provided as a download link.
     *
     * @param data Calendar event data
     * @return iCalendar content (VCALENDAR format)
     */
    public String generateICalendarContent(CalendarEventData data) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of(EVENTSNEST_TIMEZONE));
        String timestamp = formatRFC5545(now);
        String startDate = formatRFC5545(data.getStartTime());
        String endDate = formatRFC5545(data.getEndTime());

        // Generate unique UID (prevents duplicates if imported multiple times)
        String uid = String.format("booking-%s@eventsnest.com", data.getBookingId());

        StringBuilder ics = new StringBuilder();
        ics.append("BEGIN:VCALENDAR\r\n");
        ics.append("VERSION:2.0\r\n");
        ics.append("PRODID:-//EventsNest//EventsNest 1.0//EN\r\n");
        ics.append("CALSCALE:GREGORIAN\r\n");
        ics.append("METHOD:PUBLISH\r\n");
        ics.append("X-WR-TIMEZONE:").append(EVENTSNEST_TIMEZONE).append("\r\n");

        ics.append("BEGIN:VEVENT\r\n");
        ics.append("UID:").append(uid).append("\r\n");
        ics.append("DTSTAMP:").append(timestamp).append("\r\n");
        ics.append("DTSTART:").append(startDate).append("\r\n");
        ics.append("DTEND:").append(endDate).append("\r\n");
        ics.append("SUMMARY:").append(escapeICalendarText(data.getEventTitle())).append("\r\n");
        ics.append("DESCRIPTION:").append(escapeICalendarText(data.getEventDescription())).append("\r\n");
        ics.append("LOCATION:").append(escapeICalendarText(data.getLocation())).append("\r\n");
        ics.append("ORGANIZER;CN=EventsNest:mailto:").append(ORGANIZER_EMAIL).append("\r\n");
        ics.append("ATTENDEE;CN=").append(escapeICalendarText(data.getAttendeeName()))
                .append(":mailto:").append(data.getAttendeeEmail()).append("\r\n");
        ics.append("SEQUENCE:0\r\n");
        ics.append("STATUS:CONFIRMED\r\n");

        // Add 24-hour reminder
        ics.append("BEGIN:VALARM\r\n");
        ics.append("TRIGGER:-PT24H\r\n");
        ics.append("ACTION:DISPLAY\r\n");
        ics.append("DESCRIPTION:").append(escapeICalendarText(data.getEventTitle()))
                .append(" starting tomorrow\r\n");
        ics.append("END:VALARM\r\n");

        ics.append("END:VEVENT\r\n");
        ics.append("END:VCALENDAR\r\n");

        return ics.toString();
    }

    /**
     * Format LocalDateTime to RFC5545 format (YYYYMMDDTHHMMSSZ).
     * Converts to UTC for consistency.
     */
    private String formatRFC5545(LocalDateTime dateTime) {
        ZonedDateTime zoned = dateTime.atZone(ZoneId.of(EVENTSNEST_TIMEZONE))
                .withZoneSameInstant(ZoneId.of("UTC"));
        return zoned.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
    }

    /**
     * Escape special characters for iCalendar format.
     * Handles newlines, semicolons, commas, backslashes.
     */
    private String escapeICalendarText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text
                .replace("\\", "\\\\")
                .replace(",", "\\,")
                .replace(";", "\\;")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    /**
     * Format LocalDateTime to human-readable format.
     * Used in email templates.
     */
    public String formatDateForEmail(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.ENGLISH));
    }
}
