package group.moniepoint.eventsnestserver.guestlist;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.email.EmailOutbox;
import group.moniepoint.eventsnestserver.events.models.EventConfig;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventConfigRepository;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.guestlist.DuplicateGuestEmailException;
import group.moniepoint.eventsnestserver.exception.guestlist.GuestListNotEnabledException;
import group.moniepoint.eventsnestserver.exception.guestlist.GuestNotFoundException;
import group.moniepoint.eventsnestserver.exception.guestlist.InvalidRsvpTokenException;
import group.moniepoint.eventsnestserver.guestlist.dto.request.CreateGuestRequest;
import group.moniepoint.eventsnestserver.guestlist.dto.request.RsvpRequest;
import group.moniepoint.eventsnestserver.guestlist.model.Guest;
import group.moniepoint.eventsnestserver.guestlist.model.RsvpStatus;
import group.moniepoint.eventsnestserver.guestlist.repository.GuestRepository;
import group.moniepoint.eventsnestserver.guestlist.service.GuestServiceImpl;
import group.moniepoint.eventsnestserver.util.TokenHashUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestServiceTest {

    @Mock private GuestRepository guestRepository;
    @Mock private EventRespository eventRepository;
    @Mock private EventConfigRepository configRepository;
    @Mock private EventMembershipRepository membershipRepository;
    @Mock private EmailOutbox emailOutbox;

    private GuestServiceImpl guestService;

    @BeforeEach
    void setUp() {
        guestService = new GuestServiceImpl(
                guestRepository, eventRepository, configRepository, membershipRepository, emailOutbox);
    }

    private User organizer(UUID eventId) {
        User user = User.builder().id("org-1").email("org@test.com").build();
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, "org-1", EventRole.ORGANIZER))
                .thenReturn(true);
        return user;
    }

    private void enableGuestList(UUID eventId, Events event) {
        event.setStatus(EventStatus.PUBLISHED);
        EventConfig cfg = EventConfig.builder().guestListEnabled(true).build();
        when(configRepository.findByEventId(eventId)).thenReturn(Optional.of(cfg));
    }

    @Test
    void addGuest_savesGuestAndQueuesEmail() {
        UUID eventId = UUID.randomUUID();
        Events event = Events.builder().id(eventId).title("Conf").build();
        User org = organizer(eventId);
        enableGuestList(eventId, event);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(guestRepository.existsByEventIdAndEmail(eventId, "ada@x.com")).thenReturn(false);
        when(guestRepository.save(any())).thenAnswer(inv -> {
            Guest g = inv.getArgument(0);
            g = Guest.builder()
                    .id(UUID.randomUUID()).event(event).name(g.getName()).email(g.getEmail())
                    .rsvpStatus(RsvpStatus.PENDING).invitedAt(LocalDateTime.now()).build();
            return g;
        });

        CreateGuestRequest req = new CreateGuestRequest();
        req.setName("Ada Lovelace");
        req.setEmail("ada@x.com");
        req.setSendInvite(true);

        var result = guestService.addGuest(eventId, req, org);

        assertThat(result.isSuccess()).isTrue();
        verify(emailOutbox).enqueueGuestRsvpInvite(any(), any(), any());
    }

    @Test
    void addGuest_throwsWhenGuestListDisabled() {
        UUID eventId = UUID.randomUUID();
        Events event = Events.builder().id(eventId).title("Conf").status(EventStatus.PUBLISHED).build();
        User org = organizer(eventId);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(configRepository.findByEventId(eventId))
                .thenReturn(Optional.of(EventConfig.builder().guestListEnabled(false).build()));

        CreateGuestRequest req = new CreateGuestRequest();
        req.setName("Ada"); req.setEmail("ada@x.com");

        assertThatThrownBy(() -> guestService.addGuest(eventId, req, org))
                .isInstanceOf(GuestListNotEnabledException.class);

        verify(guestRepository, never()).save(any());
    }

    @Test
    void addGuest_throwsOnDuplicateEmail() {
        UUID eventId = UUID.randomUUID();
        Events event = Events.builder().id(eventId).title("Conf").build();
        User org = organizer(eventId);
        enableGuestList(eventId, event);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(guestRepository.existsByEventIdAndEmail(eventId, "ada@x.com")).thenReturn(true);

        CreateGuestRequest req = new CreateGuestRequest();
        req.setName("Ada"); req.setEmail("ada@x.com");

        assertThatThrownBy(() -> guestService.addGuest(eventId, req, org))
                .isInstanceOf(DuplicateGuestEmailException.class);
    }

    @Test
    void rsvp_acceptsValidToken() {
        UUID guestId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Events event = Events.builder().id(eventId).build();

        String rawToken = UUID.randomUUID().toString();
        String hash = TokenHashUtils.hash(rawToken);

        Guest guest = Guest.builder()
                .id(guestId).event(event).name("Ada").email("ada@x.com")
                .rsvpStatus(RsvpStatus.PENDING).invitedAt(LocalDateTime.now())
                .rsvpTokenHash(hash).build();

        when(guestRepository.findByRsvpTokenHash(hash)).thenReturn(Optional.of(guest));
        when(guestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RsvpRequest req = new RsvpRequest();
        req.setToken(rawToken);
        req.setResponse("ACCEPTED");

        var result = guestService.rsvp(req);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getRsvpStatus()).isEqualTo(RsvpStatus.ACCEPTED);
    }

    @Test
    void rsvp_throwsOnInvalidToken() {
        when(guestRepository.findByRsvpTokenHash(any())).thenReturn(Optional.empty());

        RsvpRequest req = new RsvpRequest();
        req.setToken("bad-token");
        req.setResponse("ACCEPTED");

        assertThatThrownBy(() -> guestService.rsvp(req))
                .isInstanceOf(InvalidRsvpTokenException.class);
    }

    @Test
    void removeGuest_throwsWhenGuestBelongsToDifferentEvent() {
        UUID eventId = UUID.randomUUID();
        UUID guestId = UUID.randomUUID();
        UUID otherEventId = UUID.randomUUID();
        User org = organizer(eventId);
        Events event = Events.builder().id(eventId).build();
        enableGuestList(eventId, event);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        Events otherEvent = Events.builder().id(otherEventId).build();
        Guest guest = Guest.builder().id(guestId).event(otherEvent).build();
        when(guestRepository.findById(guestId)).thenReturn(Optional.of(guest));

        assertThatThrownBy(() -> guestService.removeGuest(eventId, guestId, org))
                .isInstanceOf(GuestNotFoundException.class);
    }
}
