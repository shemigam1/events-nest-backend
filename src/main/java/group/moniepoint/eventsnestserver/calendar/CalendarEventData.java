package group.moniepoint.eventsnestserver.calendar;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Calendar event data built from booking + event entities.
 * Used to generate Google Calendar URLs and iCalendar (.ics) files.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarEventData {

    private String eventTitle;              // "TechConf 2026 - VIP Ticket"
    private String eventDescription;        // Booking + ticket details
    private LocalDateTime startTime;        // Event start (local timezone)
    private LocalDateTime endTime;          // Event end (local timezone)
    private String location;                // Venue address
    private String organizerEmail;          // organizer@eventsnest.com
    private String attendeeEmail;           // User's email
    private String attendeeName;            // User's first + last name
    private String bookingId;               // For UID uniqueness
    private List<String> ticketCodes;       // Short ticket codes (EV-ABC123, etc.)
    private String eventId;                 // Event UUID for links
}
