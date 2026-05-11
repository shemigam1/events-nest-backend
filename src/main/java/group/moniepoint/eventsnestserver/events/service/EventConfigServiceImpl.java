package group.moniepoint.eventsnestserver.events.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.dto.request.UpdateEventConfigRequest;
import group.moniepoint.eventsnestserver.events.dto.response.EventConfigResponse;
import group.moniepoint.eventsnestserver.events.models.EventConfig;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventConfigRepository;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.exception.auth.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.event.EventConfigNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@AllArgsConstructor
public class EventConfigServiceImpl implements EventConfigService {

    private final EventConfigRepository configRepository;
    private final EventMembershipRepository membershipRepository;

    @Override
    @Transactional
    public EventConfig createDefaultsFor(Events event) {
        EventConfig config = EventConfig.builder()
                .event(event)
                .ticketingEnabled(true)
                .guestListEnabled(false)
                .programmeEnabled(false)
                .ratingsEnabled(false)
                .build();
        return configRepository.saveAndFlush(config);
    }

    @Override
    @Transactional(readOnly = true)
    public EventConfigResponse getByEventId(UUID eventId) {
        EventConfig config = configRepository.findByEventId(eventId)
                .orElseThrow(EventConfigNotFoundException::new);
        return toResponse(config);
    }

    @Override
    @Transactional
    public EventsNestResponse<EventConfigResponse> updateByEventId(UUID eventId,
                                                                   UpdateEventConfigRequest request,
                                                                   User requestingUser) {
        EventConfig config = configRepository.findByEventId(eventId)
                .orElseThrow(EventConfigNotFoundException::new);

        assertIsOrganizer(eventId, requestingUser);

        if (request.getTicketingEnabled() != null) config.setTicketingEnabled(request.getTicketingEnabled());
        if (request.getGuestListEnabled() != null) config.setGuestListEnabled(request.getGuestListEnabled());
        if (request.getProgrammeEnabled() != null) config.setProgrammeEnabled(request.getProgrammeEnabled());
        if (request.getRatingsEnabled() != null)   config.setRatingsEnabled(request.getRatingsEnabled());

        EventConfig saved = configRepository.saveAndFlush(config);

        EventsNestResponse<EventConfigResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Event modules updated");
        response.setData(toResponse(saved));
        return response;
    }

    private void assertIsOrganizer(UUID eventId, User user) {
        boolean isOrganizer = membershipRepository
                .existsByEventsIdAndUserIdAndRole(eventId, user.getId(), EventRole.ORGANIZER);
        if (!isOrganizer) {
            throw new NotEventOrganizerException();
        }
    }

    static EventConfigResponse toResponse(EventConfig config) {
        return EventConfigResponse.builder()
                .eventId(config.getEvent().getId())
                .ticketingEnabled(config.isTicketingEnabled())
                .guestListEnabled(config.isGuestListEnabled())
                .programmeEnabled(config.isProgrammeEnabled())
                .ratingsEnabled(config.isRatingsEnabled())
                .build();
    }
}
