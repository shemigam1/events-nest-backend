package group.moniepoint.eventsnestserver.events.service;

import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.dto.request.CreateEventRequest;
import group.moniepoint.eventsnestserver.events.dto.request.UpdateEventRequest;
import group.moniepoint.eventsnestserver.events.dto.response.EventResponse;
import group.moniepoint.eventsnestserver.events.dto.response.EventSummaryResponse;
import group.moniepoint.eventsnestserver.events.models.EventMembership;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.models.MembershipStatus;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.InvalidEventStateException;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import group.moniepoint.eventsnestserver.auth.model.User;
import lombok.AllArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class EventServiceImpl implements EventService {

    private final ModelMapper modelMapper;
    private final EventRespository eventRepository;
    private final EventMembershipRepository membershipRepository;

    @Override
    @Transactional
    public EventsNestResponse<EventResponse> createEvent(CreateEventRequest createEventRequest, User creator) {
        Events event = modelMapper.map(createEventRequest, Events.class);
        event.setStatus(EventStatus.DRAFT);
        event.setCreatedBy(creator);
        Events saved = eventRepository.save(event);

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
    public List<EventSummaryResponse> getPublishedEvents() {
        return eventRepository.findAllByStatus(EventStatus.PUBLISHED)
                .stream()
                .map(e -> modelMapper.map(e, EventSummaryResponse.class))
                .toList();
    }

    @Override
    public EventResponse getEventById(UUID id) {
        Events event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("event not found"));
        return toEventResponse(event);
    }

    @Override
    @Transactional
    public EventsNestResponse<EventResponse> updateEvent(UUID id, UpdateEventRequest request, User requestingUser) {
        Events event = findEventOrThrow(id);
        assertIsOrganizer(event, requestingUser);

        if (event.getStatus() == EventStatus.PUBLISHED) {
            throw new InvalidEventStateException("cannot update a published event");
        }

        if (request.getTitle() != null) event.setTitle(request.getTitle());
        if (request.getDescription() != null) event.setDescription(request.getDescription());
        if (request.getVenue() != null) event.setVenue(request.getVenue());
        if (request.getStartTime() != null) event.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) event.setEndTime(request.getEndTime());

        Events saved = eventRepository.save(event);

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
            throw new InvalidEventStateException("only DRAFT events can be submitted for approval");
        }

        event.setStatus(EventStatus.PENDING_APPROVAL);
        Events saved = eventRepository.save(event);

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
            throw new InvalidEventStateException("only DRAFT or CANCELLED events can be deleted");
        }

        eventRepository.delete(event);
    }

    private EventResponse toEventResponse(Events event) {
        EventResponse response = modelMapper.map(event, EventResponse.class);
        if (event.getCreatedBy() != null) {
            response.setCreatedBy(event.getCreatedBy().getId());
        }
        return response;
    }

    private Events findEventOrThrow(UUID id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("event not found"));
    }

    private void assertIsOrganizer(Events event, User user) {
        boolean isOrganizer = membershipRepository
                .existsByEventsIdAndUserIdAndRole(event.getId(), user.getId(), EventRole.ORGANIZER);
        if (!isOrganizer) {
            throw new UnauthorizedException("only the event organizer can perform this action");
        }
    }
}
