package group.moniepoint.eventsnestserver.events.service;

import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.dto.request.CreateEventRequest;
import group.moniepoint.eventsnestserver.events.dto.request.UpdateEventRequest;
import group.moniepoint.eventsnestserver.events.dto.response.EventResponse;
import group.moniepoint.eventsnestserver.events.dto.response.EventSummaryResponse;
import group.moniepoint.eventsnestserver.events.dto.response.OrganizerStatsResponse;
import group.moniepoint.eventsnestserver.auth.model.User;

import java.util.List;
import java.util.UUID;

public interface EventService {
    EventsNestResponse<EventResponse> createEvent(CreateEventRequest request, User creator);

    List<EventSummaryResponse> getPublishedEvents();

    /**
     * Public detail endpoint. Accepts an optional caller for PRIVATE-event
     * gating (organizer or accepted-RSVP guest can see; everyone else 404).
     * Pass {@code null} for unauthenticated callers.
     */
    EventResponse getEventById(UUID id, User caller);

    EventsNestResponse<EventResponse> updateEvent(UUID id, UpdateEventRequest request, User requestingUser);

    EventsNestResponse<EventResponse> submitForApproval(UUID id, User requestingUser);

    void deleteEvent(UUID id, User requestingUser);

    List<EventResponse> getMyEvents(User organizer);

    EventResponse getMyEventById(UUID id, User organizer);

    OrganizerStatsResponse getMyStats(User organizer);

    EventResponse getEventByCode(String code);
}
