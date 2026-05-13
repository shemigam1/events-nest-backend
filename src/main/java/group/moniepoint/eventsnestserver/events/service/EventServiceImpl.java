package group.moniepoint.eventsnestserver.events.service;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.dto.EventEditProposedChanges;
import group.moniepoint.eventsnestserver.events.dto.request.CreateEventRequest;
import group.moniepoint.eventsnestserver.events.dto.request.UpdateEventRequest;
import group.moniepoint.eventsnestserver.bookings.repository.BookingRepository;
import group.moniepoint.eventsnestserver.events.dto.response.EventResponse;
import group.moniepoint.eventsnestserver.events.dto.response.EventSummaryResponse;
import group.moniepoint.eventsnestserver.events.dto.response.OrganizerResponse;
import group.moniepoint.eventsnestserver.events.dto.response.OrganizerStatsResponse;
import group.moniepoint.eventsnestserver.events.dto.response.PendingUpdateResponse;
import group.moniepoint.eventsnestserver.events.models.EventEditRequest;
import group.moniepoint.eventsnestserver.events.models.EventEditStatus;
import group.moniepoint.eventsnestserver.events.models.EventMembership;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.EventVisibility;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.models.MembershipStatus;
import group.moniepoint.eventsnestserver.events.repository.EventConfigRepository;
import group.moniepoint.eventsnestserver.events.repository.EventDayRepository;
import group.moniepoint.eventsnestserver.events.repository.EventEditRequestRepository;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.guestlist.model.RsvpStatus;
import group.moniepoint.eventsnestserver.guestlist.repository.GuestRepository;
import group.moniepoint.eventsnestserver.exception.auth.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.checkin.InvalidCheckInStartTimeException;
import group.moniepoint.eventsnestserver.exception.event.EventFieldLockedException;
import group.moniepoint.eventsnestserver.exception.event.EventNotDeletableException;
import group.moniepoint.eventsnestserver.exception.event.EventNotFoundException;
import group.moniepoint.eventsnestserver.exception.event.EventNotPublishedException;
import group.moniepoint.eventsnestserver.exception.event.EventNotSubmittableException;
import group.moniepoint.eventsnestserver.exception.event.EventNotWithdrawableException;
import group.moniepoint.eventsnestserver.exception.EventsNestException;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.tiers.dto.response.TicketTierResponse;
import group.moniepoint.eventsnestserver.tiers.models.TicketTier;
import group.moniepoint.eventsnestserver.tiers.repository.TicketTierRepository;
import lombok.AllArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class EventServiceImpl implements EventService {

    private final ModelMapper modelMapper;
    private final EventRespository eventRepository;
    private final EventMembershipRepository membershipRepository;
    private final TicketTierRepository tierRepository;
    private final BookingRepository bookingRepository;
    private final EventEditRequestRepository editRequestRepository;
    private final EventConfigRepository configRepository;
    private final EventConfigService configService;
    private final EventDayRepository dayRepository;
    private final EventDayService dayService;
    private final GuestRepository guestRepository;

    @Override
    @Transactional
    public EventsNestResponse<EventResponse> createEvent(CreateEventRequest createEventRequest, User creator) {
        if (createEventRequest.getCheckInStartTime() != null
                && !createEventRequest.getCheckInStartTime().isBefore(createEventRequest.getStartTime())) {
            throw new InvalidCheckInStartTimeException();
        }

        Events event = modelMapper.map(createEventRequest, Events.class);
        event.setStatus(EventStatus.DRAFT);
        event.setVisibility(createEventRequest.getVisibility() != null
                ? createEventRequest.getVisibility()
                : EventVisibility.PUBLIC);
        event.setCreatedBy(creator);
        event.setCode(NanoIdUtils.randomNanoId(
                NanoIdUtils.DEFAULT_NUMBER_GENERATOR,
                NanoIdUtils.DEFAULT_ALPHABET, 8));
        event.setCheckInStartTime(
                createEventRequest.getCheckInStartTime() != null
                        ? createEventRequest.getCheckInStartTime()
                        : createEventRequest.getStartTime().minusHours(2));
        Events saved = eventRepository.saveAndFlush(event);

        createEventRequest.getTiers().forEach(tierRequest -> {
            int totalCapacity = tierRequest.getRowCount() * tierRequest.getSeatsPerRow();
            tierRepository.save(TicketTier.builder()
                    .event(saved)
                    .name(tierRequest.getName())
                    .price(tierRequest.getPrice())
                    .rowPrefix(tierRequest.getRowPrefix())
                    .rowCount(tierRequest.getRowCount())
                    .seatsPerRow(tierRequest.getSeatsPerRow())
                    .totalCapacity(totalCapacity)
                    .availableCapacity(totalCapacity)
                    .build());
        });

        membershipRepository.save(EventMembership.builder()
                .user(creator)
                .events(saved)
                .role(EventRole.ORGANIZER)
                .status(MembershipStatus.ACTIVE)
                .build());

        configService.createDefaultsFor(saved);
        dayService.createDefaultDayFor(saved);

        EventsNestResponse<EventResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Event created successfully");
        response.setData(toEventResponse(saved));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable("public-events")
    public List<EventSummaryResponse> getPublishedEvents() {
        // PRIVATE events are intentionally hidden from the browse list.
        // They remain reachable via /events/code/{code} for invited guests.
        return eventRepository.findAllByStatusAndVisibility(EventStatus.PUBLISHED, EventVisibility.PUBLIC)
                .stream()
                .map(this::toEventSummaryResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "event-detail", key = "#id",
               unless = "#result.visibility.name() == 'PRIVATE'")
    public EventResponse getEventById(UUID id, User caller) {
        Events event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("event not found"));
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new EventNotPublishedException();
        }
        if (event.getVisibility() == EventVisibility.PRIVATE && !callerMayViewPrivate(event, caller)) {
            // 404 not 403 — don't confirm existence to outsiders.
            throw new ResourceNotFoundException("event not found");
        }
        return toEventResponse(event);
    }

    private boolean callerMayViewPrivate(Events event, User caller) {
        if (caller == null) return false;
        // Organizer always allowed.
        boolean isOrganizer = membershipRepository
                .existsByEventsIdAndUserIdAndRole(event.getId(), caller.getId(), EventRole.ORGANIZER);
        if (isOrganizer) return true;
        // Accepted-RSVP guest allowed.
        return guestRepository.existsByEventIdAndEmailAndRsvpStatus(
                event.getId(), caller.getEmail(), RsvpStatus.ACCEPTED);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "event-detail",  key = "#id"),
            @CacheEvict(value = "public-events", allEntries = true)
    })
    public EventsNestResponse<EventResponse> updateEvent(UUID id, UpdateEventRequest request, User requestingUser) {
        Events event = findEventOrThrow(id);
        assertIsOrganizer(event, requestingUser);

        if (event.getStatus() == EventStatus.PUBLISHED) {
            return handlePublishedEventUpdate(event, request, requestingUser);
        }

        // DRAFT / PENDING_APPROVAL — all fields freely editable
        if (request.getTitle() != null) event.setTitle(request.getTitle());
        if (request.getDescription() != null) event.setDescription(request.getDescription());
        if (request.getVenue() != null) event.setVenue(request.getVenue());
        if (request.getStartTime() != null) event.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) event.setEndTime(request.getEndTime());
        if (request.getVisibility() != null) event.setVisibility(request.getVisibility());

        if (request.getCheckInStartTime() != null) {
            LocalDateTime effectiveStart = request.getStartTime() != null
                    ? request.getStartTime() : event.getStartTime();
            if (!request.getCheckInStartTime().isBefore(effectiveStart)) {
                throw new InvalidCheckInStartTimeException();
            }
            event.setCheckInStartTime(request.getCheckInStartTime());
        }

        Events saved = eventRepository.saveAndFlush(event);

        EventsNestResponse<EventResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Event updated successfully");
        response.setData(toEventResponse(saved));
        return response;
    }

    @Override
    @Transactional
    public EventsNestResponse<EventResponse> submitForApproval(UUID id, User requestingUser) {
        Events event = findEventOrThrow(id);
        assertIsOrganizer(event, requestingUser);

        if (event.getStatus() != EventStatus.DRAFT) {
            throw new EventNotSubmittableException();
        }

        // Cover image is encouraged but not required. Admins can still
        // reject events that need one — keeping it optional makes the
        // form less prescriptive while preserving the upload endpoint
        // for organisers who do add a cover.

        event.setStatus(EventStatus.PENDING_APPROVAL);
        Events saved = eventRepository.saveAndFlush(event);

        EventsNestResponse<EventResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Event submitted for approval");
        response.setData(toEventResponse(saved));
        return response;
    }

    @Override
    @Transactional
    public EventsNestResponse<EventResponse> withdrawSubmission(UUID id, User requestingUser) {
        Events event = findEventOrThrow(id);
        assertIsOrganizer(event, requestingUser);

        if (event.getStatus() != EventStatus.PENDING_APPROVAL) {
            throw new EventNotWithdrawableException();
        }

        event.setStatus(EventStatus.DRAFT);
        // Clear any prior rejection reason — withdrawing puts the event
        // back in a clean DRAFT state, not a "rejected" one.
        event.setRejectionReason(null);
        Events saved = eventRepository.saveAndFlush(event);

        EventsNestResponse<EventResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Submission withdrawn — event is back in draft");
        response.setData(toEventResponse(saved));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventResponse> getMyEvents(User organizer) {
        return eventRepository.findAllByOrganizerId(organizer.getId())
                .stream()
                .map(this::toEventResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse getEventByCode(String code) {
        Events event = eventRepository.findByCode(code)
                .orElseThrow(EventNotFoundException::new);
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new EventNotPublishedException();
        }
        return toEventResponse(event);
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizerStatsResponse getMyStats(User organizer) {
        List<Events> events = eventRepository.findAllByOrganizerId(organizer.getId());
        long total = events.size();
        long published = events.stream().filter(e -> e.getStatus() == EventStatus.PUBLISHED).count();
        long ticketsSold = bookingRepository.sumConfirmedTicketsByOrganizer(organizer.getId());
        java.math.BigDecimal revenue = bookingRepository.sumConfirmedRevenueByOrganizer(organizer.getId());
        return OrganizerStatsResponse.builder()
                .totalEvents(total)
                .publishedEvents(published)
                .ticketsSold(ticketsSold)
                .totalRevenue(revenue != null ? revenue : java.math.BigDecimal.ZERO)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse getMyEventById(UUID id, User organizer) {
        Events event = findEventOrThrow(id);
        assertIsOrganizer(event, organizer);
        return toEventResponse(event);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "event-detail",    key = "#id"),
            @CacheEvict(value = "event-programme", key = "#id"),
            @CacheEvict(value = "event-config",    key = "#id"),
            @CacheEvict(value = "public-events",   allEntries = true)
    })
    public void deleteEvent(UUID id, User requestingUser) {
        Events event = findEventOrThrow(id);
        assertIsOrganizer(event, requestingUser);

        if (event.getStatus() != EventStatus.DRAFT && event.getStatus() != EventStatus.CANCELLED) {
            throw new EventNotDeletableException();
        }

        eventRepository.delete(event);
    }

    // ─── published event update ───────────────────────────────────────────────────

    private EventsNestResponse<EventResponse> handlePublishedEventUpdate(Events event,
                                                                          UpdateEventRequest request,
                                                                          User requestingUser) {
        if (request.getTitle() != null)           throw new EventFieldLockedException("title");
        if (request.getVenue() != null)           throw new EventFieldLockedException("venue");
        if (request.getStartTime() != null)       throw new EventFieldLockedException("startTime");
        if (request.getEndTime() != null)         throw new EventFieldLockedException("endTime");
        if (request.getCheckInStartTime() != null) throw new EventFieldLockedException("checkInStartTime");

        if (request.getDescription() == null) {
            throw new EventsNestException("no editable fields were provided");
        }

        EventEditProposedChanges proposed = new EventEditProposedChanges(
                request.getDescription(), null, null, null, null);

        // Upsert — replace an existing PENDING request rather than stacking duplicates
        EventEditRequest editRequest = editRequestRepository
                .findByEventIdAndStatus(event.getId(), EventEditStatus.PENDING)
                .orElse(EventEditRequest.builder()
                        .event(event)
                        .submittedBy(requestingUser)
                        .build());

        editRequest.setProposedChanges(proposed);
        editRequest.setStatus(EventEditStatus.PENDING);
        editRequest.setSubmittedBy(requestingUser);
        editRequestRepository.save(editRequest);

        EventsNestResponse<EventResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Update submitted for review. Your live event remains unchanged until approved.");
        response.setData(toEventResponse(event));
        return response;
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

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
        editRequestRepository.findByEventIdAndStatus(event.getId(), EventEditStatus.PENDING)
                .ifPresent(er -> response.setPendingUpdate(new PendingUpdateResponse(
                        er.getId(),
                        er.getProposedChanges(),
                        er.getStatus(),
                        er.getRejectionReason(),
                        er.getCreatedAt())));
        configRepository.findByEventId(event.getId())
                .ifPresent(c -> response.setConfig(EventConfigServiceImpl.toResponse(c)));
        response.setDays(dayRepository.findAllByEventIdOrderByDayNumberAsc(event.getId())
                .stream().map(EventDayServiceImpl::toResponse).toList());
        return response;
    }

    private EventSummaryResponse toEventSummaryResponse(Events event) {
        return EventSummaryResponse.builder()
                .id(event.getId())
                .code(event.getCode())
                .title(event.getTitle())
                .venue(event.getVenue())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .status(event.getStatus())
                .visibility(event.getVisibility())
                .coverImageUrl(event.getCoverImageUrl())
                .tiers(tierRepository.findAllByEventId(event.getId())
                        .stream().map(this::toTierResponse).toList())
                .publicUrl(event.getCode() != null
                        ? "https://eventsnest.app/events/" + event.getCode()
                        : null)
                .build();
    }

    private TicketTierResponse toTierResponse(TicketTier tier) {
        return TicketTierResponse.builder()
                .id(tier.getId())
                .eventId(tier.getEvent() != null ? tier.getEvent().getId() : null)
                .eventDayId(tier.getEventDay() != null ? tier.getEventDay().getId() : null)
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

    private Events findEventOrThrow(UUID id) {
        return eventRepository.findById(id)
                .orElseThrow(EventNotFoundException::new);
    }

    private void assertIsOrganizer(Events event, User user) {
        boolean isOrganizer = membershipRepository
                .existsByEventsIdAndUserIdAndRole(event.getId(), user.getId(), EventRole.ORGANIZER);
        if (!isOrganizer) {
            throw new NotEventOrganizerException();
        }
    }
}
