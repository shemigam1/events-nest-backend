package group.moniepoint.eventsnestserver.checkin.service;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.checkin.dto.request.CreateCheckInInviteRequest;
import group.moniepoint.eventsnestserver.checkin.dto.response.CheckInInviteResponse;
import group.moniepoint.eventsnestserver.checkin.model.CheckInInvite;
import group.moniepoint.eventsnestserver.checkin.model.CheckInInviteStatus;
import group.moniepoint.eventsnestserver.checkin.repository.CheckInInviteRepository;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.email.EmailOutbox;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.exception.auth.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.checkin.CheckInInviteNotFoundException;
import group.moniepoint.eventsnestserver.exception.event.EventNotFoundException;
import group.moniepoint.eventsnestserver.util.TokenHashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckInInviteServiceImpl implements CheckInInviteService {

    private final CheckInInviteRepository inviteRepository;
    private final EventRespository eventRepository;
    private final EventMembershipRepository membershipRepository;
    private final EmailOutbox emailOutbox;

    @Override
    @Transactional
    public EventsNestResponse<CheckInInviteResponse> createInvite(UUID eventId,
                                                                   CreateCheckInInviteRequest request,
                                                                   User organizer) {
        Events event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);
        assertIsOrganizer(eventId, organizer);

        String rawToken = "ckin_" + NanoIdUtils.randomNanoId(
                NanoIdUtils.DEFAULT_NUMBER_GENERATOR,
                NanoIdUtils.DEFAULT_ALPHABET,
                24);
        String tokenHash = TokenHashUtils.hash(rawToken);

        CheckInInvite invite = CheckInInvite.builder()
                .event(event)
                .name(request.getName())
                .email(request.getEmail())
                .tokenHash(tokenHash)
                .expiresAt(event.getEndTime().plusHours(24))
                .createdBy(organizer)
                .build();
        CheckInInvite saved = inviteRepository.save(invite);

        if (request.getEmail() != null) {
            emailOutbox.enqueueStaffInvite(
                    request.getEmail(),
                    request.getName(),
                    rawToken,
                    event);
        }

        CheckInInviteResponse data = toResponse(saved);
        data.setRawToken(rawToken);

        EventsNestResponse<CheckInInviteResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Check-in invite created. Save the token — it will not be shown again.");
        response.setData(data);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckInInviteResponse> listInvites(UUID eventId, User organizer) {
        assertIsOrganizer(eventId, organizer);
        return inviteRepository.findAllByEventId(eventId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public EventsNestResponse<Void> revokeInvite(UUID eventId, UUID inviteId, User organizer) {
        assertIsOrganizer(eventId, organizer);

        CheckInInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(CheckInInviteNotFoundException::new);

        if (!invite.getEvent().getId().equals(eventId)) {
            throw new CheckInInviteNotFoundException();
        }

        invite.setStatus(CheckInInviteStatus.REVOKED);
        inviteRepository.save(invite);

        EventsNestResponse<Void> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Check-in invite revoked");
        return response;
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private void assertIsOrganizer(UUID eventId, User user) {
        if (!membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, user.getId(), EventRole.ORGANIZER)) {
            throw new NotEventOrganizerException();
        }
    }

    private CheckInInviteResponse toResponse(CheckInInvite invite) {
        return CheckInInviteResponse.builder()
                .id(invite.getId())
                .name(invite.getName())
                .email(invite.getEmail())
                .status(invite.getStatus())
                .expiresAt(invite.getExpiresAt())
                .lastUsedAt(invite.getLastUsedAt())
                .createdAt(invite.getCreatedAt())
                .build();
    }
}
