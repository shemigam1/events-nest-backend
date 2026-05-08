package group.moniepoint.eventsnestserver.events.service;

import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.dto.request.CreateEventRequest;
import group.moniepoint.eventsnestserver.events.dto.request.UpdateEventRequest;
import group.moniepoint.eventsnestserver.events.dto.response.EventResponse;
import group.moniepoint.eventsnestserver.events.dto.response.EventSummaryResponse;
import group.moniepoint.eventsnestserver.events.dto.response.OrganizerResponse;
import group.moniepoint.eventsnestserver.events.models.EventMembership;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.models.MembershipStatus;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.tiers.dto.response.TicketTierResponse;
import group.moniepoint.eventsnestserver.tiers.models.TicketTier;
import group.moniepoint.eventsnestserver.tiers.repository.TicketTierRepository;
import group.moniepoint.eventsnestserver.exception.EventNotFoundException;
import group.moniepoint.eventsnestserver.exception.EventNotDeletableException;
import group.moniepoint.eventsnestserver.exception.EventNotPublishedException;
import group.moniepoint.eventsnestserver.exception.EventNotSubmittableException;
import group.moniepoint.eventsnestserver.exception.InvalidCheckInStartTimeException;
import group.moniepoint.eventsnestserver.exception.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.PublishedEventNotEditableException;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import group.moniepoint.eventsnestserver.auth.model.User;
import lombok.AllArgsConstructor;
import org.modelmapper.ModelMapper;
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

    @Override
    @Transactional
    public EventsNestResponse<EventResponse> createEvent(CreateEventRequest createEventRequest, User creator) {
        if (createEventRequest.getCheckInStartTime() != null
                && !createEventRequest.getCheckInStartTime().isBefore(createEventRequest.getStartTime())) {
            throw new InvalidCheckInStartTimeException();
        }

        Events event = modelMapper.map(createEventRequest, Events.class);
        event.setStatus(EventStatus.DRAFT);
        event.setCreatedBy(creator);
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

        EventMembership membership = EventMembership.builder()
                .user(creator)
                .events(saved)
                .role(EventRole.ORGANIZER)
                .status(MembershipStatus.ACTIVE)
                .build();
        membershipRepository.save(membership);

        EventsNestResponse<EventResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Event created successfully");
        response.setData(toEventResponse(saved));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventSummaryResponse> getPublishedEvents() {
        return eventRepository.findAllByStatus(EventStatus.PUBLISHED)
                .stream()
                .map(this::toEventSummaryResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse getEventById(UUID id) {
        Events event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("event not found"));
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new EventNotPublishedException();
        }
        return toEventResponse(event);
    }

    @Override
    @Transactional
    public EventsNestResponse<EventResponse> updateEvent(UUID id, UpdateEventRequest request, User requestingUser) {
        Events event = findEventOrThrow(id);
        assertIsOrganizer(event, requestingUser);

        if (event.getStatus() == EventStatus.PUBLISHED) {
            throw new PublishedEventNotEditableException();
        }

        if (request.getTitle() != null) event.setTitle(request.getTitle());
        if (request.getDescription() != null) event.setDescription(request.getDescription());
        if (request.getVenue() != null) event.setVenue(request.getVenue());
        if (request.getStartTime() != null) event.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) event.setEndTime(request.getEndTime());

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
    public void deleteEvent(UUID id, User requestingUser) {
        Events event = findEventOrThrow(id);
        assertIsOrganizer(event, requestingUser);

        if (event.getStatus() != EventStatus.DRAFT && event.getStatus() != EventStatus.CANCELLED) {
            throw new EventNotDeletableException();
        }

        eventRepository.delete(event);
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

    private EventSummaryResponse toEventSummaryResponse(Events event) {
        return EventSummaryResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .venue(event.getVenue())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .status(event.getStatus())
                .tiers(tierRepository.findAllByEventId(event.getId())
                        .stream().map(this::toTierResponse).toList())
                .build();
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
