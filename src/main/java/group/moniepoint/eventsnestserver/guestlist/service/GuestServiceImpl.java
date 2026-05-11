package group.moniepoint.eventsnestserver.guestlist.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.email.EmailOutbox;
import group.moniepoint.eventsnestserver.events.models.EventConfig;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventConfigRepository;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.auth.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.event.EventConfigNotFoundException;
import group.moniepoint.eventsnestserver.exception.event.EventNotFoundException;
import group.moniepoint.eventsnestserver.exception.guestlist.DuplicateGuestEmailException;
import group.moniepoint.eventsnestserver.exception.guestlist.GuestListNotEnabledException;
import group.moniepoint.eventsnestserver.exception.guestlist.GuestNotFoundException;
import group.moniepoint.eventsnestserver.exception.guestlist.InvalidRsvpTokenException;
import group.moniepoint.eventsnestserver.guestlist.dto.request.CreateGuestRequest;
import group.moniepoint.eventsnestserver.guestlist.dto.request.RsvpRequest;
import group.moniepoint.eventsnestserver.guestlist.dto.request.UpdateGuestStatusRequest;
import group.moniepoint.eventsnestserver.guestlist.dto.response.GuestResponse;
import group.moniepoint.eventsnestserver.guestlist.model.Guest;
import group.moniepoint.eventsnestserver.guestlist.model.RsvpStatus;
import group.moniepoint.eventsnestserver.guestlist.repository.GuestRepository;
import group.moniepoint.eventsnestserver.util.TokenHashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GuestServiceImpl implements GuestService {

    private final GuestRepository guestRepository;
    private final EventRespository eventRepository;
    private final EventConfigRepository configRepository;
    private final EventMembershipRepository membershipRepository;
    private final EmailOutbox emailOutbox;

    @Override
    @Transactional
    public EventsNestResponse<GuestResponse> addGuest(UUID eventId, CreateGuestRequest request, User organizer) {
        Events event = findEventOrThrow(eventId);
        assertOrganizer(eventId, organizer);
        assertGuestListEnabled(eventId);

        if (guestRepository.existsByEventIdAndEmail(eventId, request.getEmail())) {
            throw new DuplicateGuestEmailException();
        }

        String rawToken = UUID.randomUUID().toString();
        String tokenHash = TokenHashUtils.hash(rawToken);

        Guest guest = Guest.builder()
                .event(event)
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .note(request.getNote())
                .rsvpStatus(RsvpStatus.PENDING)
                .rsvpTokenHash(tokenHash)
                .invitedAt(LocalDateTime.now())
                .build();

        Guest saved = guestRepository.save(guest);

        if (request.isSendInvite()) {
            emailOutbox.enqueueGuestRsvpInvite(saved, rawToken, event);
        }

        EventsNestResponse<GuestResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Guest added successfully");
        response.setData(toResponse(saved));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<GuestResponse> getGuests(UUID eventId, User organizer) {
        assertOrganizer(eventId, organizer);
        assertGuestListEnabled(eventId);
        return guestRepository.findAllByEventIdOrderByInvitedAtDesc(eventId)
                .stream().map(GuestServiceImpl::toResponse).toList();
    }

    @Override
    @Transactional
    public EventsNestResponse<GuestResponse> updateGuestStatus(UUID eventId, UUID guestId,
                                                               UpdateGuestStatusRequest request,
                                                               User organizer) {
        assertOrganizer(eventId, organizer);
        assertGuestListEnabled(eventId);

        Guest guest = guestRepository.findById(guestId)
                .filter(g -> g.getEvent().getId().equals(eventId))
                .orElseThrow(GuestNotFoundException::new);

        guest.setRsvpStatus(request.getRsvpStatus());
        guest.setRespondedAt(LocalDateTime.now());
        Guest saved = guestRepository.save(guest);

        EventsNestResponse<GuestResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Guest status updated");
        response.setData(toResponse(saved));
        return response;
    }

    @Override
    @Transactional
    public EventsNestResponse<Void> removeGuest(UUID eventId, UUID guestId, User organizer) {
        assertOrganizer(eventId, organizer);
        assertGuestListEnabled(eventId);

        Guest guest = guestRepository.findById(guestId)
                .filter(g -> g.getEvent().getId().equals(eventId))
                .orElseThrow(GuestNotFoundException::new);

        guestRepository.delete(guest);

        EventsNestResponse<Void> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Guest removed");
        return response;
    }

    @Override
    @Transactional
    public EventsNestResponse<GuestResponse> rsvp(RsvpRequest request) {
        String tokenHash = TokenHashUtils.hash(request.getToken());
        Guest guest = guestRepository.findByRsvpTokenHash(tokenHash)
                .orElseThrow(InvalidRsvpTokenException::new);

        RsvpStatus status;
        try {
            status = RsvpStatus.valueOf(request.getResponse().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRsvpTokenException();
        }

        if (status != RsvpStatus.ACCEPTED && status != RsvpStatus.DECLINED) {
            throw new InvalidRsvpTokenException();
        }

        guest.setRsvpStatus(status);
        guest.setRespondedAt(LocalDateTime.now());
        Guest saved = guestRepository.save(guest);

        EventsNestResponse<GuestResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("RSVP recorded — " + status.name().toLowerCase());
        response.setData(toResponse(saved));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Guest> getRawGuests(UUID eventId) {
        return guestRepository.findAllByEventId(eventId);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private void assertOrganizer(UUID eventId, User user) {
        if (!membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, user.getId(), EventRole.ORGANIZER)) {
            throw new NotEventOrganizerException();
        }
    }

    private void assertGuestListEnabled(UUID eventId) {
        EventConfig config = configRepository.findByEventId(eventId)
                .orElseThrow(EventConfigNotFoundException::new);
        if (!config.isGuestListEnabled()) {
            throw new GuestListNotEnabledException();
        }
    }

    private Events findEventOrThrow(UUID eventId) {
        return eventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);
    }

    static GuestResponse toResponse(Guest guest) {
        return GuestResponse.builder()
                .id(guest.getId())
                .eventId(guest.getEvent().getId())
                .name(guest.getName())
                .email(guest.getEmail())
                .phone(guest.getPhone())
                .rsvpStatus(guest.getRsvpStatus())
                .invitedAt(guest.getInvitedAt())
                .respondedAt(guest.getRespondedAt())
                .note(guest.getNote())
                .build();
    }
}
