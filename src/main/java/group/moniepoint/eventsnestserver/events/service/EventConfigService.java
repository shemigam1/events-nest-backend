package group.moniepoint.eventsnestserver.events.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.dto.request.UpdateEventConfigRequest;
import group.moniepoint.eventsnestserver.events.dto.response.EventConfigResponse;
import group.moniepoint.eventsnestserver.events.models.EventConfig;
import group.moniepoint.eventsnestserver.events.models.Events;

import java.util.UUID;

public interface EventConfigService {

    /**
     * Create and persist the default config row for a newly created event.
     * Called from {@code EventServiceImpl.createEvent} inside the same
     * transaction so an event always has a config.
     */
    EventConfig createDefaultsFor(Events event);

    EventConfigResponse getByEventId(UUID eventId);

    EventsNestResponse<EventConfigResponse> updateByEventId(UUID eventId,
                                                            UpdateEventConfigRequest request,
                                                            User requestingUser);
}
