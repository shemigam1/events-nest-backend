package group.moniepoint.eventsnestserver.admin.service;

import group.moniepoint.eventsnestserver.admin.dto.request.RejectEventRequest;
import group.moniepoint.eventsnestserver.admin.dto.response.PageResponse;
import group.moniepoint.eventsnestserver.admin.dto.response.PlatformAnalyticsResponse;
import group.moniepoint.eventsnestserver.admin.dto.response.UserSummaryResponse;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.bookings.repository.BookingRepository;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.dto.response.EventResponse;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.tickets.models.TicketStatus;
import group.moniepoint.eventsnestserver.tickets.repository.TicketRepository;
import lombok.AllArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
            throw new InvalidEventStateException(
                    "only PENDING_APPROVAL events can be approved");
        }

        event.setStatus(EventStatus.PUBLISHED);
        event.setRejectionReason(null);
        Events saved = eventRepository.save(event);

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
            throw new InvalidEventStateException(
                    "only PENDING_APPROVAL events can be rejected");
        }

        event.setStatus(EventStatus.DRAFT);
        event.setRejectionReason(request.getReason());
        Events saved = eventRepository.save(event);

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

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private Events findEventOrThrow(UUID id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("event not found"));
    }

    private EventResponse toEventResponse(Events event) {
        EventResponse response = modelMapper.map(event, EventResponse.class);
        if (event.getCreatedBy() != null) {
            response.setCreatedBy(event.getCreatedBy().getId());
        }
        return response;
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
