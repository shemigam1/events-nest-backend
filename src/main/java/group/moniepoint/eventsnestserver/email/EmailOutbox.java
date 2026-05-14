package group.moniepoint.eventsnestserver.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import group.moniepoint.eventsnestserver.admin.event.EventApprovedEvent;
import group.moniepoint.eventsnestserver.admin.event.EventRejectedEvent;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.bookings.event.BookingConfirmedEvent;
import group.moniepoint.eventsnestserver.calendar.CalendarService;
import group.moniepoint.eventsnestserver.email.model.EmailJob;
import group.moniepoint.eventsnestserver.email.model.EmailJobType;
import group.moniepoint.eventsnestserver.email.payload.BookingConfirmationPayload;
import group.moniepoint.eventsnestserver.email.payload.BookingConfirmationWithCalendarPayload;
import group.moniepoint.eventsnestserver.email.payload.EventApprovedPayload;
import group.moniepoint.eventsnestserver.email.payload.EventRejectedPayload;
import group.moniepoint.eventsnestserver.email.payload.GuestRsvpInvitePayload;
import group.moniepoint.eventsnestserver.email.payload.PasswordResetPayload;
import group.moniepoint.eventsnestserver.email.payload.RatingRequestPayload;
import group.moniepoint.eventsnestserver.email.payload.StaffInvitePayload;
import group.moniepoint.eventsnestserver.email.payload.VendorInquiryEmailPayload;
import group.moniepoint.eventsnestserver.email.payload.VendorContractOfferEmailPayload;
import group.moniepoint.eventsnestserver.vendor.model.VendorInquiry;
import group.moniepoint.eventsnestserver.contracts.model.VendorContract;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.guestlist.model.Guest;
import group.moniepoint.eventsnestserver.ratings.model.RatingForm;
import group.moniepoint.eventsnestserver.email.repository.EmailJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Single seam for enqueuing transactional emails. Callers (currently
 * {@code NotificationKafkaConsumer}) hand in a typed event; this service
 * resolves the recipient, serialises a payload, and saves an
 * {@link EmailJob} row. The {@code EmailJobPoller} drains the queue and
 * actually calls Resend — so a slow/down Resend never blocks the consumer
 * thread.
 *
 * Resilience: every method here is best-effort. Failures (missing user,
 * JSON serialisation issues) are logged and swallowed so a downstream
 * problem here doesn't poison the upstream Kafka consumer offset.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailOutbox {

    private final EmailJobRepository emailJobRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final CalendarService calendarService;

    @Transactional
    public void enqueueBookingConfirmation(BookingConfirmedEvent event) {
        if (isBlank(event.attendeeEmail())) {
            log.warn("Skipping BOOKING_CONFIRMED email — no attendee email on event for booking {}",
                    event.bookingId());
            return;
        }

        String name = lookupName(event.attendeeId());
        BookingConfirmationPayload payload = new BookingConfirmationPayload(
                name,
                event.eventTitle(),
                event.tierName(),
                event.quantity(),
                event.totalAmount(),
                event.paymentReference());

        save(EmailJobType.BOOKING_CONFIRMED, event.attendeeEmail(), payload);
    }

    @Transactional
    public void enqueueBookingConfirmationWithCalendar(
            BookingConfirmedEvent event,
            String googleCalendarUrl,
            String eventDate,
            String eventLocation) {
        if (isBlank(event.attendeeEmail())) {
            log.warn("Skipping BOOKING_CONFIRMED_WITH_CALENDAR email — no attendee email on event for booking {}",
                    event.bookingId());
            return;
        }

        String name = lookupName(event.attendeeId());
        BookingConfirmationWithCalendarPayload payload = new BookingConfirmationWithCalendarPayload(
                name,
                event.eventTitle(),
                event.tierName(),
                event.quantity(),
                event.totalAmount(),
                event.paymentReference(),
                googleCalendarUrl != null ? googleCalendarUrl : "",
                eventDate != null ? eventDate : "",
                eventLocation != null ? eventLocation : "");

        save(EmailJobType.BOOKING_CONFIRMED_WITH_CALENDAR, event.attendeeEmail(), payload);
    }

    @Transactional
    public void enqueueEventApproved(EventApprovedEvent event) {
        Optional<User> organiser = lookup(event.organiserId());
        if (organiser.isEmpty() || isBlank(organiser.get().getEmail())) {
            log.warn("Skipping EVENT_APPROVED email — organiser {} not found or missing email",
                    event.organiserId());
            return;
        }

        EventApprovedPayload payload = new EventApprovedPayload(
                fullName(organiser.get()),
                event.eventTitle());

        save(EmailJobType.EVENT_APPROVED, organiser.get().getEmail(), payload);
    }

    /**
     * Called by {@code CheckInInviteServiceImpl} after a staff invite is created.
     * Doesn't go through Kafka — this is a request-time enqueue, not driven by
     * a domain event. The poller picks it up the same way as the others.
     */
    @Transactional
    public void enqueueStaffInvite(String toEmail,
                                   String staffName,
                                   String rawToken,
                                   Events event) {
        if (isBlank(toEmail)) {
            log.warn("Skipping STAFF_INVITE email — no recipient on invite");
            return;
        }

        StaffInvitePayload payload = new StaffInvitePayload(
                staffName,
                rawToken,
                event.getId(),
                event.getCode(),
                event.getTitle());

        save(EmailJobType.STAFF_INVITE, toEmail, payload);
    }

    @Transactional
    public void enqueueEventRejected(EventRejectedEvent event) {
        Optional<User> organiser = lookup(event.organiserId());
        if (organiser.isEmpty() || isBlank(organiser.get().getEmail())) {
            log.warn("Skipping EVENT_REJECTED email — organiser {} not found or missing email",
                    event.organiserId());
            return;
        }

        EventRejectedPayload payload = new EventRejectedPayload(
                fullName(organiser.get()),
                event.eventTitle(),
                event.reason());

        save(EmailJobType.EVENT_REJECTED, organiser.get().getEmail(), payload);
    }

    @Transactional
    public void enqueueGuestRsvpInvite(Guest guest, String rawToken, Events event) {
        if (isBlank(guest.getEmail())) {
            log.warn("Skipping GUEST_RSVP_INVITE — no email for guest {}", guest.getId());
            return;
        }
        GuestRsvpInvitePayload payload = new GuestRsvpInvitePayload(
                guest.getName(), event.getTitle(), event.getId(), rawToken);
        save(EmailJobType.GUEST_RSVP_INVITE, guest.getEmail(), payload);
    }

    @Transactional
    public void enqueueRatingRequest(User attendee, RatingForm form) {
        if (isBlank(attendee.getEmail())) {
            log.warn("Skipping RATING_REQUEST — no email for attendee {}", attendee.getId());
            return;
        }
        RatingRequestPayload payload = new RatingRequestPayload(
                fullName(attendee), form.getEvent().getTitle(), form.getId());
        save(EmailJobType.RATING_REQUEST, attendee.getEmail(), payload);
    }

    @Transactional
    public void enqueuePasswordReset(String toEmail, String name, String rawToken) {
        if (isBlank(toEmail)) {
            log.warn("Skipping PASSWORD_RESET email — no recipient email");
            return;
        }
        PasswordResetPayload payload = new PasswordResetPayload(name, rawToken);
        save(EmailJobType.PASSWORD_RESET, toEmail, payload);
    }

    @Transactional
    public void enqueueVendorInquiry(VendorInquiry inquiry, String chatUrl) {
        String toEmail = inquiry.getVendor().getEmail();
        if (isBlank(toEmail)) {
            log.warn("Skipping VENDOR_INQUIRY email — vendor {} has no email", inquiry.getVendor().getId());
            return;
        }
        VendorInquiryEmailPayload payload = new VendorInquiryEmailPayload(
                fullName(inquiry.getVendor()),
                fullName(inquiry.getOrganizer()),
                inquiry.getEvent().getTitle(),
                inquiry.getEvent().getId(),
                inquiry.getMessage(),
                inquiry.getServiceType(),
                inquiry.getConversation() != null ? inquiry.getConversation().getId() : null);
        save(EmailJobType.VENDOR_INQUIRY, toEmail, payload);
    }

    @Transactional
    public void enqueueVendorContractOffer(VendorContract contract, String chatUrl) {
        String toEmail = contract.getVendor().getEmail();
        if (isBlank(toEmail)) {
            log.warn("Skipping VENDOR_CONTRACT_OFFER email — vendor {} has no email", contract.getVendor().getId());
            return;
        }
        VendorContractOfferEmailPayload payload = new VendorContractOfferEmailPayload(
                fullName(contract.getVendor()),
                fullName(contract.getOrganizer()),
                contract.getEvent().getTitle(),
                contract.getEvent().getId(),
                contract.getTitle(),
                contract.getAmount(),
                contract.getConversation() != null ? contract.getConversation().getId() : null,
                contract.getId());
        save(EmailJobType.VENDOR_CONTRACT_OFFER, toEmail, payload);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private void save(EmailJobType type, String toEmail, Object payload) {
        try {
            emailJobRepository.save(EmailJob.builder()
                    .type(type)
                    .toEmail(toEmail)
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .build());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialise {} payload for {}: {}", type, toEmail, e.getMessage());
        }
    }

    private Optional<User> lookup(String userId) {
        if (isBlank(userId)) return Optional.empty();
        return userRepository.findById(userId);
    }

    private String lookupName(String userId) {
        return lookup(userId).map(EmailOutbox::fullName).orElse(null);
    }

    private static String fullName(User user) {
        StringBuilder sb = new StringBuilder();
        if (user.getFirstName() != null && !user.getFirstName().isBlank()) {
            sb.append(user.getFirstName().trim());
        }
        if (user.getLastName() != null && !user.getLastName().isBlank()) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(user.getLastName().trim());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
