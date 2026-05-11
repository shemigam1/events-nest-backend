package group.moniepoint.eventsnestserver.events.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.dto.request.CreateEventDayRequest;
import group.moniepoint.eventsnestserver.events.dto.request.UpdateEventDayRequest;
import group.moniepoint.eventsnestserver.events.dto.response.EventDayResponse;
import group.moniepoint.eventsnestserver.events.models.EventDay;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventDayRepository;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.EventsNestException;
import group.moniepoint.eventsnestserver.exception.auth.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.checkin.InvalidCheckInStartTimeException;
import group.moniepoint.eventsnestserver.exception.event.CannotDeleteLastEventDayException;
import group.moniepoint.eventsnestserver.exception.event.EventDayNotFoundException;
import group.moniepoint.eventsnestserver.exception.event.EventNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class EventDayServiceImpl implements EventDayService {

    private final EventDayRepository dayRepository;
    private final EventMembershipRepository membershipRepository;
    private final EventRespository eventRepository;

    @Override
    @Transactional
    public EventDay createDefaultDayFor(Events event) {
        EventDay day = EventDay.builder()
                .event(event)
                .dayNumber(1)
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .checkInStartTime(event.getCheckInStartTime())
                .venue(null) // null means "use the event's default venue"
                .build();
        return dayRepository.saveAndFlush(day);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventDayResponse> listByEventId(UUID eventId) {
        return dayRepository.findAllByEventIdOrderByDayNumberAsc(eventId)
                .stream()
                .map(EventDayServiceImpl::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public EventsNestResponse<EventDayResponse> addDay(UUID eventId,
                                                       CreateEventDayRequest request,
                                                       User requestingUser) {
        assertIsOrganizer(eventId, requestingUser);

        if (request.getCheckInStartTime() != null
                && !request.getCheckInStartTime().isBefore(request.getStartTime())) {
            throw new InvalidCheckInStartTimeException();
        }

        Events event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);

        // Resolve next day_number for this event.
        int nextNumber = dayRepository.findFirstByEventIdOrderByDayNumberDesc(eventId)
                .map(d -> d.getDayNumber() + 1)
                .orElse(1);

        EventDay day = EventDay.builder()
                .event(event)
                .dayNumber(nextNumber)
                .title(request.getTitle())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .checkInStartTime(request.getCheckInStartTime())
                .venue(request.getVenue())
                .build();
        EventDay saved = dayRepository.saveAndFlush(day);

        EventsNestResponse<EventDayResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Day added");
        response.setData(toResponse(saved));
        return response;
    }

    @Override
    @Transactional
    public EventsNestResponse<EventDayResponse> updateDay(UUID eventId,
                                                          UUID dayId,
                                                          UpdateEventDayRequest request,
                                                          User requestingUser) {
        assertIsOrganizer(eventId, requestingUser);

        EventDay day = dayRepository.findByIdAndEventId(dayId, eventId)
                .orElseThrow(EventDayNotFoundException::new);

        if (request.getTitle() != null)     day.setTitle(request.getTitle());
        if (request.getStartTime() != null) day.setStartTime(request.getStartTime());
        if (request.getEndTime() != null)   day.setEndTime(request.getEndTime());
        if (request.getVenue() != null)     day.setVenue(request.getVenue());

        // end-after-start across the merged state.
        if (!day.getEndTime().isAfter(day.getStartTime())) {
            throw new EventsNestException("endTime must be after startTime");
        }

        if (request.getCheckInStartTime() != null) {
            if (!request.getCheckInStartTime().isBefore(day.getStartTime())) {
                throw new InvalidCheckInStartTimeException();
            }
            day.setCheckInStartTime(request.getCheckInStartTime());
        }

        EventDay saved = dayRepository.saveAndFlush(day);

        EventsNestResponse<EventDayResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Day updated");
        response.setData(toResponse(saved));
        return response;
    }

    @Override
    @Transactional
    public void deleteDay(UUID eventId, UUID dayId, User requestingUser) {
        assertIsOrganizer(eventId, requestingUser);

        EventDay day = dayRepository.findByIdAndEventId(dayId, eventId)
                .orElseThrow(EventDayNotFoundException::new);

        if (dayRepository.countByEventId(eventId) <= 1) {
            throw new CannotDeleteLastEventDayException();
        }

        dayRepository.delete(day);
    }

    private void assertIsOrganizer(UUID eventId, User user) {
        boolean isOrganizer = membershipRepository
                .existsByEventsIdAndUserIdAndRole(eventId, user.getId(), EventRole.ORGANIZER);
        if (!isOrganizer) {
            throw new NotEventOrganizerException();
        }
    }

    static EventDayResponse toResponse(EventDay day) {
        return EventDayResponse.builder()
                .id(day.getId())
                .eventId(day.getEvent() != null ? day.getEvent().getId() : null)
                .dayNumber(day.getDayNumber())
                .title(day.getTitle())
                .startTime(day.getStartTime())
                .endTime(day.getEndTime())
                .checkInStartTime(day.getCheckInStartTime())
                .venue(day.getVenue())
                .build();
    }
}
