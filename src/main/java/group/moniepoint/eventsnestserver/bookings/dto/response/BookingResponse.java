package group.moniepoint.eventsnestserver.bookings.dto.response;

import group.moniepoint.eventsnestserver.bookings.models.BookingStatus;
import group.moniepoint.eventsnestserver.bookings.models.PaymentStatus;
import group.moniepoint.eventsnestserver.tickets.dto.response.TicketResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private UUID id;
    private UUID eventId;
    private String eventTitle;
    private UUID tierId;
    private String tierName;
    private Integer quantity;
    private BigDecimal totalAmount;
    private BookingStatus status;
    private PaymentStatus paymentStatus;
    private String paymentReference;
    private LocalDateTime createdAt;
    private List<TicketResponse> tickets;

    // Attendee identifying fields. Populated for both /me/bookings (attendee
    // sees their own info) and /organizer/events/{id}/bookings (organiser sees
    // every attendee on their event).
    private String attendeeId;
    private String attendeeFirstName;
    private String attendeeLastName;
    private String attendeeName;
    private String attendeeEmail;
}
