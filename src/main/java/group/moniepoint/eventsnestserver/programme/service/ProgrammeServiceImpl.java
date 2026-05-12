package group.moniepoint.eventsnestserver.programme.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.models.EventConfig;
import group.moniepoint.eventsnestserver.events.models.EventDay;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventConfigRepository;
import group.moniepoint.eventsnestserver.events.repository.EventDayRepository;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.auth.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.event.EventConfigNotFoundException;
import group.moniepoint.eventsnestserver.exception.event.EventDayNotFoundException;
import group.moniepoint.eventsnestserver.exception.event.EventNotFoundException;
import group.moniepoint.eventsnestserver.exception.programme.ProgrammeItemNotFoundException;
import group.moniepoint.eventsnestserver.exception.programme.ProgrammeNotEnabledException;
import group.moniepoint.eventsnestserver.programme.dto.request.CreateProgrammeItemRequest;
import group.moniepoint.eventsnestserver.programme.dto.request.UpdateProgrammeItemRequest;
import group.moniepoint.eventsnestserver.programme.dto.response.ProgrammeItemResponse;
import group.moniepoint.eventsnestserver.programme.model.ProgrammeItem;
import group.moniepoint.eventsnestserver.programme.repository.ProgrammeItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProgrammeServiceImpl implements ProgrammeService {

    private final ProgrammeItemRepository itemRepository;
    private final EventRespository eventRepository;
    private final EventConfigRepository configRepository;
    private final EventMembershipRepository membershipRepository;
    private final EventDayRepository dayRepository;

    @Override
    @Transactional
    public EventsNestResponse<ProgrammeItemResponse> addItem(UUID eventId,
                                                             CreateProgrammeItemRequest request,
                                                             User organizer) {
        Events event = findEventOrThrow(eventId);
        assertOrganizer(eventId, organizer);
        assertProgrammeEnabled(eventId);

        EventDay day = null;
        if (request.getEventDayId() != null) {
            day = dayRepository.findByIdAndEventId(request.getEventDayId(), eventId)
                    .orElseThrow(EventDayNotFoundException::new);
        }

        ProgrammeItem item = ProgrammeItem.builder()
                .event(event)
                .eventDay(day)
                .title(request.getTitle())
                .description(request.getDescription())
                .speakerName(request.getSpeakerName())
                .speakerBio(request.getSpeakerBio())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .displayOrder(request.getDisplayOrder())
                .build();

        ProgrammeItem saved = itemRepository.save(item);

        EventsNestResponse<ProgrammeItemResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Programme item added");
        response.setData(toResponse(saved));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProgrammeItemResponse> getItems(UUID eventId) {
        assertProgrammeEnabled(eventId);
        return itemRepository.findAllByEventIdOrderByDisplayOrderAsc(eventId)
                .stream().map(ProgrammeServiceImpl::toResponse).toList();
    }

    @Override
    @Transactional
    public EventsNestResponse<ProgrammeItemResponse> updateItem(UUID eventId, UUID itemId,
                                                                UpdateProgrammeItemRequest request,
                                                                User organizer) {
        assertOrganizer(eventId, organizer);
        assertProgrammeEnabled(eventId);

        ProgrammeItem item = itemRepository.findById(itemId)
                .filter(i -> i.getEvent().getId().equals(eventId))
                .orElseThrow(ProgrammeItemNotFoundException::new);

        if (request.getTitle() != null)       item.setTitle(request.getTitle());
        if (request.getDescription() != null) item.setDescription(request.getDescription());
        if (request.getSpeakerName() != null) item.setSpeakerName(request.getSpeakerName());
        if (request.getSpeakerBio() != null)  item.setSpeakerBio(request.getSpeakerBio());
        if (request.getStartTime() != null)   item.setStartTime(request.getStartTime());
        if (request.getEndTime() != null)     item.setEndTime(request.getEndTime());
        if (request.getDisplayOrder() != null) item.setDisplayOrder(request.getDisplayOrder());

        if (request.getEventDayId() != null) {
            EventDay day = dayRepository.findByIdAndEventId(request.getEventDayId(), eventId)
                    .orElseThrow(EventDayNotFoundException::new);
            item.setEventDay(day);
        }

        ProgrammeItem saved = itemRepository.save(item);

        EventsNestResponse<ProgrammeItemResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Programme item updated");
        response.setData(toResponse(saved));
        return response;
    }

    @Override
    @Transactional
    public EventsNestResponse<Void> deleteItem(UUID eventId, UUID itemId, User organizer) {
        assertOrganizer(eventId, organizer);
        assertProgrammeEnabled(eventId);

        ProgrammeItem item = itemRepository.findById(itemId)
                .filter(i -> i.getEvent().getId().equals(eventId))
                .orElseThrow(ProgrammeItemNotFoundException::new);

        itemRepository.delete(item);

        EventsNestResponse<Void> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Programme item deleted");
        return response;
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private void assertOrganizer(UUID eventId, User user) {
        boolean isOrganizer = membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, user.getId(), EventRole.ORGANIZER);
        boolean isManager   = membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, user.getId(), EventRole.MANAGER);
        if (!isOrganizer && !isManager) {
            throw new NotEventOrganizerException();
        }
    }

    private void assertProgrammeEnabled(UUID eventId) {
        EventConfig config = configRepository.findByEventId(eventId)
                .orElseThrow(EventConfigNotFoundException::new);
        if (!config.isProgrammeEnabled()) {
            throw new ProgrammeNotEnabledException();
        }
    }

    private Events findEventOrThrow(UUID eventId) {
        return eventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);
    }

    static ProgrammeItemResponse toResponse(ProgrammeItem item) {
        return ProgrammeItemResponse.builder()
                .id(item.getId())
                .eventId(item.getEvent().getId())
                .eventDayId(item.getEventDay() != null ? item.getEventDay().getId() : null)
                .title(item.getTitle())
                .description(item.getDescription())
                .speakerName(item.getSpeakerName())
                .speakerBio(item.getSpeakerBio())
                .startTime(item.getStartTime())
                .endTime(item.getEndTime())
                .displayOrder(item.getDisplayOrder())
                .build();
    }
}
