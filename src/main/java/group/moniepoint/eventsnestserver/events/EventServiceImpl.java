package group.moniepoint.eventsnestserver.events;

import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.dto.request.CreateEventRequest;
import group.moniepoint.eventsnestserver.events.dto.response.EventResponse;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import lombok.AllArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class EventServiceImpl implements EventService {

    private final ModelMapper modelMapper;
    private final EventRespository eventRepository;

    @Override
    public EventsNestResponse<EventResponse> createEvent(CreateEventRequest request) {
        Events event = modelMapper.map(request, Events.class);
        event.setStatus(EventStatus.DRAFT);

        Events saved = eventRepository.save(event);

        EventsNestResponse<EventResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Event created successfully");
        response.setData(modelMapper.map(saved, EventResponse.class));
        return response;
    }
}
