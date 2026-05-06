package group.moniepoint.eventsnestserver.events;

import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.dto.request.CreateEventRequest;
import group.moniepoint.eventsnestserver.events.dto.response.EventResponse;

public interface EventService {
    EventsNestResponse<EventResponse> createEvent(CreateEventRequest request);
}
