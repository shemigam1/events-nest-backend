package group.moniepoint.eventsnestserver.manager.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.dto.response.EventResponse;
import group.moniepoint.eventsnestserver.manager.dto.request.AssignManagerRequest;
import group.moniepoint.eventsnestserver.manager.dto.response.ManagerResponse;

import java.util.List;
import java.util.UUID;

public interface ManagerService {

    /** Organiser assigns another user as a manager for their event. */
    EventsNestResponse<ManagerResponse> assignManager(UUID eventId, AssignManagerRequest request, User organizer);

    /** Organiser removes a manager from their event. */
    EventsNestResponse<Void> removeManager(UUID eventId, String managerId, User organizer);

    /** Organiser lists all managers assigned to their event. */
    List<ManagerResponse> getManagersForEvent(UUID eventId, User organizer);

    /** Manager views all events they are managing. */
    List<EventResponse> getManagedEvents(User manager);

    /** Manager views a single managed event. */
    EventResponse getManagedEventById(UUID eventId, User manager);
}
