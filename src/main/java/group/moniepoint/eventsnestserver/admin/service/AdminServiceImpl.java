package group.moniepoint.eventsnestserver.admin.service;

import group.moniepoint.eventsnestserver.admin.dto.request.CompleteAdminInvitationRequest;
import group.moniepoint.eventsnestserver.admin.dto.response.EventEditRequestResponse;
import group.moniepoint.eventsnestserver.admin.dto.request.InviteAdminRequest;
import group.moniepoint.eventsnestserver.admin.dto.request.RejectEventRequest;
import group.moniepoint.eventsnestserver.admin.dto.request.UpdateUserStatusRequest;
import group.moniepoint.eventsnestserver.admin.event.EventApprovedEvent;
import group.moniepoint.eventsnestserver.admin.event.EventRejectedEvent;
import group.moniepoint.eventsnestserver.admin.kafka.AdminEventPublisher;
import group.moniepoint.eventsnestserver.admin.dto.response.PageResponse;
import group.moniepoint.eventsnestserver.admin.dto.response.PlatformAnalyticsResponse;
import group.moniepoint.eventsnestserver.admin.dto.response.UserSummaryResponse;
import group.moniepoint.eventsnestserver.admin.model.AdminInvitation;
import group.moniepoint.eventsnestserver.admin.repository.AdminInvitationRepository;
import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.bookings.dto.response.BookingResponse;
import group.moniepoint.eventsnestserver.bookings.models.Booking;
import group.moniepoint.eventsnestserver.bookings.repository.BookingRepository;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.email.EmailService;
import group.moniepoint.eventsnestserver.events.dto.response.EventResponse;
import group.moniepoint.eventsnestserver.events.dto.response.OrganizerResponse;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.events.models.EventEditRequest;
import group.moniepoint.eventsnestserver.events.models.EventEditStatus;
import group.moniepoint.eventsnestserver.events.repository.EventEditRequestRepository;
import group.moniepoint.eventsnestserver.exception.EmailAlreadyInUseException;
import group.moniepoint.eventsnestserver.exception.EventAlreadyCancelledException;
import group.moniepoint.eventsnestserver.exception.EventNotFoundException;
import group.moniepoint.eventsnestserver.exception.EventNotPendingApprovalException;
import group.moniepoint.eventsnestserver.exception.InvitationTokenInvalidException;
import group.moniepoint.eventsnestserver.exception.NoPendingEventUpdateException;
import group.moniepoint.eventsnestserver.exception.UserNotFoundException;
import group.moniepoint.eventsnestserver.tiers.dto.response.TicketTierResponse;
import group.moniepoint.eventsnestserver.tiers.models.TicketTier;
import group.moniepoint.eventsnestserver.tiers.repository.TicketTierRepository;
import group.moniepoint.eventsnestserver.tickets.models.TicketStatus;
import group.moniepoint.eventsnestserver.tickets.repository.TicketRepository;
import lombok.AllArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@AllArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final ModelMapper modelMapper;
    private final EventRespository eventRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final TicketRepository ticketRepository;
    private final TicketTierRepository tierRepository;
    private final EventEditRequestRepository editRequestRepository;
    private final AdminInvitationRepository invitationRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AdminEventPublisher adminEventPublisher;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> getEventsByStatus(EventStatus status, Pageable pageable) {
        return PageResponse.from(
                eventRepository.findAllByStatus(status, pageable),
                this::toEventResponse);
    }

    @Override
    @Transactional
    public EventsNestResponse<EventResponse> approveEvent(UUID eventId) {
        Events event = findEventOrThrow(eventId);
        if (event.getStatus() != EventStatus.PENDING_APPROVAL) {
            throw new EventNotPendingApprovalException();
        }

        event.setStatus(EventStatus.PUBLISHED);
        event.setRejectionReason(null);
        Events saved = eventRepository.save(event);

        adminEventPublisher.publishApproved(new EventApprovedEvent(
                saved.getId(),
                saved.getTitle(),
                saved.getCreatedBy() != null ? saved.getCreatedBy().getId() : null,
                LocalDateTime.now()));

        // Phase B telemetry — admin approval is a major lifecycle moment;
        // count it for the dashboard regardless of whether anyone is subscribed
        // to the Kafka event downstream.
        meterRegistry.counter("eventsnest.events.published").increment();

        EventsNestResponse<EventResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Event approved and published");
        response.setData(toEventResponse(saved));
        return response;
    }

    @Override
    @Transactional
    public EventsNestResponse<EventResponse> rejectEvent(UUID eventId, RejectEventRequest request) {
        Events event = findEventOrThrow(eventId);
        if (event.getStatus() != EventStatus.PENDING_APPROVAL) {
            throw new EventNotPendingApprovalException();
        }

        event.setStatus(EventStatus.DRAFT);
        event.setRejectionReason(request.getReason());
        Events saved = eventRepository.save(event);

        adminEventPublisher.publishRejected(new EventRejectedEvent(
                saved.getId(),
                saved.getTitle(),
                saved.getCreatedBy() != null ? saved.getCreatedBy().getId() : null,
                request.getReason(),
                LocalDateTime.now()));

        EventsNestResponse<EventResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Event rejected");
        response.setData(toEventResponse(saved));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> getUsers(Pageable pageable) {
        return PageResponse.from(
                userRepository.findAll(pageable),
                this::toUserSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public PlatformAnalyticsResponse getAnalytics() {
        Map<String, Long> eventsByStatus = new LinkedHashMap<>();
        for (EventStatus status : EventStatus.values()) {
            eventsByStatus.put(status.name(), 0L);
        }
        for (Object[] row : eventRepository.countGroupedByStatus()) {
            EventStatus status = (EventStatus) row[0];
            Long count = (Long) row[1];
            eventsByStatus.put(status.name(), count);
        }

        long usedTickets = ticketRepository.countByStatus(TicketStatus.USED);
        long issuedTickets = ticketRepository.countByStatusIn(
                List.of(TicketStatus.VALID, TicketStatus.USED));
        double checkInRate = issuedTickets == 0
                ? 0.0
                : (double) usedTickets / issuedTickets;

        BigDecimal revenue = bookingRepository.sumConfirmedRevenue();

        return PlatformAnalyticsResponse.builder()
                .eventsByStatus(eventsByStatus)
                .totalBookings(bookingRepository.count())
                .totalRevenue(revenue == null ? BigDecimal.ZERO : revenue)
                .checkInRate(checkInRate)
                .build();
    }

    @Override
    @Transactional
    public EventsNestResponse<Void> inviteAdmin(InviteAdminRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyInUseException();
        }

        // Replace any existing pending invitation for this email (re-invite flow)
        invitationRepository.findByEmail(request.getEmail())
                .ifPresent(invitationRepository::delete);

        String token = UUID.randomUUID().toString();
        AdminInvitation invitation = AdminInvitation.builder()
                .email(request.getEmail())
                .token(token)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        invitationRepository.save(invitation);

        emailService.sendAdminInvitation(request.getEmail(), token);

        EventsNestResponse<Void> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Invitation sent to " + request.getEmail());
        return response;
    }

    @Override
    @Transactional
    public EventsNestResponse<UserSummaryResponse> completeAdminInvitation(CompleteAdminInvitationRequest request) {
        AdminInvitation invitation = invitationRepository.findByToken(request.getToken())
                .orElseThrow(InvitationTokenInvalidException::new);

        if (invitation.isUsed() || invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvitationTokenInvalidException();
        }

        // Guard against the edge case where the email was registered after the invite was sent
        if (userRepository.existsByEmail(invitation.getEmail())) {
            throw new EmailAlreadyInUseException();
        }

        User admin = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(invitation.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.ADMIN)
                .build();
        User saved = userRepository.save(admin);

        invitation.setUsed(true);
        invitationRepository.save(invitation);

        EventsNestResponse<UserSummaryResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Admin account created successfully");
        response.setData(toUserSummary(saved));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public EventsNestResponse<EventResponse> getEventById(UUID eventId) {
        Events event = findEventOrThrow(eventId);
        EventsNestResponse<EventResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setData(toEventResponse(event));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public EventsNestResponse<UserSummaryResponse> getUserById(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        EventsNestResponse<UserSummaryResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setData(toUserSummary(user));
        return response;
    }

    @Override
    @Transactional
    public EventsNestResponse<UserSummaryResponse> updateUserStatus(String userId, UpdateUserStatusRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        user.setEnabled(request.getEnabled());
        User saved = userRepository.save(user);
        EventsNestResponse<UserSummaryResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage(request.getEnabled() ? "User account enabled" : "User account disabled");
        response.setData(toUserSummary(saved));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getEventBookings(UUID eventId) {
        findEventOrThrow(eventId);
        return bookingRepository.findAllByEventIdOrderByCreatedAtDesc(eventId)
                .stream()
                .map(this::toBookingResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EventEditRequestResponse> getEventEditRequests(EventEditStatus status, Pageable pageable) {
        return PageResponse.from(
                editRequestRepository.findAllByStatusOrderByCreatedAtAsc(status, pageable),
                this::toEventEditRequestResponse);
    }

    @Override
    @Transactional
    public EventsNestResponse<EventResponse> approveEventUpdate(UUID editRequestId) {
        EventEditRequest editRequest = editRequestRepository.findById(editRequestId)
                .orElseThrow(NoPendingEventUpdateException::new);

        if (editRequest.getStatus() != EventEditStatus.PENDING) {
            throw new NoPendingEventUpdateException();
        }

        Events event = editRequest.getEvent();
        if (editRequest.getProposedChanges().description() != null) {
            event.setDescription(editRequest.getProposedChanges().description());
        }
        if (editRequest.getProposedChanges().venue() != null) {
            event.setVenue(editRequest.getProposedChanges().venue());
        }
        if (editRequest.getProposedChanges().startTime() != null) {
            event.setStartTime(editRequest.getProposedChanges().startTime());
        }
        if (editRequest.getProposedChanges().endTime() != null) {
            event.setEndTime(editRequest.getProposedChanges().endTime());
        }
        if (editRequest.getProposedChanges().checkInStartTime() != null) {
            event.setCheckInStartTime(editRequest.getProposedChanges().checkInStartTime());
        }
        Events saved = eventRepository.save(event);

        editRequest.setStatus(EventEditStatus.APPROVED);
        editRequest.setReviewedAt(LocalDateTime.now());
        editRequestRepository.save(editRequest);

        EventsNestResponse<EventResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Event update approved and applied");
        response.setData(toEventResponse(saved));
        return response;
    }

    @Override
    @Transactional
    public EventsNestResponse<EventResponse> rejectEventUpdate(UUID editRequestId, RejectEventRequest request) {
        EventEditRequest editRequest = editRequestRepository.findById(editRequestId)
                .orElseThrow(NoPendingEventUpdateException::new);

        if (editRequest.getStatus() != EventEditStatus.PENDING) {
            throw new NoPendingEventUpdateException();
        }

        editRequest.setStatus(EventEditStatus.REJECTED);
        editRequest.setRejectionReason(request.getReason());
        editRequest.setReviewedAt(LocalDateTime.now());
        editRequestRepository.save(editRequest);

        EventsNestResponse<EventResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Event update rejected");
        response.setData(toEventResponse(editRequest.getEvent()));
        return response;
    }

    @Override
    @Transactional
    public EventsNestResponse<EventResponse> cancelEvent(UUID eventId) {
        Events event = findEventOrThrow(eventId);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new EventAlreadyCancelledException();
        }
        event.setStatus(EventStatus.CANCELLED);
        Events saved = eventRepository.save(event);
        EventsNestResponse<EventResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Event cancelled");
        response.setData(toEventResponse(saved));
        return response;
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private Events findEventOrThrow(UUID id) {
        return eventRepository.findById(id)
                .orElseThrow(EventNotFoundException::new);
    }

    private EventResponse toEventResponse(Events event) {
        EventResponse response = modelMapper.map(event, EventResponse.class);
        if (event.getCreatedBy() != null) {
            response.setCreatedBy(event.getCreatedBy().getId());
            response.setOrganizer(OrganizerResponse.builder()
                    .id(event.getCreatedBy().getId())
                    .firstName(event.getCreatedBy().getFirstName())
                    .lastName(event.getCreatedBy().getLastName())
                    .build());
        }
        response.setTiers(tierRepository.findAllByEventId(event.getId())
                .stream().map(this::toTierResponse).toList());
        return response;
    }

    private TicketTierResponse toTierResponse(TicketTier tier) {
        return TicketTierResponse.builder()
                .id(tier.getId())
                .eventId(tier.getEvent() != null ? tier.getEvent().getId() : null)
                .name(tier.getName())
                .price(tier.getPrice())
                .rowPrefix(tier.getRowPrefix())
                .rowCount(tier.getRowCount())
                .seatsPerRow(tier.getSeatsPerRow())
                .totalCapacity(tier.getTotalCapacity())
                .availableCapacity(tier.getAvailableCapacity())
                .createdAt(tier.getCreatedAt())
                .build();
    }

    private EventEditRequestResponse toEventEditRequestResponse(EventEditRequest er) {
        return EventEditRequestResponse.builder()
                .id(er.getId())
                .eventId(er.getEvent() != null ? er.getEvent().getId() : null)
                .eventTitle(er.getEvent() != null ? er.getEvent().getTitle() : null)
                .proposedChanges(er.getProposedChanges())
                .status(er.getStatus())
                .rejectionReason(er.getRejectionReason())
                .submittedById(er.getSubmittedBy() != null ? er.getSubmittedBy().getId() : null)
                .submittedByName(er.getSubmittedBy() != null
                        ? er.getSubmittedBy().getFirstName() + " " + er.getSubmittedBy().getLastName() : null)
                .submittedByEmail(er.getSubmittedBy() != null ? er.getSubmittedBy().getEmail() : null)
                .submittedAt(er.getCreatedAt())
                .reviewedAt(er.getReviewedAt())
                .build();
    }

    private BookingResponse toBookingResponse(Booking booking) {
        return BookingResponse.builder()
                .id(booking.getId())
                .eventId(booking.getEvent() != null ? booking.getEvent().getId() : null)
                .eventTitle(booking.getEvent() != null ? booking.getEvent().getTitle() : null)
                .tierId(booking.getTier() != null ? booking.getTier().getId() : null)
                .tierName(booking.getTier() != null ? booking.getTier().getName() : null)
                .quantity(booking.getQuantity())
                .totalAmount(booking.getTotalAmount())
                .status(booking.getStatus())
                .paymentStatus(booking.getPaymentStatus())
                .paymentReference(booking.getPaymentReference())
                .createdAt(booking.getCreatedAt())
                .attendeeId(booking.getAttendee() != null ? booking.getAttendee().getId() : null)
                .attendeeFirstName(booking.getAttendee() != null ? booking.getAttendee().getFirstName() : null)
                .attendeeLastName(booking.getAttendee() != null ? booking.getAttendee().getLastName() : null)
                .attendeeName(booking.getAttendee() != null
                        ? booking.getAttendee().getFirstName() + " " + booking.getAttendee().getLastName() : null)
                .attendeeEmail(booking.getAttendee() != null ? booking.getAttendee().getEmail() : null)
                .build();
    }

    private UserSummaryResponse toUserSummary(User user) {
        return UserSummaryResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
