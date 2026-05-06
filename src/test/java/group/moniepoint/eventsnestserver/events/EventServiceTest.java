package group.moniepoint.eventsnestserver.events;

import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.dto.request.CreateEventRequest;
import group.moniepoint.eventsnestserver.events.dto.response.EventResponse;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRespository eventRepository;

    private EventServiceImpl eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventServiceImpl(new ModelMapper(), eventRepository);
    }

    @Test
    void createEventPersistsMappedEntityWithDraftStatusAndReturnsResponse() {
        CreateEventRequest request = new CreateEventRequest();
        request.setTitle("Annual Hackathon");
        request.setVenue("Lagos Tech Hub");
        request.setStartTime(LocalDateTime.of(2026, 8, 1, 9, 0));
        request.setEndTime(LocalDateTime.of(2026, 8, 1, 18, 0));

        UUID generatedId = UUID.randomUUID();
        when(eventRepository.save(any(Events.class))).thenAnswer(inv -> {
            Events e = inv.getArgument(0);
            e.setId(generatedId);
            return e;
        });

        EventsNestResponse<EventResponse> response = eventService.createEvent(request);

        ArgumentCaptor<Events> captor = ArgumentCaptor.forClass(Events.class);
        verify(eventRepository).save(captor.capture());
        Events saved = captor.getValue();

        assertThat(saved.getTitle()).isEqualTo("Annual Hackathon");
        assertThat(saved.getVenue()).isEqualTo("Lagos Tech Hub");
        assertThat(saved.getStartTime()).isEqualTo(request.getStartTime());
        assertThat(saved.getEndTime()).isEqualTo(request.getEndTime());
        assertThat(saved.getStatus()).isEqualTo(EventStatus.DRAFT);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Event created successfully");
        assertThat(response.getData().getId()).isEqualTo(generatedId);
        assertThat(response.getData().getTitle()).isEqualTo("Annual Hackathon");
        assertThat(response.getData().getVenue()).isEqualTo("Lagos Tech Hub");
        assertThat(response.getData().getStatus()).isEqualTo(EventStatus.DRAFT);
    }
}
