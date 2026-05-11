package group.moniepoint.eventsnestserver.events.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.dto.request.CreateEventDayRequest;
import group.moniepoint.eventsnestserver.events.dto.request.UpdateEventDayRequest;
import group.moniepoint.eventsnestserver.events.dto.response.EventDayResponse;
import group.moniepoint.eventsnestserver.events.models.EventDay;
import group.moniepoint.eventsnestserver.events.models.Events;

import java.util.List;
import java.util.UUID;

public interface EventDayService {

    /**
     * Auto-create the initial day for a newly created event. Called from
     * {@code EventServiceImpl.createEvent} inside the same transaction.
     * Mirrors the event's start/end/check-in window — single-day events
     * never see this concept in the UI.
     */
    EventDay createDefaultDayFor(Events event);

    List<EventDayResponse> listByEventId(UUID eventId);

    EventsNestResponse<EventDayResponse> addDay(UUID eventId,
                                                CreateEventDayRequest request,
                                                User requestingUser);

    EventsNestResponse<EventDayResponse> updateDay(UUID eventId,
                                                  UUID dayId,
                                                  UpdateEventDayRequest request,
                                                  User requestingUser);

    void deleteDay(UUID eventId, UUID dayId, User requestingUser);
}
